"""
BiLSTM-CRF 手术记录实体识别模型 - 独立推理脚本

提供与训练脚本解耦的独立推理能力：
  - 命令行单条文本推理
  - 批量文件推理
  - Python API 调用
  - 输出JSON格式结果（实体类型、文本、起止位置、置信度）

使用示例:
  # 命令行推理
  python infer_bilstm_crf.py \
    --model_dir ./models/surgery-ner \
    --text "患者张三，男，45岁，住院号2024001234，行腹腔镜下胆囊切除术，出血约50ml。"

  # 批量推理
  python infer_bilstm_crf.py \
    --model_dir ./models/surgery-ner \
    --input_file ./test_texts.txt \
    --output_file ./result.json

  # Python API
  from scripts.infer_bilstm_crf import SurgeryNerInference
  infer = SurgeryNerInference(model_dir="./models/surgery-ner")
  result = infer.predict("患者张三，男，45岁...")
"""

import os
import sys
import json
import argparse
from pathlib import Path
from typing import List, Dict, Any, Optional

import torch
import torch.nn as nn

try:
    from transformers import AutoTokenizer, AutoModel
except ImportError:
    print("请安装 transformers: pip install transformers")
    sys.exit(1)


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
    ):
        outputs = self.bert(input_ids=input_ids, attention_mask=attention_mask)
        sequence_output = outputs.last_hidden_state
        sequence_output = self.dropout(sequence_output)
        lstm_out, _ = self.bilstm(sequence_output)
        feats = self.linear(lstm_out)
        pred_tags = self.crf.decode(feats, attention_mask)
        confidences = self._compute_confidence(feats, attention_mask, pred_tags)
        return pred_tags, confidences

    def _compute_confidence(
        self, feats: torch.Tensor, mask: torch.Tensor, pred_tags: List[List[int]]
    ) -> List[List[float]]:
        batch_size, seq_len, num_tags = feats.shape
        probs = torch.softmax(feats, dim=-1)
        conf_batch = []
        for i in range(batch_size):
            seq_len_i = int(mask[i].sum().item())
            confs = []
            for j in range(min(seq_len_i, len(pred_tags[i]))):
                tag_id = pred_tags[i][j]
                if 0 <= tag_id < num_tags:
                    confs.append(float(probs[i, j, tag_id].item()))
                else:
                    confs.append(0.0)
            conf_batch.append(confs)
        return conf_batch


class SurgeryNerInference:
    """手术记录实体识别推理器"""

    def __init__(
        self,
        model_dir: str,
        bert_model_name: Optional[str] = None,
        max_seq_length: int = 512,
        device: Optional[str] = None,
    ):
        self.model_dir = Path(model_dir)
        self.max_seq_length = max_seq_length

        if device is None:
            device = "cuda" if torch.cuda.is_available() else "cpu"
        self.device = torch.device(device)

        config_path = self.model_dir / "config.json"
        if config_path.exists():
            with open(config_path, "r", encoding="utf-8") as f:
                self.config = json.load(f)
        else:
            self.config = {
                "architecture": {
                    "bilstm_hidden_dim": 256,
                    "bilstm_layers": 2,
                    "bilstm_dropout": 0.3,
                }
            }

        label_path = self.model_dir / "label2id.json"
        if not label_path.exists():
            raise FileNotFoundError(f"标签映射文件不存在: {label_path}")
        with open(label_path, "r", encoding="utf-8") as f:
            self.label2id = json.load(f)
        self.id2label = {v: k for k, v in self.label2id.items()}
        self.num_labels = len(self.label2id)

        tokenizer_dir = self.model_dir / "tokenizer"
        if tokenizer_dir.exists():
            self.tokenizer = AutoTokenizer.from_pretrained(str(tokenizer_dir), local_files_only=True)
        else:
            if bert_model_name is None:
                bert_model_name = "bert-base-chinese"
            try:
                from app.config import get_settings
                settings = get_settings()
                if settings.offline_mode and os.path.exists(settings.bert_local_path):
                    self.tokenizer = AutoTokenizer.from_pretrained(settings.bert_local_path, local_files_only=True)
                else:
                    self.tokenizer = AutoTokenizer.from_pretrained(bert_model_name)
            except Exception:
                self.tokenizer = AutoTokenizer.from_pretrained(bert_model_name)

        if bert_model_name is None:
            bert_model_name = "bert-base-chinese"

        arch = self.config.get("architecture", {})
        bert_local_path = None
        try:
            from app.config import get_settings
            settings = get_settings()
            if settings.offline_mode and os.path.exists(settings.bert_local_path):
                bert_local_path = settings.bert_local_path
        except Exception:
            pass

        self.model = BertBiLSTMCRF(
            bert_model_name=bert_local_path or bert_model_name,
            num_labels=self.num_labels,
            hidden_dim=arch.get("bilstm_hidden_dim", 256),
            lstm_layers=arch.get("bilstm_layers", 2),
            lstm_dropout=arch.get("bilstm_dropout", 0.3),
            offline_mode=bert_local_path is not None,
        ).to(self.device)

        model_path = self.model_dir / "pytorch_model.bin"
        if not model_path.exists():
            print(f"[警告] 模型权重不存在: {model_path}")
            print("       请先训练模型或下载预训练权重（参见 models/surgery-ner/README.md）")
            print("       当前将使用未训练的随机权重进行推理，结果不可用。")
        else:
            state_dict = torch.load(model_path, map_location=self.device)
            self.model.load_state_dict(state_dict, strict=False)
        self.model.eval()

    def predict(self, text: str) -> List[Dict[str, Any]]:
        """
        对单条文本进行实体识别。

        Args:
            text: 手术记录原始文本

        Returns:
            List[Dict], 每个字典包含:
                - entity_type: 实体类型（如 SURGERY_NAME）
                - text: 实体文本
                - start_pos: 起始字符位置
                - end_pos: 结束字符位置（开区间）
                - confidence: 置信度 (0~1)
        """
        if not text:
            return []

        chars = list(text)
        tokens = []
        char_to_token = []
        for i, ch in enumerate(chars):
            sub = self.tokenizer.tokenize(ch)
            if not sub:
                sub = [self.tokenizer.unk_token]
            char_to_token.append(len(tokens))
            tokens.extend(sub)

        if len(tokens) > self.max_seq_length - 2:
            tokens = tokens[: self.max_seq_length - 2]

        input_tokens = [self.tokenizer.cls_token] + tokens + [self.tokenizer.sep_token]
        input_ids = self.tokenizer.convert_tokens_to_ids(input_tokens)
        attention_mask = [1] * len(input_ids)

        padding_len = self.max_seq_length - len(input_ids)
        if padding_len > 0:
            input_ids += [self.tokenizer.pad_token_id] * padding_len
            attention_mask += [0] * padding_len

        input_ids_tensor = torch.tensor([input_ids], dtype=torch.long, device=self.device)
        attention_mask_tensor = torch.tensor([attention_mask], dtype=torch.long, device=self.device)

        with torch.no_grad():
            pred_tags, confidences = self.model(input_ids_tensor, attention_mask_tensor)

        token_offset = 1
        pred_tags_seq = pred_tags[0]
        conf_seq = confidences[0]

        char_labels = ["O"] * len(chars)
        char_confs = [0.0] * len(chars)
        for ci, ti in enumerate(char_to_token):
            token_pos = token_offset + ti
            if token_pos < len(pred_tags_seq):
                tag_id = pred_tags_seq[token_pos]
                char_labels[ci] = self.id2label.get(tag_id, "O")
                char_confs[ci] = conf_seq[token_pos] if token_pos < len(conf_seq) else 0.0

        entities = []
        i = 0
        while i < len(chars):
            label = char_labels[i]
            if label.startswith("B-"):
                etype = label[2:]
                start = i
                conf_sum = char_confs[i]
                count = 1
                j = i + 1
                while j < len(chars) and char_labels[j] == f"I-{etype}":
                    conf_sum += char_confs[j]
                    count += 1
                    j += 1
                text_span = "".join(chars[start:j])
                avg_conf = conf_sum / max(count, 1)
                entities.append({
                    "entity_type": etype,
                    "text": text_span,
                    "start_pos": start,
                    "end_pos": j,
                    "confidence": round(avg_conf, 4),
                    "source": "MODEL"
                })
                i = j
            else:
                i += 1

        entities.sort(key=lambda x: x["start_pos"])
        return entities

    def predict_batch(self, texts: List[str]) -> List[List[Dict[str, Any]]]:
        """批量推理"""
        return [self.predict(t) for t in texts]


