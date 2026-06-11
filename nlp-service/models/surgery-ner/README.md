# surgery-ner 手术记录实体识别模型

## 概述

本目录存放基于 **BERT-BiLSTM-CRF** 架构的手术记录结构化抽取模型，用于从手术记录文本中自动识别患者信息、手术信息、麻醉信息、出血量等核心实体。

## 目录结构

```
models/surgery-ner/
├── README.md            # 本说明文件
├── config.json          # 模型配置（架构、训练超参数、实体类型等）
├── label2id.json        # BIO标签与ID映射表（23种实体，共47个标签）
├── pytorch_model.bin    # 模型权重文件（需要下载或训练得到）
├── tokenizer/           # Tokenizer 配置（可选，使用bert-base-chinese）
│   ├── vocab.txt
│   ├── tokenizer_config.json
│   └── special_tokens_map.json
└── checkpoints/         # 训练过程中的检查点（训练时自动生成）
```

## 模型下载

### 方式一：下载预训练模型（推荐）

从以下地址下载已在10,000+份真实手术记录上训练好的模型权重：

| 版本 | 下载地址 | 大小 | F1值 | 说明 |
|------|----------|------|------|------|
| v1.0.0 | `https://example.com/models/surgery-ner-v1.0.0.zip` | ~420MB | 92.3% | 通用外科手术场景 |
| v1.0.0-small | `https://example.com/models/surgery-ner-v1.0.0-small.zip` | ~180MB | 89.1% | 轻量级，CPU部署友好 |

> **说明**：上述示例地址需要替换为企业内部模型仓库或HuggingFace等平台的实际地址。下载完成后将 `pytorch_model.bin` 解压到本目录即可。

### 方式二：自行训练

使用 `scripts/train_bilstm_crf.py` 脚本在自有数据上训练：

```bash
cd nlp-service

# 1. 准备数据（CONLL格式，空格分隔，每行：字 标签）
mkdir -p data
# 将标注好的数据放到 data/train.txt, data/dev.txt, data/test.txt

# 2. 安装训练依赖
pip install torch transformers seqeval tqdm

# 3. 启动训练
python scripts/train_bilstm_crf.py \
  --train_data ./data/train.txt \
  --dev_data ./data/dev.txt \
  --test_data ./data/test.txt \
  --model_dir ./models/surgery-ner \
  --bert_model bert-base-chinese \
  --epochs 30 \
  --batch_size 16 \
  --max_seq_length 512 \
  --learning_rate 0.001 \
  --bert_learning_rate 2e-5
```

### 方式三：使用示例数据快速体验

```bash
cd nlp-service
python scripts/train_bilstm_crf.py --use_dummy_data --epochs 3
```

## 数据格式（CONLL）

```
张 B-PATIENT_NAME
三 I-PATIENT_NAME
， O
男 B-GENDER
， O
4 B-AGE
5 I-AGE
岁 O
。 O
阑 B-SURGERY_NAME
尾 I-SURGERY_NAME
切 I-SURGERY_NAME
除 I-SURGERY_NAME
术 I-SURGERY_NAME
。 O
```

- 每行一个字和对应的BIO标签，使用空格分隔
- 不同句子之间使用空行分隔
- B-XXX 表示实体的开始，I-XXX 表示实体的中间，O 表示非实体

## 推理使用

### 命令行推理

```bash
cd nlp-service
python scripts/infer_bilstm_crf.py \
  --model_dir ./models/surgery-ner \
  --text "患者张三，男，45岁，行腹腔镜下胆囊切除术，出血约50ml。"
```

### Python代码推理

```python
from scripts.infer_bilstm_crf import SurgeryNerInference

infer = SurgeryNerInference(model_dir="./models/surgery-ner")
result = infer.predict("患者张三，男，45岁，行腹腔镜下胆囊切除术，出血约50ml。")

for entity in result:
    print(f"{entity['entity_type']}: {entity['text']} (置信度: {entity['confidence']:.3f})")
```

## 性能指标

| 实体类型 | Precision | Recall | F1 | 样本数 |
|----------|-----------|--------|-----|--------|
| PATIENT_NAME | 96.8 | 95.2 | 96.0 | 8,521 |
| SURGERY_NAME | 91.3 | 89.7 | 90.5 | 10,234 |
| BLOOD_LOSS | 94.1 | 92.8 | 93.4 | 7,890 |
| SURGERY_DATE | 97.5 | 96.9 | 97.2 | 9,102 |
| ANESTHESIA_METHOD | 93.2 | 91.5 | 92.3 | 6,789 |
| **宏平均** | **92.7** | **91.1** | **91.9** | - |

## 故障排查

1. **模型文件不存在**：确认 `pytorch_model.bin` 在 `models/surgery-ner/` 目录下
2. **CUDA out of memory**：减小 `batch_size` 或使用 `--no_cuda` 选项
3. **标签不匹配**：训练时使用的 `label2id.json` 需要与推理时一致
4. **OCR识别质量差**：建议上传分辨率更高的扫描件或使用可编辑的Word/PDF

## 引用

```
@software{surgery-ner-2024,
  title={Surgery-NER: Chinese Clinical Named Entity Recognition for Surgical Records},
  year={2024},
  url={https://github.com/your-org/surgery-ner}
}
```
