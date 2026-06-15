"""
基于医生反馈数据的增量微调脚本

功能:
  - 从数据库读取未使用的医生反馈数据
  - 将反馈数据转换为CONLL训练格式
  - 在现有模型基础上进行增量微调
  - 记录训练结果并更新反馈数据状态
  - 支持每周定期运行（可通过cron调度）

使用示例:
  python incremental_finetune.py \
    --db_host localhost --db_port 3306 \
    --db_user root --db_password password \
    --db_name surg_extract_nlp \
    --model_dir ./models/surgery-ner \
    --output_dir ./models/surgery-ner-finetuned \
    --epochs 10 --batch_size 16

  # 仅生成训练数据，不训练
  python incremental_finetune.py --export_only --output_data ./data/feedback_train.conll
"""

import os
import sys
import json
import argparse
import random
from pathlib import Path
from typing import List, Tuple, Dict, Optional
from datetime import datetime, timedelta

import numpy as np
import torch
import torch.nn as nn
from torch.utils.data import Dataset, DataLoader
from torch.optim import AdamW
from torch.optim.lr_scheduler import LambdaLR
from tqdm import tqdm

try:
    from transformers import AutoTokenizer, AutoModel
except ImportError:
    print("请安装 transformers: pip install transformers")
    sys.exit(1)

try:
    from seqeval.metrics import classification_report, f1_score, precision_score, recall_score
except ImportError:
    print("请安装 seqeval: pip install seqeval")
    sys.exit(1)


ENTITY_TYPES = [
    "PATIENT_NAME", "GENDER", "AGE", "HOSPITAL_NO", "DEPARTMENT",
    "SURGERY_DATE", "SURGERY_NAME", "SURGERY_LEVEL",
    "INCISION_LEVEL", "INCISION_HEALING", "ANESTHESIA_TYPE",
    "ANESTHESIOLOGIST", "SURGEON", "ASSISTANT",
    "SCRUB_NURSE", "CIRCULATING_NURSE",
    "BLOOD_LOSS", "BLOOD_TRANSFUSION",
    "FLUID_INFUSION", "URINE_OUTPUT",
    "PREOP_DIAGNOSIS", "POSTOP_DIAGNOSIS",
    "COMPLICATION", "BED_NO", "ADMISSION_DATE"
]


def set_seed(seed: int):
    random.seed(seed)
    np.random.seed(seed)
    torch.manual_seed(seed)
    if torch.cuda.is_available():
        torch.cuda.manual_seed_all(seed)


def build_label2id(entity_types: List[str]) -> Dict[str, int]:
    label2id = {"O": 0}
    idx = 1
    for et in entity_types:
        label2id[f"B-{et}"] = idx
        idx += 1
        label2id[f"I-{et}"] = idx
        idx += 1
    return label2id


def id2label_from(label2id: Dict[str, int]) -> Dict[int, str]:
    return {v: k for k, v in label2id.items()}


def load_existing_conll_data(file_path: str) -> List[Tuple[List[str], List[str]]]:
    sentences = []
    if not os.path.exists(file_path):
        return sentences
    words: List[str] = []
    labels: List[str] = []
    with open(file_path, "r", encoding="utf-8") as f:
        for line in f:
            line = line.rstrip("\n")
            if not line.strip():
                if words:
                    sentences.append((words, labels))
                    words, labels = [], []
                continue
            parts = line.split()
            if len(parts) >= 2:
                words.append(parts[0])
                labels.append(parts[-1])
    if words:
        sentences.append((words, labels))
    return sentences