def print_entities(text: str, entities: List[Dict[str, Any]]):
    """在控制台友好打印识别结果"""
    print(f"\n输入文本: {text}")
    if not entities:
        print("  (未识别到任何实体)")
        return
    print(f"\n识别到 {len(entities)} 个实体:")
    print("-" * 80)
    print(f"{'类型':<25} {'文本':<20} {'位置':<12} {'置信度':<8}")
    print("-" * 80)
    for e in entities:
        print(
            f"{e['entity_type']:<25} "
            f"{e['text']:<20} "
            f"{e['start_pos']}-{e['end_pos']:<7} "
            f"{e['confidence']:.4f}"
        )
    print("-" * 80)


def main():
    parser = argparse.ArgumentParser(description="手术记录BiLSTM-CRF实体识别推理")
    parser.add_argument("--model_dir", type=str, default="./models/surgery-ner", help="模型目录")
    parser.add_argument("--bert_model", type=str, default=None, help="BERT模型名（未保存tokenizer时使用）")
    parser.add_argument("--text", type=str, default=None, help="要推理的单条文本")
    parser.add_argument("--input_file", type=str, default=None, help="批量输入文件（每行一条文本）")
    parser.add_argument("--output_file", type=str, default=None, help="批量输出JSON文件路径")
    parser.add_argument("--max_seq_length", type=int, default=512)
    parser.add_argument("--no_cuda", action="store_true")
    parser.add_argument("--json", action="store_true", help="仅输出JSON格式")
    args = parser.parse_args()

    device = None
    if args.no_cuda:
        device = "cpu"

    infer = SurgeryNerInference(
        model_dir=args.model_dir,
        bert_model_name=args.bert_model,
        max_seq_length=args.max_seq_length,
        device=device,
    )

    if args.text:
        entities = infer.predict(args.text)
        if args.json:
            print(json.dumps({"text": args.text, "entities": entities}, ensure_ascii=False, indent=2))
        else:
            print_entities(args.text, entities)
        return

    if args.input_file:
        if not os.path.exists(args.input_file):
            print(f"输入文件不存在: {args.input_file}")
            sys.exit(1)
        with open(args.input_file, "r", encoding="utf-8") as f:
            lines = [line.strip() for line in f if line.strip()]
        results = []
        for line in lines:
            entities = infer.predict(line)
            results.append({"text": line, "entities": entities})
        if args.output_file:
            with open(args.output_file, "w", encoding="utf-8") as f:
                json.dump(results, f, ensure_ascii=False, indent=2)
            print(f"结果已保存到: {args.output_file}")
        else:
            print(json.dumps(results, ensure_ascii=False, indent=2))
        return

    print("请使用 --text 提供单条文本，或 --input_file 提供批量输入文件")
    print("示例:")
    print('  python infer_bilstm_crf.py --text "患者张三，男，45岁，行腹腔镜下胆囊切除术。"')


if __name__ == "__main__":
    main()
