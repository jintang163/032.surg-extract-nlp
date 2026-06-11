"""
BiLSTM-CRF 手术记录实体识别模型 - 训练脚本

架构: BERT Embedding -> BiLSTM (2层, 隐藏层256) -> CRF
支持:
  - CONLL格式数据加载
  - 早停、学习率调度、权重衰减
  - 训练过程日志、验证集评估
  - 模型权重保存、最佳检查点保留
  - Dummy数据生成（无标注数据时快速体验）

使用示例:
  python train_bilstm_crf.py \
    --train_data ./data/train.txt \
    --dev_data ./data/dev.txt \
    --test_data ./data/test.txt \
    --model_dir ./models/surgery-ner \
    --epochs 30 --batch_size 16

快速体验（使用Dummy数据）:
  python train_bilstm_crf.py --use_dummy_data --epochs 3
"""

import os
import sys
import json
import random
import argparse
from pathlib import Path
from typing import List, Tuple, Dict, Optional

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


def set_seed(seed: int):
    random.seed(seed)
    np.random.seed(seed)
    torch.manual_seed(seed)
    if torch.cuda.is_available():
        torch.cuda.manual_seed_all(seed)


ENTITY_TYPES = [
    "PATIENT_NAME", "GENDER", "AGE", "HOSPITAL_NO", "DEPARTMENT",
    "SURGERY_DATE", "SURGERY_NAME", "SURGERY_NAME_ALT",
    "INCISION_LEVEL", "INCISION_HEALING", "ANESTHESIA_METHOD",
    "ANESTHESIA_DOCTOR", "SURGEON", "SURGEON_ASSISTANT",
    "OPERATING_NURSE", "BLOOD_LOSS", "BLOOD_TRANSFUSION",
    "FLUID_INFUSION", "SURGERY_DURATION", "INTRAOP_COMPLICATION",
    "INTRAOP_FINDING", "SPECIMEN", "DRAINAGE_TUBE"
]


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


def load_conll_data(file_path: str) -> List[Tuple[List[str], List[str]]]:
    """加载CONLL格式数据: 每行 '字 标签'，句子间空行分隔"""
    sentences = []
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


def generate_dummy_data(num_samples: int = 200) -> List[Tuple[List[str], List[str]]]:
    """生成模拟训练数据用于快速体验"""
    templates = [
        (
            "患者{name}，{gender}，{age}岁，住院号{hosp_no}。"
            "于{date}在{anes}下行{surgery}。"
            "手术医生：{surgeon}，助手：{assistant}。"
            "术中出血约{blood_loss}，未输血。"
            "手术顺利，术中无并发症。",
            {
                "name": "PATIENT_NAME", "gender": "GENDER", "age": "AGE",
                "hosp_no": "HOSPITAL_NO", "date": "SURGERY_DATE",
                "anes": "ANESTHESIA_METHOD", "surgery": "SURGERY_NAME",
                "surgeon": "SURGEON", "assistant": "SURGEON_ASSISTANT",
                "blood_loss": "BLOOD_LOSS"
            }
        ),
        (
            "患者{name}，{gender}，{age}岁。"
            "诊断：急性阑尾炎。"
            "行{surgery}，{anes}。"
            "切口等级：{incision}，愈合良好。"
            "出血{blood_loss}，输血{transfusion}。",
            {
                "name": "PATIENT_NAME", "gender": "GENDER", "age": "AGE",
                "surgery": "SURGERY_NAME", "anes": "ANESTHESIA_METHOD",
                "incision": "INCISION_LEVEL", "blood_loss": "BLOOD_LOSS",
                "transfusion": "BLOOD_TRANSFUSION"
            }
        )
    ]

    pool = {
        "name": ["张三", "李四", "王五", "赵六", "陈七", "刘八", "周九", "吴十"],
        "gender": ["男", "女"],
        "age": [str(i) for i in range(20, 80)],
        "hosp_no": [f"2024{str(i).zfill(6)}" for i in range(1000, 9999)],
        "date": ["2024-01-15", "2024-02-20", "2024-03-10", "2024-04-05"],
        "anes": ["全身麻醉", "硬膜外麻醉", "蛛网膜下腔麻醉", "局部浸润麻醉"],
        "surgery": [
            "腹腔镜下胆囊切除术", "阑尾切除术", "腹股沟疝修补术",
            "甲状腺次全切除术", "乳腺肿物切除术", "胃大部切除术"
        ],
        "surgeon": ["李主任", "王教授", "张副主任", "赵主治医师"],
        "assistant": ["陈医生", "刘医生", "周医生", "吴医生"],
        "blood_loss": ["50ml", "100ml", "150ml", "200ml", "300ml"],
        "transfusion": ["0ml", "200ml红细胞悬液", "400ml红细胞悬液"],
        "incision": ["I类切口", "II类切口", "III类切口"]
    }

    samples = []
    for _ in range(num_samples):
        tmpl, mapping = random.choice(templates)
        values = {k: random.choice(v) for k, v in pool.items()}
        text = tmpl.format(**values)
        chars = list(text)
        labels = ["O"] * len(chars)

        cursor = 0
        for key, etype in mapping.items():
            val = values[key]
            idx = text.find(val, cursor)
            if idx >= 0:
                labels[idx] = f"B-{etype}"
                for j in range(idx + 1, idx + len(val)):
                    labels[j] = f"I-{etype}"
                cursor = idx + len(val)

        samples.append((chars, labels))
    return samples


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
        lstm_dropout: float = 0.3
    ):
        super().__init__()
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
    """将预测结果与原始标签对齐，忽略-100（subtoken非首字、CLS、SEP、PAD）"""
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