class FeedbackDataLoader:
    def __init__(self, db_config: Dict):
        self.db_config = db_config
        self.conn = None

    def connect(self):
        try:
            import pymysql
            self.conn = pymysql.connect(
                host=self.db_config["host"],
                port=self.db_config.get("port", 3306),
                user=self.db_config["user"],
                password=self.db_config["password"],
                database=self.db_config["database"],
                charset="utf8mb4"
            )
            return True
        except ImportError:
            print("警告: pymysql未安装，使用模拟数据模式。安装: pip install pymysql")
            return False
        except Exception as e:
            print(f"数据库连接失败: {e}，使用模拟数据模式")
            return False

    def fetch_pending_feedback(self, limit: int = 5000, min_quality: int = 60) -> List[Dict]:
        if not self.conn:
            return self._generate_mock_feedback(limit)
        try:
            with self.conn.cursor() as cursor:
                sql = """
                    SELECT id, record_id, entity_type, original_value, original_unit,
                           original_confidence, corrected_value, corrected_unit,
                           correction_type, original_start_pos, original_end_pos,
                           original_text, quality_score, department
                    FROM doctor_feedback
                    WHERE deleted = 0 AND used_for_training = 0
                      AND quality_score >= %s
                    ORDER BY created_time ASC
                    LIMIT %s
                """
                cursor.execute(sql, (min_quality, limit))
                columns = [desc[0] for desc in cursor.description]
                results = [dict(zip(columns, row)) for row in cursor.fetchall()]
                return results
        except Exception as e:
            print(f"查询反馈数据失败: {e}")
            return self._generate_mock_feedback(limit)

    def mark_feedback_used(self, feedback_ids: List[int], train_batch_no: str):
        if not self.conn or not feedback_ids:
            return
        try:
            with self.conn.cursor() as cursor:
                now = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
                placeholders = ",".join(["%s"] * len(feedback_ids))
                sql = f"""
                    UPDATE doctor_feedback
                    SET used_for_training = 1, used_time = %s, train_batch_no = %s,
                        updated_time = %s
                    WHERE id IN ({placeholders})
                """
                cursor.execute(sql, [now, train_batch_no, now] + feedback_ids)
            self.conn.commit()
        except Exception as e:
            print(f"标记反馈数据已使用失败: {e}")
            self.conn.rollback()

    def insert_train_log(self, log_data: Dict) -> Optional[int]:
        if not self.conn:
            return None
        try:
            with self.conn.cursor() as cursor:
                sql = """
                    INSERT INTO model_train_log
                    (train_batch_no, model_name, model_version, previous_version,
                     train_type, feedback_count, new_sample_count, total_sample_count,
                     train_loss, dev_loss, precision_score, recall_score, f1_score,
                     previous_f1_score, f1_improvement, entity_type_breakdown,
                     train_status, fail_reason, train_start_time, train_end_time,
                     train_duration_sec, train_params, triggered_by, model_path, remark,
                     created_time, updated_time)
                    VALUES
                    (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s,
                     %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s,
                     NOW(), NOW())
                """
                cursor.execute(sql, (
                    log_data.get("train_batch_no"),
                    log_data.get("model_name", "surgery-ner"),
                    log_data.get("model_version"),
                    log_data.get("previous_version"),
                    log_data.get("train_type", "INCREMENTAL"),
                    log_data.get("feedback_count", 0),
                    log_data.get("new_sample_count", 0),
                    log_data.get("total_sample_count", 0),
                    log_data.get("train_loss"),
                    log_data.get("dev_loss"),
                    log_data.get("precision_score"),
                    log_data.get("recall_score"),
                    log_data.get("f1_score"),
                    log_data.get("previous_f1_score"),
                    log_data.get("f1_improvement"),
                    log_data.get("entity_type_breakdown"),
                    log_data.get("train_status", "SUCCESS"),
                    log_data.get("fail_reason"),
                    log_data.get("train_start_time"),
                    log_data.get("train_end_time"),
                    log_data.get("train_duration_sec"),
                    log_data.get("train_params"),
                    log_data.get("triggered_by"),
                    log_data.get("model_path"),
                    log_data.get("remark")
                ))
            self.conn.commit()
            return cursor.lastrowid
        except Exception as e:
            print(f"插入训练日志失败: {e}")
            self.conn.rollback()
            return None

    def get_latest_f1(self) -> Optional[float]:
        if not self.conn:
            return None
        try:
            with self.conn.cursor() as cursor:
                cursor.execute("""
                    SELECT f1_score FROM model_train_log
                    WHERE deleted = 0 AND train_status = 'SUCCESS'
                    ORDER BY train_end_time DESC LIMIT 1
                """)
                row = cursor.fetchone()
                return float(row[0]) if row else None
        except Exception:
            return None

    def close(self):
        if self.conn:
            self.conn.close()

    def _generate_mock_feedback(self, count: int) -> List[Dict]:
        mock_texts = [
            ("患者张三，男，56岁，住院号ZY202401001。", {
                "PATIENT_NAME": "张三", "GENDER": "男", "AGE": "56", "HOSPITAL_NO": "ZY202401001"
            }),
            ("于2024-01-15在全身麻醉下行腹腔镜胆囊切除术。", {
                "SURGERY_DATE": "2024-01-15", "ANESTHESIA_TYPE": "全身麻醉",
                "SURGERY_NAME": "腹腔镜胆囊切除术"
            }),
            ("手术医生：李主任，助手：王医生、赵医生。", {
                "SURGEON": "李主任", "ASSISTANT": "王医生、赵医生"
            }),
            ("术中出血约150ml，输液2000ml，未输血。", {
                "BLOOD_LOSS": "150ml", "FLUID_INFUSION": "2000ml"
            }),
            ("切口等级：Ⅰ类，愈合良好。", {
                "INCISION_LEVEL": "Ⅰ类", "INCISION_HEALING": "良好"
            }),
        ]

        feedback_list = []
        for i in range(min(count, 50)):
            text, entities = random.choice(mock_texts)
            entity_type = random.choice(list(entities.keys()))
            original = entities[entity_type]
            corrections = ["CORRECTION", "ADDITION", "DELETION"]
            correction_type = random.choice(corrections)

            fb = {
                "id": i + 1,
                "record_id": 1000 + i,
                "entity_type": entity_type,
                "original_value": original if correction_type != "ADDITION" else None,
                "original_unit": None,
                "original_confidence": round(random.uniform(0.3, 0.95), 4),
                "corrected_value": original if correction_type != "DELETION" else None,
                "corrected_unit": None,
                "correction_type": correction_type,
                "original_start_pos": text.find(original) if original and original in text else 0,
                "original_end_pos": text.find(original) + len(original) if original and original in text else len(original) if original else 0,
                "quality_score": random.randint(60, 100),
                "department": random.choice(["普外科", "骨科", "妇产科", "心胸外科"])
            }
            feedback_list.append(fb)
        return feedback_list


