import os
import re
import json
import time
from loguru import logger
from typing import List, Dict, Any, Optional, Tuple
from collections import defaultdict
from difflib import SequenceMatcher

from app.config import get_settings


class CustomNerModel:
    """自定义NER模型，基于少样本学习实现"""

    def __init__(self, field_id: int, entity_type: str, department: str, field_code: str):
        self.field_id = field_id
        self.entity_type = entity_type
        self.department = department
        self.field_code = field_code
        self.status = "NOT_TRAINED"
        self.model_version = None
        self.train_time = None
        self.sample_count = 0

        self.patterns: List[Dict[str, Any]] = []
        self.entity_dictionary: set = set()
        self.left_contexts: List[str] = []
        self.right_contexts: List[str] = []
        self.avg_entity_length = 0
        self.entity_suffixes: List[str] = []
        self.entity_prefixes: List[str] = []

    def train(self, samples: List[Dict[str, Any]]) -> bool:
        """使用样本训练模型（少样本学习）"""
        if len(samples) < 3:
            return False

        self.sample_count = len(samples)
        entities = []
        total_len = 0

        for sample in samples:
            text = sample.get("text", "")
            entity_value = sample.get("entity_value", "")
            start_pos = sample.get("start_pos")
            end_pos = sample.get("end_pos")

            if not entity_value or not text:
                continue

            if start_pos is None or end_pos is None:
                idx = text.find(entity_value)
                if idx >= 0:
                    start_pos = idx
                    end_pos = idx + len(entity_value)
                else:
                    continue

            if start_pos >= 0 and end_pos <= len(text):
                entities.append({
                    "value": entity_value,
                    "start": start_pos,
                    "end": end_pos,
                    "text": text
                })
                total_len += len(entity_value)

                left_ctx = text[max(0, start_pos - 15):start_pos]
                right_ctx = text[end_pos:min(len(text), end_pos + 15)]
                if left_ctx:
                    self.left_contexts.append(left_ctx)
                if right_ctx:
                    self.right_contexts.append(right_ctx)

                if len(entity_value) >= 3:
                    self.entity_prefixes.append(entity_value[:3])
                if len(entity_value) >= 2:
                    self.entity_suffixes.append(entity_value[-2:])

                self.entity_dictionary.add(entity_value)

        if len(entities) < 3:
            return False

        self.avg_entity_length = total_len / len(entities)
        self._generate_patterns(entities)
        self.status = "TRAINED"
        self.model_version = f"v1.0_{int(time.time())}"
        self.train_time = time.time()
        return True

    def _generate_patterns(self, entities: List[Dict[str, Any]]):
        """从样本中生成抽取模式"""
        left_patterns = defaultdict(int)
        right_patterns = defaultdict(int)

        for ent in entities:
            text = ent["text"]
            start = ent["start"]
            end = ent["end"]

            for ctx_len in range(2, 10):
                if start >= ctx_len:
                    ctx = text[start - ctx_len:start]
                    left_patterns[ctx] += 1
                if end + ctx_len <= len(text):
                    ctx = text[end:end + ctx_len]
                    right_patterns[ctx] += 1

        for pattern, count in sorted(left_patterns.items(), key=lambda x: -x[1])[:10]:
            if count >= 1:
                self.patterns.append({
                    "type": "left",
                    "pattern": re.escape(pattern),
                    "weight": min(1.0, count / len(entities))
                })

        for pattern, count in sorted(right_patterns.items(), key=lambda x: -x[1])[:10]:
            if count >= 1:
                self.patterns.append({
                    "type": "right",
                    "pattern": re.escape(pattern),
                    "weight": min(1.0, count / len(entities))
                })

    def predict(self, text: str) -> List[Dict[str, Any]]:
        """从文本中抽取实体"""
        results = []

        for ent_value in self.entity_dictionary:
            for match in re.finditer(re.escape(ent_value), text):
                results.append({
                    "entity_type": self.entity_type,
                    "entity_value": ent_value,
                    "entity_unit": None,
                    "confidence": 0.85,
                    "source": "CUSTOM_NER",
                    "start_pos": match.start(),
                    "end_pos": match.end(),
                    "original_text": ent_value,
                    "method": "dictionary"
                })

        for pattern_info in self.patterns:
            if pattern_info["type"] == "left":
                regex = pattern_info["pattern"] + r"(.{2,30}?)(?=[，。；！？,\.;\s]|$)"
                try:
                    for match in re.finditer(regex, text):
                        value = match.group(1).strip()
                        if len(value) >= 2 and self._is_likely_entity(value):
                            conf = 0.5 + pattern_info["weight"] * 0.3
                            results.append({
                                "entity_type": self.entity_type,
                                "entity_value": value,
                                "entity_unit": None,
                                "confidence": round(conf, 4),
                                "source": "CUSTOM_NER",
                                "start_pos": match.start(1),
                                "end_pos": match.end(1),
                                "original_text": value,
                                "method": "left_pattern"
                            })
                except Exception:
                    pass

            elif pattern_info["type"] == "right":
                regex = r"(?<=[，。、\s]|^)(.{2,30}?)" + pattern_info["pattern"]
                try:
                    for match in re.finditer(regex, text):
                        value = match.group(1).strip()
                        if len(value) >= 2 and self._is_likely_entity(value):
                            conf = 0.5 + pattern_info["weight"] * 0.3
                            results.append({
                                "entity_type": self.entity_type,
                                "entity_value": value,
                                "entity_unit": None,
                                "confidence": round(conf, 4),
                                "source": "CUSTOM_NER",
                                "start_pos": match.start(1),
                                "end_pos": match.end(1),
                                "original_text": value,
                                "method": "right_pattern"
                            })
                except Exception:
                    pass

        return self._deduplicate(results)

    def _is_likely_entity(self, value: str) -> bool:
        """判断是否可能是实体值"""
        if len(value) < 2 or len(value) > 50:
            return False
        if re.match(r'^[，。、；！？,\.;\s]+$', value):
            return False
        return True

    def _deduplicate(self, entities: List[Dict[str, Any]]) -> List[Dict[str, Any]]:
        """去重，保留置信度最高的"""
        seen = {}
        for ent in entities:
            key = (ent["start_pos"], ent["end_pos"])
            if key not in seen or ent["confidence"] > seen[key]["confidence"]:
                seen[key] = ent

        dict_values = {e["entity_value"] for e in entities if e.get("method") == "dictionary"}
        result = [e for e in seen.values()]
        result.sort(key=lambda x: x["start_pos"])
        return result

    def to_dict(self) -> Dict[str, Any]:
        return {
            "field_id": self.field_id,
            "entity_type": self.entity_type,
            "department": self.department,
            "field_code": self.field_code,
            "status": self.status,
            "model_version": self.model_version,
            "train_time": self.train_time,
            "sample_count": self.sample_count,
            "entity_count": len(self.entity_dictionary),
            "pattern_count": len(self.patterns)
        }