def train(args):
    set_seed(args.seed)
    device = torch.device("cuda" if torch.cuda.is_available() and not args.no_cuda else "cpu")
    print(f"使用设备: {device}")

    model_dir = Path(args.model_dir)
    model_dir.mkdir(parents=True, exist_ok=True)
    checkpoint_dir = model_dir / "checkpoints"
    checkpoint_dir.mkdir(exist_ok=True)

    label2id = build_label2id(ENTITY_TYPES)
    id2label = id2label_from(label2id)
    num_labels = len(label2id)
    print(f"标签数量: {num_labels}")

    with open(model_dir / "label2id.json", "w", encoding="utf-8") as f:
        json.dump(label2id, f, ensure_ascii=False, indent=2)

    if args.use_dummy_data:
        print("使用Dummy数据进行训练...")
        all_data = generate_dummy_data(300)
        random.shuffle(all_data)
        n = len(all_data)
        train_data = all_data[: int(n * 0.8)]
        dev_data = all_data[int(n * 0.8): int(n * 0.9)]
        test_data = all_data[int(n * 0.9):]
    else:
        if not (args.train_data and os.path.exists(args.train_data)):
            print(f"训练数据不存在: {args.train_data}")
            sys.exit(1)
        train_data = load_conll_data(args.train_data)
        dev_data = load_conll_data(args.dev_data) if args.dev_data else train_data[-200:]
        test_data = load_conll_data(args.test_data) if args.test_data else dev_data

    print(f"样本数: train={len(train_data)}, dev={len(dev_data)}, test={len(test_data)}")

    tokenizer = AutoTokenizer.from_pretrained(args.bert_model)
    try:
        tokenizer.save_pretrained(model_dir / "tokenizer")
    except Exception:
        pass

    train_dataset = NerDataset(train_data, tokenizer, label2id, args.max_seq_length)
    dev_dataset = NerDataset(dev_data, tokenizer, label2id, args.max_seq_length)
    test_dataset = NerDataset(test_data, tokenizer, label2id, args.max_seq_length)

    train_loader = DataLoader(train_dataset, batch_size=args.batch_size, shuffle=True)
    dev_loader = DataLoader(dev_dataset, batch_size=args.batch_size)
    test_loader = DataLoader(test_dataset, batch_size=args.batch_size)

    model = BertBiLSTMCRF(
        bert_model_name=args.bert_model,
        num_labels=num_labels,
        hidden_dim=args.hidden_dim,
        lstm_layers=args.lstm_layers,
        lstm_dropout=args.lstm_dropout
    ).to(device)

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

    print("=" * 60)
    print("开始训练...")
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
            torch.save(model.state_dict(), model_dir / "pytorch_model.bin")
            print(f"  -> 模型已保存 (当前最佳 F1={best_f1:.4f})")
        else:
            patience_counter += 1
            print(f"  -> F1未提升，patience={patience_counter}/{args.early_stopping_patience}")
            if patience_counter >= args.early_stopping_patience:
                print("早停触发，训练结束。")
                break

    print("\n" + "=" * 60)
    print("加载最佳模型在测试集上评估...")
    print("=" * 60)

    model.load_state_dict(torch.load(model_dir / "pytorch_model.bin", map_location=device))
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

    print(f"\n训练完成！模型已保存到: {model_dir}")
    print(f"最佳验证 F1: {best_f1:.4f}")


def main():
    parser = argparse.ArgumentParser(description="手术记录BiLSTM-CRF实体识别模型训练")
    parser.add_argument("--train_data", type=str, default=None, help="训练数据路径(CONLL格式)")
    parser.add_argument("--dev_data", type=str, default=None, help="验证数据路径")
    parser.add_argument("--test_data", type=str, default=None, help="测试数据路径")
    parser.add_argument("--model_dir", type=str, default="./models/surgery-ner", help="模型保存目录")
    parser.add_argument("--bert_model", type=str, default="bert-base-chinese", help="BERT预训练模型")
    parser.add_argument("--epochs", type=int, default=30)
    parser.add_argument("--batch_size", type=int, default=16)
    parser.add_argument("--max_seq_length", type=int, default=512)
    parser.add_argument("--learning_rate", type=float, default=0.001, help="BiLSTM/CRF学习率")
    parser.add_argument("--bert_learning_rate", type=float, default=2e-5, help="BERT微调学习率")
    parser.add_argument("--weight_decay", type=float, default=0.01)
    parser.add_argument("--warmup_steps", type=int, default=100)
    parser.add_argument("--hidden_dim", type=int, default=256, help="LSTM隐藏层维度")
    parser.add_argument("--lstm_layers", type=int, default=2, help="LSTM层数")
    parser.add_argument("--lstm_dropout", type=float, default=0.3)
    parser.add_argument("--early_stopping_patience", type=int, default=5)
    parser.add_argument("--seed", type=int, default=42)
    parser.add_argument("--no_cuda", action="store_true", help="不使用GPU")
    parser.add_argument("--use_dummy_data", action="store_true", help="使用模拟数据快速体验")
    args = parser.parse_args()
    train(args)


if __name__ == "__main__":
    main()