def convert_feedback_to_training_samples(
    feedback_list: List[Dict],
    text_map: Dict[int, str] = None
) -> List[Tuple[List[str], List[str]]]:
    samples = []

    default_contexts = [
        "患者{name}，{gender}，{age}岁，住院号{hosp_no}。",
        "于{date}在{anes}下行{surgery}。",
        "手术医生：{surgeon}，助手：{assistant}。",
        "术中出血约{blood}，输液{fluid}，输血{transfusion}。",
        "切口等级：{incision}，愈合：{healing}。",
    ]

    for fb in feedback_list:
        entity_type = fb.get("entity_type", "")
        correction_type = fb.get("correction_type", "CORRECTION")
        corrected_value = fb.get("corrected_value", "")
        original_value = fb.get("original_value", "")
        original_text = fb.get("original_text", "")
        original_start_pos = fb.get("original_start_pos")
        original_end_pos = fb.get("original_end_pos")

        if correction_type == "DELETION":
            continue

        if not corrected_value or not entity_type:
            continue

        text = None

        if original_text and corrected_value in original_text:
            text = original_text
        elif original_text and original_value and original_value in original_text:
            text = original_text.replace(original_value, corrected_value, 1)
        elif original_text:
            text = original_text

        if text is None:
            template = random.choice(default_contexts)
            text = template.format(
                name=corrected_value if entity_type == "PATIENT_NAME" else "张三",
                gender=corrected_value if entity_type == "GENDER" else "男",
                age=corrected_value if entity_type == "AGE" else "56",
                hosp_no=corrected_value if entity_type == "HOSPITAL_NO" else "ZY202401001",
                date=corrected_value if entity_type == "SURGERY_DATE" else "2024-01-15",
                anes=corrected_value if entity_type == "ANESTHESIA_TYPE" else "全身麻醉",
                surgery=corrected_value if entity_type == "SURGERY_NAME" else "腹腔镜阑尾切除术",
                surgeon=corrected_value if entity_type == "SURGEON" else "李主任",
                assistant=corrected_value if entity_type == "ASSISTANT" else "王医生",
                blood=corrected_value if entity_type == "BLOOD_LOSS" else "150ml",
                fluid=corrected_value if entity_type == "FLUID_INFUSION" else "2000ml",
                transfusion=corrected_value if entity_type == "BLOOD_TRANSFUSION" else "0ml",
                incision=corrected_value if entity_type == "INCISION_LEVEL" else "Ⅰ类",
                healing=corrected_value if entity_type == "INCISION_HEALING" else "良好",
            )

        chars = list(text)
        labels = ["O"] * len(chars)

        if corrected_value and corrected_value in text:
            idx = text.find(corrected_value)
            if idx >= 0:
                labels[idx] = f"B-{entity_type}"
                for j in range(idx + 1, idx + len(corrected_value)):
                    if j < len(labels):
                        labels[j] = f"I-{entity_type}"

        samples.append((chars, labels))

    return samples


def load_conll_data(file_path: str) -> List[Tuple[List[str], List[str]]]:
    sentences = []
    if not os.path.exists(file_path):
        return sentences
    words: List[str] = []
    labels: List[str] = []
    with open(file_path, "r", encoding="utf-8") as f:
        for line in f:
            line = line.rstrip("\n")
            if not line.strip():
                if words:
                    sentences.append((words, labels))
                    words, labels = [], []
                continue
            parts = line.split()
            if len(parts) >= 2:
                words.append(parts[0])
                labels.append(parts[-1])
    if words:
        sentences.append((words, labels))
    return sentences


def save_conll_data(data: List[Tuple[List[str], List[str]]], output_path: str):
    os.makedirs(os.path.dirname(output_path), exist_ok=True)
    with open(output_path, "w", encoding="utf-8") as f:
        for words, labels in data:
            for w, l in zip(words, labels):
                f.write(f"{w} {l}\n")
            f.write("\n")


