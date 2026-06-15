import os
import re
import time
import sys
import numpy as np
from loguru import logger
from typing import List, Dict, Any, Optional

from app.config import get_settings


ENTITY_LABELS = [
    "O",
    "B-PATIENT_NAME", "I-PATIENT_NAME",
    "B-GENDER", "I-GENDER",
    "B-AGE", "I-AGE",
    "B-HOSPITAL_NO", "I-HOSPITAL_NO",
    "B-DEPARTMENT", "I-DEPARTMENT",
    "B-SURGERY_DATE", "I-SURGERY_DATE",
    "B-SURGERY_NAME", "I-SURGERY_NAME",
    "B-SURGERY_NAME_ALT", "I-SURGERY_NAME_ALT",
    "B-INCISION_LEVEL", "I-INCISION_LEVEL",
    "B-INCISION_HEALING", "I-INCISION_HEALING",
    "B-ANESTHESIA_METHOD", "I-ANESTHESIA_METHOD",
    "B-ANESTHESIA_DOCTOR", "I-ANESTHESIA_DOCTOR",
    "B-SURGEON", "I-SURGEON",
    "B-SURGEON_ASSISTANT", "I-SURGEON_ASSISTANT",
    "B-OPERATING_NURSE", "I-OPERATING_NURSE",
    "B-BLOOD_LOSS", "I-BLOOD_LOSS",
    "B-BLOOD_TRANSFUSION", "I-BLOOD_TRANSFUSION",
    "B-FLUID_INFUSION", "I-FLUID_INFUSION",
    "B-SURGERY_DURATION", "I-SURGERY_DURATION",
    "B-INTRAOP_COMPLICATION", "I-INTRAOP_COMPLICATION",
    "B-INTRAOP_FINDING", "I-INTRAOP_FINDING",
    "B-SPECIMEN", "I-SPECIMEN",
    "B-DRAINAGE_TUBE", "I-DRAINAGE_TUBE",
]


ENTITY_TYPE_ALIASES = {
    "ANESTHESIA_TYPE": "ANESTHESIA_METHOD",
    "COMPLICATION": "INTRAOP_COMPLICATION",
    "ASSISTANT": "SURGEON_ASSISTANT",
    "ANESTHESIOLOGIST": "ANESTHESIA_DOCTOR",
    "SCRUB_NURSE": "OPERATING_NURSE",
    "CIRCULATING_NURSE": "OPERATING_NURSE",
    "SURGERY_LEVEL": "SURGERY_NAME_ALT",
    "SURGERY_PROCEDURE": "SURGERY_NAME",
    "HOSPITALIZATION_NO": "HOSPITAL_NO",
    "PATIENT_ID": "HOSPITAL_NO",
    "OPERATION_DATE": "SURGERY_DATE",
    "OPERATION_NAME": "SURGERY_NAME",
    "INCISION_GRADE": "INCISION_LEVEL",
    "WOUND_HEALING": "INCISION_HEALING",
    "BLOOD_VOLUME": "BLOOD_LOSS",
    "TRANSFUSION_VOLUME": "BLOOD_TRANSFUSION",
    "INFUSION_VOLUME": "FLUID_INFUSION",
    "OPERATION_DURATION": "SURGERY_DURATION",
    "COMPLICATIONS": "INTRAOP_COMPLICATION",
    "SURGICAL_FINDINGS": "INTRAOP_FINDING",
    "OP_SURGEON": "SURGEON",
    "OP_ASSISTANT": "SURGEON_ASSISTANT",
    "OP_NURSE": "OPERATING_NURSE",
}


