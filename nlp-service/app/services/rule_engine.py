import re
import os
import json
from loguru import logger
from typing import List, Dict, Any, Optional
from dataclasses import dataclass, field


@dataclass
class RuleConfig:
    rule_id: str
    rule_name: str
    entity_type: str
    conditions: List[Dict[str, Any]] = field(default_factory=list)
    actions: List[Dict[str, Any]] = field(default_factory=list)
    priority: int = 0
    enabled: bool = True


class RuleEngine:
    def __init__(self, rules_file: str = "./config/rules.json"):
        self.rules: List[RuleConfig] = []
        self._load_rules(rules_file)
        self._init_builtin_rules()

    def _load_rules(self, rules_file: str):
        if not os.path.exists(rules_file):
            return
        try:
            with open(rules_file, "r", encoding="utf-8") as f:
                data = json.load(f)
            for r in data:
                if r.get("enabled", True):
                    self.rules.append(RuleConfig(
                        rule_id=r.get("id", ""),
                        rule_name=r.get("name", ""),
                        entity_type=r["entity_type"],
                        conditions=r.get("conditions", []),
                        actions=r.get("actions", []),
                        priority=r.get("priority", 0),
                        enabled=True
                    ))
            logger.info(f"加载自定义规则: {len(data)}条")
        except Exception as e:
            logger.warning(f"加载规则文件失败: {e}")

    def _init_builtin_rules(self):
        builtin = [
            RuleConfig(
                rule_id="blood_check_1",
                rule_name="出血量合理性校验",
                entity_type="BLOOD_LOSS",
                conditions=[{"type": "range", "field": "entity_value", "min": 0, "max": 10000}],
                actions=[{"type": "confidence_adjust", "value": 0.95}],
                priority=100
            ),
            RuleConfig(
                rule_id="transfusion_check_1",
                rule_name="输血量合理性校验",
                entity_type="BLOOD_TRANSFUSION",
                conditions=[{"type": "range", "field": "entity_value", "min": 0, "max": 10000}],
                actions=[{"type": "confidence_adjust", "value": 0.95}],
                priority=100
            ),
            RuleConfig(
                rule_id="surgery_name_combine",
                rule_name="手术名称补全规则",
                entity_type="SURGERY_NAME",
                conditions=[{"type": "suffix_contains", "field": "entity_value", "values": ["切除术", "吻合术", "修补术", "成形术", "置换术", "切开术", "结扎术", "活检术", "造瘘术", "探查术", "植骨术", "内固定术"]}],
                actions=[{"type": "confidence_adjust", "value": 0.98}],
                priority=90
            ),
            RuleConfig(
                rule_id="complication_none",
                rule_name="无并发症处理",
                entity_type="COMPLICATION",
                conditions=[{"type": "exact_match", "field": "entity_value", "values": ["无", "未见", "未发生", "无特殊", "顺利"]}],
                actions=[{"type": "set_value", "value": "无"}],
                priority=95
            ),
            RuleConfig(
                rule_id="incision_normalize",
                rule_name="切口等级规范化",
                entity_type="INCISION_LEVEL",
                conditions=[{"type": "in", "field": "entity_value", "values": ["Ⅰ", "Ⅱ", "Ⅲ"]}],
                actions=[{"type": "confidence_adjust", "value": 1.0}],
                priority=100
            ),
            RuleConfig(
                rule_id="anesthesia_standard",
                rule_name="麻醉方式标准化",
                entity_type="ANESTHESIA_TYPE",
                conditions=[{"type": "contains_any", "field": "entity_value", "values": ["全麻", "全身麻醉"]}],
                actions=[{"type": "replace_value", "pattern": r"(?:静吸复合|气管插管|静脉|吸入)?\s*全身?麻醉", "replacement": "全身麻醉"}],
                priority=85
            ),
        ]
        self.rules.extend(builtin)
        self.rules.sort(key=lambda x: -x.priority)
        logger.info(f"规则引擎初始化完成: {len(self.rules)}条规则")

    def apply_rules(self, entities: List[Dict[str, Any]], text: str = "") -> List[Dict[str, Any]]:
        if not entities:
            return entities

        result = []
        entity_index: Dict[str, Dict[str, Any]] = {}
        for entity in entities:
            key = f"{entity['entity_type']}_{entity.get('start_pos', 0)}_{entity.get('end_pos', 0)}"
            entity_index[key] = entity

        for entity in entities:
            processed_entity = dict(entity)
            matched_rules = [r for r in self.rules if r.entity_type == processed_entity["entity_type"]]

            for rule in matched_rules:
                if not self._check_conditions(rule.conditions, processed_entity, text):
                    continue
                self._apply_actions(rule.actions, processed_entity)

            result.append(processed_entity)

        result = self._merge_duplicate_entities(result)
        result = self._apply_deduction_rules(result, text)

        logger.info(f"规则引擎处理完成: 输入{len(entities)}个, 输出{len(result)}个实体")
        return result

    def _check_conditions(self, conditions: List[Dict[str, Any]], entity: Dict[str, Any], text: str) -> bool:
        for cond in conditions:
            cond_type = cond.get("type", "")
            field = entity.get(cond.get("field", "entity_value"), "") if cond.get("field") != "text" else text
            field_value = str(field) if field is not None else ""

            if cond_type == "exact_match":
                if field_value not in cond.get("values", []):
                    return False
            elif cond_type == "contains":
                if cond.get("value", "") not in field_value:
                    return False
            elif cond_type == "contains_any":
                values = cond.get("values", [])
                if not any(v in field_value for v in values):
                    return False
            elif cond_type == "in":
                if field_value not in cond.get("values", []):
                    return False
            elif cond_type == "range":
                try:
                    num = float(field_value)
                    min_v = cond.get("min", float("-inf"))
                    max_v = cond.get("max", float("inf"))
                    if num < min_v or num > max_v:
                        return False
                except (ValueError, TypeError):
                    return False
            elif cond_type == "regex_match":
                pattern = cond.get("pattern", "")
                if not re.search(pattern, field_value):
                    return False
            elif cond_type == "suffix_contains":
                values = cond.get("values", [])
                if not any(field_value.endswith(v) for v in values):
                    return False

        return True

    def _apply_actions(self, actions: List[Dict[str, Any]], entity: Dict[str, Any]):
        for action in actions:
            action_type = action.get("type", "")

            if action_type == "confidence_adjust":
                current = entity.get("confidence", 0.0)
                target = action.get("value", 0.0)
                entity["confidence"] = round(max(current, target), 4)
            elif action_type == "set_value":
                entity["entity_value"] = action.get("value", entity["entity_value"])
                entity["source"] = "RULE"
            elif action_type == "replace_value":
                pattern = action.get("pattern", "")
                replacement = action.get("replacement", "")
                try:
                    new_val = re.sub(pattern, replacement, entity["entity_value"])
                    if new_val != entity["entity_value"]:
                        entity["entity_value"] = new_val
                        entity["source"] = "RULE"
                except re.error:
                    pass
            elif action_type == "set_unit":
                entity["entity_unit"] = action.get("value", entity.get("entity_unit"))
            elif action_type == "normalize_number":
                try:
                    num = float(re.sub(r"[^\d.]", "", entity["entity_value"]))
                    entity["entity_value"] = str(int(num)) if num.is_integer() else str(num)
                    entity["source"] = "RULE"
                except (ValueError, TypeError):
                    pass

    def _merge_duplicate_entities(self, entities: List[Dict[str, Any]]) -> List[Dict[str, Any]]:
        merged: Dict[str, Dict[str, Any]] = {}
        for entity in entities:
            key = f"{entity['entity_type']}|{entity['entity_value']}"
            if key in merged:
                existing = merged[key]
                existing["confidence"] = round(max(
                    existing.get("confidence", 0.0),
                    entity.get("confidence", 0.0)
                ), 4)
                if entity.get("source") == "MANUAL":
                    existing["source"] = "MANUAL"
                elif entity.get("source") == "RULE" and existing.get("source") != "MANUAL":
                    existing["source"] = "RULE"
            else:
                merged[key] = dict(entity)

        return list(merged.values())

    def _apply_deduction_rules(self, entities: List[Dict[str, Any]], text: str) -> List[Dict[str, Any]]:
        entity_types = {e["entity_type"] for e in entities}

        if "COMPLICATION" not in entity_types:
            if re.search(r"(手术顺利|无并发症|过程顺利|未发生并发症|无意外|无特殊情况)", text):
                entities.append({
                    "entity_type": "COMPLICATION",
                    "entity_value": "无",
                    "entity_unit": None,
                    "confidence": 0.90,
                    "source": "RULE",
                    "start_pos": None,
                    "end_pos": None,
                    "original_text": "推断:无"
                })

        if "BLOOD_TRANSFUSION" not in entity_types and "BLOOD_LOSS" in entity_types:
            for e in entities:
                if e["entity_type"] == "BLOOD_LOSS":
                    try:
                        loss = float(e["entity_value"])
                        if loss < 400:
                            entities.append({
                                "entity_type": "BLOOD_TRANSFUSION",
                                "entity_value": "0",
                                "entity_unit": "ml",
                                "confidence": 0.75,
                                "source": "RULE",
                                "start_pos": None,
                                "end_pos": None,
                                "original_text": "推断:少量出血未输血"
                            })
                    except (ValueError, TypeError):
                        pass
                    break

        if "INCISION_HEALING" not in entity_types:
            if re.search(r"(切口.*[良正]好|甲级?愈合|愈合良好)", text):
                entities.append({
                    "entity_type": "INCISION_HEALING",
                    "entity_value": "甲",
                    "entity_unit": None,
                    "confidence": 0.80,
                    "source": "RULE",
                    "start_pos": None,
                    "end_pos": None,
                    "original_text": "推断:甲级愈合"
                })

        return entities