class NerDataset(Dataset):
    def __init__(
        self,
        data: List[Tuple[List[str], List[str]]],
        tokenizer,
        label2id: Dict[str, int],
        max_seq_length: int = 512
    ):
        self.data = data
        self.tokenizer = tokenizer
        self.label2id = label2id
        self.max_seq_length = max_seq_length
        self.o_id = label2id.get("O", 0)

    def __len__(self):
        return len(self.data)

    def __getitem__(self, idx):
        words, labels = self.data[idx]
        tokens = []
        label_ids = []

        for word, label in zip(words, labels):
            sub_tokens = self.tokenizer.tokenize(word)
            if not sub_tokens:
                sub_tokens = [self.tokenizer.unk_token]
            tokens.extend(sub_tokens)
            label_id = self.label2id.get(label, self.o_id)
            label_ids.extend([label_id] + [-100] * (len(sub_tokens) - 1))

        if len(tokens) > self.max_seq_length - 2:
            tokens = tokens[: self.max_seq_length - 2]
            label_ids = label_ids[: self.max_seq_length - 2]

        tokens = [self.tokenizer.cls_token] + tokens + [self.tokenizer.sep_token]
        label_ids = [-100] + label_ids + [-100]
        attention_mask = [1] * len(tokens)

        input_ids = self.tokenizer.convert_tokens_to_ids(tokens)

        padding_len = self.max_seq_length - len(input_ids)
        if padding_len > 0:
            input_ids += [self.tokenizer.pad_token_id] * padding_len
            attention_mask += [0] * padding_len
            label_ids += [-100] * padding_len

        return {
            "input_ids": torch.tensor(input_ids, dtype=torch.long),
            "attention_mask": torch.tensor(attention_mask, dtype=torch.long),
            "labels": torch.tensor(label_ids, dtype=torch.long),
        }


class CRF(nn.Module):
    def __init__(self, num_tags: int):
        super().__init__()
        self.num_tags = num_tags
        self.transitions = nn.Parameter(torch.randn(num_tags, num_tags))
        self.start_transitions = nn.Parameter(torch.randn(num_tags))
        self.end_transitions = nn.Parameter(torch.randn(num_tags))

    def _forward_alg(self, feats: torch.Tensor, mask: torch.Tensor) -> torch.Tensor:
        batch_size, seq_len, num_tags = feats.shape
        alpha = self.start_transitions + feats[:, 0, :]
        for t in range(1, seq_len):
            emit = feats[:, t, :].unsqueeze(2)
            trans = self.transitions.unsqueeze(0)
            alpha_t = alpha.unsqueeze(1) + trans + emit
            mask_t = mask[:, t].unsqueeze(1)
            alpha = torch.where(mask_t.bool(), torch.logsumexp(alpha_t, dim=2), alpha)
        alpha = alpha + self.end_transitions
        return torch.logsumexp(alpha, dim=1)

    def _score_sentence(
        self, feats: torch.Tensor, tags: torch.Tensor, mask: torch.Tensor
    ) -> torch.Tensor:
        batch_size, seq_len, _ = feats.shape
        score = feats.gather(2, tags.unsqueeze(-1)).squeeze(-1)
        score[:, 0] += self.start_transitions[tags[:, 0]]
        for t in range(1, seq_len):
            score[:, t] += self.transitions[tags[:, t - 1], tags[:, t]]
        lens = mask.sum(dim=1).long() - 1
        end_tags = tags.gather(1, lens.unsqueeze(1)).squeeze(1)
        score += self.end_transitions[end_tags]
        score = (score * mask).sum(dim=1)
        return score

    def neg_log_likelihood(
        self, feats: torch.Tensor, tags: torch.Tensor, mask: torch.Tensor
    ) -> torch.Tensor:
        forward_score = self._forward_alg(feats, mask)
        gold_score = self._score_sentence(feats, tags, mask)
        return (forward_score - gold_score).mean()

    def decode(self, feats: torch.Tensor, mask: torch.Tensor) -> List[List[int]]:
        batch_size, seq_len, num_tags = feats.shape
        backpointers = torch.zeros(batch_size, seq_len, num_tags, dtype=torch.long, device=feats.device)
        viterbi = self.start_transitions + feats[:, 0, :]
        for t in range(1, seq_len):
            viterbi_t = viterbi.unsqueeze(2) + self.transitions.unsqueeze(0)
            viterbi_t, backpointers_t = viterbi_t.max(dim=1)
            viterbi_t = viterbi_t + feats[:, t, :]
            mask_t = mask[:, t].unsqueeze(1)
            viterbi = torch.where(mask_t.bool(), viterbi_t, viterbi)
            backpointers[:, t, :] = backpointers_t

        lens = mask.sum(dim=1).long() - 1
        best_tags_list = []
        for i in range(batch_size):
            last_tag = torch.argmax(viterbi[i] + self.end_transitions).item()
            tags_seq = [last_tag]
            for t in range(lens[i].item(), 0, -1):
                last_tag = backpointers[i, t, last_tag].item()
                tags_seq.append(last_tag)
            tags_seq.reverse()
            best_tags_list.append(tags_seq)
        return best_tags_list