class CustomNerService:
    """自定义NER服务管理"""

    def __init__(self):
        self.settings = get_settings()
        self.models: Dict[str, CustomNerModel] = {}
        self.model_dir = os.path.join(self.settings.model_dir, "custom-ner")
        os.makedirs(self.model_dir, exist_ok=True)
        self._load_existing_models()
        logger.info("自定义NER服务初始化完成")

    def _load_existing_models(self):
        """加载已存在的模型"""
        if not os.path.exists(self.model_dir):
            return
        for filename in os.listdir(self.model_dir):
            if filename.endswith(".json"):
                try:
                    filepath = os.path.join(self.model_dir, filename)
                    with open(filepath, "r", encoding="utf-8") as f:
                        data = json.load(f)
                    field_id = data.get("field_id")
                    if field_id:
                        key = str(field_id)
                        model = CustomNerModel(
                            field_id=data.get("field_id", 0),
                            entity_type=data.get("entity_type", ""),
                            department=data.get("department", ""),
                            field_code=data.get("field_code", "")
                        )
                        model.status = data.get("status", "NOT_TRAINED")
                        model.model_version = data.get("model_version")
                        model.train_time = data.get("train_time")
                        model.sample_count = data.get("sample_count", 0)
                        model.entity_dictionary = set(data.get("entity_dictionary", []))
                        model.patterns = data.get("patterns", [])
                        model.avg_entity_length = data.get("avg_entity_length", 0)
                        self.models[key] = model
                        logger.info(f"加载自定义NER模型: {data.get('field_code')} ({data.get('department')})")
                except Exception as e:
                    logger.warning(f"加载自定义模型失败 {filename}: {e}")

    def train_model(self, field_id: int, department: str, field_code: str,
                    entity_type: str, samples: List[Dict[str, Any]]) -> Dict[str, Any]:
        """训练自定义NER模型"""
        key = str(field_id)

        model = CustomNerModel(field_id, entity_type, department, field_code)
        success = model.train(samples)

        if not success:
            return {
                "success": False,
                "message": "训练失败，样本不足或无效",
                "field_id": field_id
            }

        self.models[key] = model
        self._save_model(model)

        logger.info(f"自定义NER模型训练完成: department={department}, field_code={field_code}, samples={len(samples)}")

        return {
            "success": True,
            "message": "训练成功",
            "field_id": field_id,
            "model_version": model.model_version,
            "sample_count": model.sample_count,
            "entity_count": len(model.entity_dictionary),
            "pattern_count": len(model.patterns)
        }

    def _save_model(self, model: CustomNerModel):
        """保存模型到文件"""
        filepath = os.path.join(self.model_dir, f"field_{model.field_id}.json")
        data = {
            "field_id": model.field_id,
            "entity_type": model.entity_type,
            "department": model.department,
            "field_code": model.field_code,
            "status": model.status,
            "model_version": model.model_version,
            "train_time": model.train_time,
            "sample_count": model.sample_count,
            "entity_dictionary": list(model.entity_dictionary),
            "patterns": model.patterns,
            "avg_entity_length": model.avg_entity_length
        }
        try:
            with open(filepath, "w", encoding="utf-8") as f:
                json.dump(data, f, ensure_ascii=False, indent=2)
        except Exception as e:
            logger.error(f"保存自定义模型失败: {e}")

    def get_model_status(self, field_id: int) -> Dict[str, Any]:
        """获取模型状态"""
        key = str(field_id)
        if key not in self.models:
            return {
                "success": True,
                "field_id": field_id,
                "status": "NOT_TRAINED",
                "message": "模型未训练"
            }

        model = self.models[key]
        return {
            "success": True,
            **model.to_dict()
        }

    def extract_entities(self, text: str, department: str = None,
                         entity_types: List[str] = None) -> List[Dict[str, Any]]:
        """使用自定义模型抽取实体"""
        results = []

        for key, model in self.models.items():
            if model.status != "TRAINED":
                continue
            if department and model.department != department:
                continue
            if entity_types and model.entity_type not in entity_types:
                continue

            try:
                entities = model.predict(text)
                results.extend(entities)
            except Exception as e:
                logger.error(f"自定义NER模型预测失败 field_id={key}: {e}")

        return results

    def get_fields_by_department(self, department: str) -> List[Dict[str, Any]]:
        """获取某科室的所有自定义字段模型信息"""
        result = []
        for key, model in self.models.items():
            if model.department == department:
                result.append(model.to_dict())
        return result