class NerModelService:
    def __init__(self):
        self.settings = get_settings()
        self.inference = None
        self.model_loaded = False
        self.model_weight_exists = False
        self.label_to_id = {label: i for i, label in enumerate(ENTITY_LABELS)}
        self.id_to_label = {i: label for i, label in enumerate(ENTITY_LABELS)}

        self._gpu_optimizer = None
        self._effective_device = self._resolve_device()
        self._try_load_model()

    def _resolve_device(self) -> str:
        try:
            from app.services.gpu_optimizer import get_gpu_optimizer
            gpu_opt = get_gpu_optimizer()
            self._gpu_optimizer = gpu_opt
            device_str = str(gpu_opt.device)
            logger.info(f"[NER模型] 设备解析: {device_str}")
            return device_str
        except Exception as e:
            logger.warning(f"[NER模型] GPU优化器不可用，使用配置设备: {e}")
            return self.settings.device

    def _try_load_model(self):
        model_dir = os.path.join(self.settings.model_dir, "surgery-ner")
        model_weight_path = os.path.join(model_dir, "pytorch_model.bin")
        label_path = os.path.join(model_dir, "label2id.json")

        if not os.path.exists(label_path):
            logger.warning(
                f"[NER模型] 标签文件不存在: {label_path}。"
                f"请参考 models/surgery-ner/README.md 下载或训练模型。"
                f"当前将仅使用正则抽取和规则引擎完成实体抽取。"
            )
            self.model_loaded = False
            return

        if not os.path.exists(model_weight_path):
            logger.warning(
                f"[NER模型] 模型权重文件不存在: {model_weight_path}。"
                f"请从 models/surgery-ner/README.md 中的下载地址获取预训练权重，"
                f"或运行 scripts/train_bilstm_crf.py 自行训练。"
                f"当前将仅使用正则抽取和规则引擎完成实体抽取。"
            )
            self.model_loaded = False
            return

        try:
            scripts_path = os.path.join(
                os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
                "scripts"
            )
            if scripts_path not in sys.path:
                sys.path.insert(0, scripts_path)
            from infer_bilstm_crf import SurgeryNerInference

            self.inference = SurgeryNerInference(
                model_dir=model_dir,
                bert_model_name=self.settings.bert_model_name,
                max_seq_length=min(self.settings.max_text_length, 512),
                device=self._effective_device,
            )
            self.model_loaded = True
            self.model_weight_exists = True
            logger.info(
                f"[NER模型] BiLSTM-CRF模型加载成功: {model_dir}, "
                f"设备: {self._effective_device}"
            )

            if self._gpu_optimizer and self._effective_device.startswith("cuda"):
                self._warmup_gpu()

        except ImportError as e:
            logger.warning(
                f"[NER模型] 深度学习依赖未安装，无法加载模型: {e}。"
                f"请执行: pip install torch transformers seqeval。"
                f"当前将仅使用正则抽取和规则引擎。"
            )
            self.model_loaded = False
        except Exception as e:
            logger.warning(
                f"[NER模型] 加载失败: {e}。当前将仅使用正则抽取和规则引擎。",
                exc_info=True,
            )
            self.model_loaded = False

    def _warmup_gpu(self):
        try:
            import torch
            sample_input_ids = torch.tensor(
                [[101] + [0] * 31], dtype=torch.long, device=self._effective_device
            )
            sample_attention_mask = torch.tensor(
                [[1] * 32], dtype=torch.long, device=self._effective_device
            )
            sample_input = {"input_ids": sample_input_ids, "attention_mask": sample_attention_mask}
            self._gpu_optimizer.warmup_model(self.inference.model, sample_input, iterations=3)
            logger.info("[NER模型] GPU预热完成")
        except Exception as e:
            logger.debug(f"[NER模型] GPU预热跳过: {e}")

    def predict(self, text: str) -> List[Dict[str, Any]]:
        if not text or not text.strip():
            return []

        if not self.model_loaded or self.inference is None:
            return []

        try:
            if self._gpu_optimizer and self._effective_device.startswith("cuda"):
                raw_entities = self._gpu_optimizer.optimize_inference(self.inference.predict)(text)
            else:
                raw_entities = self.inference.predict(text)
            return [self._normalize_entity(e) for e in raw_entities if e]
        except Exception as e:
            logger.error(f"[NER模型] 预测失败，返回空列表（将由正则和规则引擎补全）: {e}", exc_info=True)
            return []

    def _normalize_entity(self, e: Dict[str, Any]) -> Dict[str, Any]:
        entity_type = e.get("entity_type", "")
        entity_type = ENTITY_TYPE_ALIASES.get(entity_type, entity_type)

        text_val = e.get("text") or e.get("entity_value") or e.get("value") or ""
        text_val = str(text_val).strip()
        if not text_val:
            return None

        start = e.get("start_pos", e.get("start", 0))
        end = e.get("end_pos", e.get("end", start + len(text_val)))
        conf = float(e.get("confidence", 0.7))
        if conf < 0 or conf > 1:
            conf = max(0.0, min(1.0, conf))

        return {
            "entity_type": entity_type,
            "entity_value": text_val,
            "entity_unit": self._extract_unit(text_val, entity_type),
            "confidence": round(conf, 4),
            "source": e.get("source", "MODEL"),
            "start_pos": int(start),
            "end_pos": int(end),
            "original_text": text_val,
        }

    @staticmethod
    def _extract_unit(text: str, entity_type: str) -> Optional[str]:
        if entity_type in ("BLOOD_LOSS", "BLOOD_TRANSFUSION", "FLUID_INFUSION"):
            m = re.search(r"(ml|mL|ML|cc|毫升|ml/单位|单位)", text, re.IGNORECASE)
            if m:
                unit = m.group(1)
                if unit == "毫升":
                    return "ml"
                return unit.lower()
        if entity_type == "SURGERY_DURATION":
            m = re.search(r"(小时|分钟|min|h|hr)", text, re.IGNORECASE)
            if m:
                unit = m.group(1).lower()
                if unit in ("h", "hr", "小时"):
                    return "小时"
                if unit in ("min", "分钟"):
                    return "分钟"
        return None