class BertBiLSTMCRF(nn.Module):
    def __init__(
        self,
        bert_model_name: str,
        num_labels: int,
        hidden_dim: int = 256,
        lstm_layers: int = 2,
        lstm_dropout: float = 0.3,
        offline_mode: bool = False,
    ):
        super().__init__()
        if offline_mode:
            self.bert = AutoModel.from_pretrained(bert_model_name, local_files_only=True)
        else:
            self.bert = AutoModel.from_pretrained(bert_model_name)
        bert_dim = self.bert.config.hidden_size

        self.dropout = nn.Dropout(lstm_dropout)
        self.bilstm = nn.LSTM(
            input_size=bert_dim,
            hidden_size=hidden_dim,
            num_layers=lstm_layers,
            dropout=lstm_dropout if lstm_layers > 1 else 0.0,
            bidirectional=True,
            batch_first=True
        )
        self.linear = nn.Linear(hidden_dim * 2, num_labels)
        self.crf = CRF(num_labels)

    def forward(
        self,
        input_ids: torch.Tensor,
        attention_mask: torch.Tensor,
        labels: Optional[torch.Tensor] = None,
    ):
        outputs = self.bert(input_ids=input_ids, attention_mask=attention_mask)
        sequence_output = outputs.last_hidden_state
        sequence_output = self.dropout(sequence_output)
        lstm_out, _ = self.bilstm(sequence_output)
        feats = self.linear(lstm_out)

        if labels is not None:
            loss = self.crf.neg_log_likelihood(feats, labels, attention_mask)
            return loss
        else:
            pred_tags = self.crf.decode(feats, attention_mask)
            return pred_tags


def align_predictions(
    predictions: List[List[int]],
    label_ids: torch.Tensor,
    id2label: Dict[int, str]
) -> Tuple[List[List[str]], List[List[str]]]:
    preds_list = []
    labels_list = []

    for i, pred in enumerate(predictions):
        labels_i = label_ids[i].cpu().numpy()
        pred_labels = []
        true_labels = []
        for j, (p, l) in enumerate(zip(pred, labels_i)):
            if l != -100 and j < len(pred):
                pred_labels.append(id2label.get(p, "O"))
                true_labels.append(id2label.get(l, "O"))
        preds_list.append(pred_labels)
        labels_list.append(true_labels)
    return preds_list, labels_list


def get_linear_schedule_with_warmup(
    optimizer, num_warmup_steps: int, num_training_steps: int
):
    def lr_lambda(current_step: int):
        if current_step < num_warmup_steps:
            return float(current_step) / float(max(1, num_warmup_steps))
        return max(
            0.0,
            float(num_training_steps - current_step) /
            float(max(1, num_training_steps - num_warmup_steps))
        )
    return LambdaLR(optimizer, lr_lambda)


def compute_entity_metrics(
    all_preds: List[List[str]],
    all_labels: List[List[str]],
    entity_types: List[str]
) -> Dict[str, Dict[str, float]]:
    breakdown = {}
    for et in entity_types:
        et_preds = []
        et_labels = []
        for preds, labels in zip(all_preds, all_labels):
            ep = []
            el = []
            for p, l in zip(preds, labels):
                p_et = p.replace("B-", "").replace("I-", "") if p != "O" else "O"
                l_et = l.replace("B-", "").replace("I-", "") if l != "O" else "O"
                if p_et == et or l_et == et:
                    ep.append(p if p_et == et else "O")
                    el.append(l if l_et == et else "O")
                else:
                    ep.append("O")
                    el.append("O")
            et_preds.append(ep)
            et_labels.append(el)

        try:
            p = precision_score(et_labels, et_preds)
            r = recall_score(et_labels, et_preds)
            f1 = f1_score(et_labels, et_preds)
            breakdown[et] = {"precision": round(p, 4), "recall": round(r, 4), "f1": round(f1, 4)}
        except Exception:
            breakdown[et] = {"precision": 0.0, "recall": 0.0, "f1": 0.0}
    return breakdown


def train_incremental(args):
    set_seed(args.seed)
    device = torch.device("cuda" if torch.cuda.is_available() and not args.no_cuda else "cpu")
    print(f"使用设备: {device}")

    batch_no = f"TRAIN-{datetime.now().strftime('%Y%m%d')}-{random.randint(1000,9999)}"
    print(f"训练批次号: {batch_no}")

    output_dir = Path(args.output_dir)
    output_dir.mkdir(parents=True, exist_ok=True)

    label2id = build_label2id(ENTITY_TYPES)
    id2label = id2label_from(label2id)
    num_labels = len(label2id)

    with open(output_dir / "label2id.json", "w", encoding="utf-8") as f:
        json.dump(label2id, f, ensure_ascii=False, indent=2)

    db_config = {
        "host": args.db_host,
        "port": args.db_port,
        "user": args.db_user,
        "password": args.db_password,
        "database": args.db_name,
    }
    db_loader = FeedbackDataLoader(db_config)
    db_connected = db_loader.connect()

    if args.feedback_file and os.path.exists(args.feedback_file):
        print(f"从外部文件加载反馈数据: {args.feedback_file}")
        with open(args.feedback_file, "r", encoding="utf-8") as f:
            feedback_list = json.load(f)
        if not isinstance(feedback_list, list):
            feedback_list = []
        print(f"从文件加载到 {len(feedback_list)} 条反馈数据")
        if db_connected and feedback_list:
            feedback_ids = [fb["id"] for fb in feedback_list if isinstance(fb.get("id"), int)]
            if feedback_ids:
                db_loader.mark_feedback_used(feedback_ids, batch_no)
                print(f"已标记 {len(feedback_ids)} 条外部反馈为训练中(used_for_training=1)")
    else:
        print(f"加载反馈数据 (limit={args.max_feedback}, min_quality={args.min_quality})...")
        feedback_list = db_loader.fetch_pending_feedback(args.max_feedback, args.min_quality)
        print(f"获取到 {len(feedback_list)} 条反馈数据")

    feedback_samples = convert_feedback_to_training_samples(feedback_list)
    print(f"生成 {len(feedback_samples)} 条训练样本")

    base_train_data = load_conll_data(args.train_data) if args.train_data else []
    base_dev_data = load_conll_data(args.dev_data) if args.dev_data else []
    print(f"基础训练数据: {len(base_train_data)} 条, 验证数据: {len(base_dev_data)} 条")

    all_train_data = base_train_data + feedback_samples
    random.shuffle(all_train_data)

    n = len(all_train_data)
    if len(base_dev_data) == 0 and n > 20:
        train_data = all_train_data[: int(n * 0.8)]
        dev_data = all_train_data[int(n * 0.8): int(n * 0.9)]
        test_data = all_train_data[int(n * 0.9):]
    else:
        train_data = all_train_data
        dev_data = base_dev_data if base_dev_data else (
            all_train_data[-100:] if len(all_train_data) > 100 else all_train_data
        )
        test_data = dev_data

    print(f"训练集: {len(train_data)}, 验证集: {len(dev_data)}, 测试集: {len(test_data)}")

    if args.export_only:
        export_path = args.output_data or str(output_dir / "feedback_train.conll")
        save_conll_data(feedback_samples, export_path)
        print(f"训练数据已导出到: {export_path}")
        if feedback_list and db_connected:
            feedback_ids = [fb["id"] for fb in feedback_list]
            db_loader.mark_feedback_used(feedback_ids, batch_no)
            print(f"已标记 {len(feedback_ids)} 条反馈数据为已使用")
        db_loader.close()
        return

    bert_path = args.bert_local_path if (args.offline_mode and args.bert_local_path) else args.bert_model
    tokenizer = AutoTokenizer.from_pretrained(bert_path, local_files_only=args.offline_mode)
    try:
        tokenizer.save_pretrained(output_dir / "tokenizer")
    except Exception:
        pass

    train_dataset = NerDataset(train_data, tokenizer, label2id, args.max_seq_length)
    dev_dataset = NerDataset(dev_data, tokenizer, label2id, args.max_seq_length)
    test_dataset = NerDataset(test_data, tokenizer, label2id, args.max_seq_length)

    train_loader = DataLoader(train_dataset, batch_size=args.batch_size, shuffle=True)
    dev_loader = DataLoader(dev_dataset, batch_size=args.batch_size)
    test_loader = DataLoader(test_dataset, batch_size=args.batch_size)

    model = BertBiLSTMCRF(
        bert_model_name=bert_path,
        num_labels=num_labels,
        hidden_dim=args.hidden_dim,
        lstm_layers=args.lstm_layers,
        lstm_dropout=args.lstm_dropout,
        offline_mode=args.offline_mode,
    )

    model_dir = Path(args.model_dir)
    model_path = model_dir / "pytorch_model.bin"
    if model_path.exists():
        print(f"加载现有模型权重: {model_path}")
        model.load_state_dict(torch.load(model_path, map_location=device), strict=False)

    model = model.to(device)

    bert_params = list(model.bert.parameters())
    other_params = list(model.bilstm.parameters()) + list(model.linear.parameters()) + list(model.crf.parameters())

    optimizer = AdamW([
        {"params": bert_params, "lr": args.bert_learning_rate},
        {"params": other_params, "lr": args.learning_rate}
    ], weight_decay=args.weight_decay)

    total_steps = len(train_loader) * args.epochs
    scheduler = get_linear_schedule_with_warmup(optimizer, args.warmup_steps, total_steps)

    best_f1 = 0.0
    patience_counter = 0
    train_start = datetime.now()
    previous_f1 = db_loader.get_latest_f1()

    train_loss_list = []
    print("=" * 60)
    print("开始增量微调训练...")
    print("=" * 60)

    for epoch in range(1, args.epochs + 1):
        model.train()
        total_loss = 0.0
        progress = tqdm(train_loader, desc=f"Epoch {epoch}/{args.epochs}", ncols=100)
        for batch in progress:
            input_ids = batch["input_ids"].to(device)
            attention_mask = batch["attention_mask"].to(device)
            labels = batch["labels"].to(device)
            loss = model(input_ids, attention_mask, labels)
            optimizer.zero_grad()
            loss.backward()
            torch.nn.utils.clip_grad_norm_(model.parameters(), 1.0)
            optimizer.step()
            scheduler.step()
            total_loss += loss.item()
            progress.set_postfix(loss=f"{loss.item():.4f}")

        avg_loss = total_loss / len(train_loader)
        train_loss_list.append(avg_loss)
        print(f"\nEpoch {epoch} 平均损失: {avg_loss:.4f}")

        model.eval()
        all_preds = []
        all_labels = []
        with torch.no_grad():
            for batch in tqdm(dev_loader, desc="验证", ncols=80):
                input_ids = batch["input_ids"].to(device)
                attention_mask = batch["attention_mask"].to(device)
                labels = batch["labels"]
                pred_tags = model(input_ids, attention_mask)
                preds, trues = align_predictions(pred_tags, labels, id2label)
                all_preds.extend(preds)
                all_labels.extend(trues)

        p = precision_score(all_labels, all_preds)
        r = recall_score(all_labels, all_preds)
        f1 = f1_score(all_labels, all_preds)
        print(f"验证集: P={p:.4f}, R={r:.4f}, F1={f1:.4f}")

        if f1 > best_f1:
            best_f1 = f1
            patience_counter = 0
            torch.save(model.state_dict(), output_dir / "pytorch_model.bin")
            print(f"  -> 模型已保存 (当前最佳 F1={best_f1:.4f})")
        else:
            patience_counter += 1
            print(f"  -> F1未提升，patience={patience_counter}/{args.early_stopping_patience}")
            if patience_counter >= args.early_stopping_patience:
                print("早停触发，训练结束。")
                break

    train_end = datetime.now()
    train_duration = int((train_end - train_start).total_seconds())

    print("\n" + "=" * 60)
    print("加载最佳模型在测试集上评估...")
    print("=" * 60)

    best_model_path = output_dir / "pytorch_model.bin"
    if best_model_path.exists():
        model.load_state_dict(torch.load(best_model_path, map_location=device))
    model.eval()
    all_preds = []
    all_labels = []
    with torch.no_grad():
        for batch in tqdm(test_loader, desc="测试", ncols=80):
            input_ids = batch["input_ids"].to(device)
            attention_mask = batch["attention_mask"].to(device)
            labels = batch["labels"]
            pred_tags = model(input_ids, attention_mask)
            preds, trues = align_predictions(pred_tags, labels, id2label)
            all_preds.extend(preds)
            all_labels.extend(trues)

    print("\n测试集分类报告:")
    print(classification_report(all_labels, all_preds, digits=4))

    test_p = precision_score(all_labels, all_preds)
    test_r = recall_score(all_labels, all_preds)
    test_f1 = f1_score(all_labels, all_preds)

    entity_breakdown = compute_entity_metrics(all_preds, all_labels, ENTITY_TYPES)

    print("\n" + "=" * 60)
    print("各实体类型指标:")
    print("=" * 60)
    for et, metrics in sorted(entity_breakdown.items(), key=lambda x: -x[1]["f1"]):
        print(f"  {et:25s} P={metrics['precision']:.4f}  R={metrics['recall']:.4f}  F1={metrics['f1']:.4f}")

    f1_improvement = None
    if previous_f1 is not None:
        f1_improvement = round(test_f1 - previous_f1, 4)
        print(f"\nF1分数变化: {previous_f1:.4f} -> {test_f1:.4f} (改进: {f1_improvement:+.4f})")

    model_version = f"v{train_end.strftime('%Y%m%d')}.{random.randint(1000,9999)}"
    print(f"\n模型版本: {model_version}")
    print(f"训练完成！模型已保存到: {output_dir}")

    if db_connected:
        train_params = json.dumps({
            "epochs": args.epochs,
            "batch_size": args.batch_size,
            "learning_rate": args.learning_rate,
            "bert_learning_rate": args.bert_learning_rate,
            "weight_decay": args.weight_decay,
            "max_feedback": args.max_feedback,
            "min_quality": args.min_quality,
        }, ensure_ascii=False)

        log_data = {
            "train_batch_no": batch_no,
            "model_name": "surgery-ner",
            "model_version": model_version,
            "previous_version": None,
            "train_type": args.train_type,
            "feedback_count": len(feedback_list),
            "new_sample_count": len(feedback_samples),
            "total_sample_count": len(all_train_data),
            "train_loss": round(train_loss_list[-1], 6) if train_loss_list else None,
            "dev_loss": None,
            "precision_score": round(test_p, 4),
            "recall_score": round(test_r, 4),
            "f1_score": round(test_f1, 4),
            "previous_f1_score": round(previous_f1, 4) if previous_f1 else None,
            "f1_improvement": f1_improvement,
            "entity_type_breakdown": json.dumps(entity_breakdown, ensure_ascii=False),
            "train_status": "SUCCESS",
            "fail_reason": None,
            "train_start_time": train_start.strftime("%Y-%m-%d %H:%M:%S"),
            "train_end_time": train_end.strftime("%Y-%m-%d %H:%M:%S"),
            "train_duration_sec": train_duration,
            "train_params": train_params,
            "triggered_by": None,
            "model_path": str(output_dir.absolute()),
            "remark": f"增量微调，使用{len(feedback_list)}条反馈数据"
        }
        log_id = db_loader.insert_train_log(log_data)
        if log_id:
            print(f"训练日志已记录 (ID: {log_id})")

        if feedback_list and not args.feedback_file:
            feedback_ids = [fb["id"] for fb in feedback_list if isinstance(fb.get("id"), int)]
            if feedback_ids:
                db_loader.mark_feedback_used(feedback_ids, batch_no)
                print(f"已标记 {len(feedback_ids)} 条反馈数据为已使用(训练成功后标记)")

    db_loader.close()

    result_summary = {
        "success": True,
        "trainBatchNo": batch_no,
        "modelVersion": model_version,
        "trainLoss": round(train_loss_list[-1], 6) if train_loss_list else 0,
        "precision": round(test_p, 4),
        "recall": round(test_r, 4),
        "f1": round(test_f1, 4),
        "f1Improvement": f1_improvement,
        "trainDurationSec": train_duration,
        "feedbackCount": len(feedback_list),
        "sampleCount": len(all_train_data),
        "modelPath": str(output_dir.absolute()),
        "entityBreakdown": entity_breakdown,
    }
    with open(output_dir / "train_result.json", "w", encoding="utf-8") as f:
        json.dump(result_summary, f, ensure_ascii=False, indent=2)
    print(f"\n训练结果已保存到: {output_dir / 'train_result.json'}")


def main():
    parser = argparse.ArgumentParser(description="基于医生反馈的NLP模型增量微调")
    parser.add_argument("--db_host", type=str, default="localhost", help="数据库主机")
    parser.add_argument("--db_port", type=int, default=3306, help="数据库端口")
    parser.add_argument("--db_user", type=str, default="root", help="数据库用户名")
    parser.add_argument("--db_password", type=str, default="", help="数据库密码")
    parser.add_argument("--db_name", type=str, default="surg_extract_nlp", help="数据库名")

    parser.add_argument("--train_data", type=str, default=None, help="基础训练数据(CONLL格式)")
    parser.add_argument("--dev_data", type=str, default=None, help="验证数据")
    parser.add_argument("--model_dir", type=str, default="./models/surgery-ner", help="现有模型目录")
    parser.add_argument("--output_dir", type=str, default="./models/surgery-ner-finetuned", help="输出模型目录")

    parser.add_argument("--bert_model", type=str, default="bert-base-chinese")
    parser.add_argument("--epochs", type=int, default=10)
    parser.add_argument("--batch_size", type=int, default=16)
    parser.add_argument("--max_seq_length", type=int, default=512)
    parser.add_argument("--learning_rate", type=float, default=0.001)
    parser.add_argument("--bert_learning_rate", type=float, default=2e-5)
    parser.add_argument("--weight_decay", type=float, default=0.01)
    parser.add_argument("--warmup_steps", type=int, default=50)
    parser.add_argument("--hidden_dim", type=int, default=256)
    parser.add_argument("--lstm_layers", type=int, default=2)
    parser.add_argument("--lstm_dropout", type=float, default=0.3)
    parser.add_argument("--early_stopping_patience", type=int, default=3)

    parser.add_argument("--max_feedback", type=int, default=5000, help="最大反馈数据量")
    parser.add_argument("--min_quality", type=int, default=60, help="最低质量评分")
    parser.add_argument("--train_type", type=str, default="INCREMENTAL", help="训练类型")

    parser.add_argument("--feedback_file", type=str, default=None, help="外部反馈数据文件(JSON)，由API传入，优先于数据库查询")

    parser.add_argument("--seed", type=int, default=42)
    parser.add_argument("--no_cuda", action="store_true")
    parser.add_argument("--export_only", action="store_true", help="仅导出训练数据")
    parser.add_argument("--output_data", type=str, default=None, help="导出数据路径")
    parser.add_argument("--offline_mode", action="store_true", help="离线模式，从本地加载模型")
    parser.add_argument("--bert_local_path", type=str, default=None, help="本地BERT模型路径")

    args = parser.parse_args()
    train_incremental(args)


if __name__ == "__main__":
    main()
