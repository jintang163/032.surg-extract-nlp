import re
import os
import json
from loguru import logger
from typing import List, Optional, Dict, Any, Tuple
from dataclasses import dataclass

from app.config import get_settings


@dataclass
class RegexRule:
    rule_name: str
    entity_type: str
    pattern: str
    priority: int = 0
    unit: Optional[str] = None
    group_index: int = 1


class RegexExtractor:
    def __init__(self):
        self.settings = get_settings()
        self.rules: List[RegexRule] = []
        self._load_rules()
        self._load_dict_rules()

    def _load_rules(self):
        default_rules = [
            RegexRule("姓名-1", "PATIENT_NAME", r"姓\s*名\s*[:：]\s*([\u4e00-\u9fa5]{2,6})", 100),
            RegexRule("患者姓名-2", "PATIENT_NAME", r"患者[:：\s]*([\u4e00-\u9fa5]{2,6})", 95),
            RegexRule("住院号-1", "HOSPITAL_NO", r"住院号\s*[:：]\s*([A-Za-z0-9\-]{3,32})", 100),
            RegexRule("住院号-2", "HOSPITAL_NO", r"[住入]院号\s*[:：#号]*\s*([A-Za-z0-9\-]{3,32})", 90),
            RegexRule("性别-1", "GENDER", r"性\s*别\s*[:：]\s*(男|女)", 100),
            RegexRule("性别-2", "GENDER", r"患者(男|女)", 90),
            RegexRule("年龄-1", "AGE", r"年\s*龄\s*[:：]\s*(\d{1,3})\s*岁", 100, unit="岁"),
            RegexRule("年龄-2", "AGE", r"(\d{1,3})\s*岁", 80, unit="岁"),
            RegexRule("科室-1", "DEPARTMENT", r"科\s*室\s*[:：]\s*([\u4e00-\u9fa5]{2,32}科?)", 100),
            RegexRule("床号-1", "BED_NO", r"床\s*号\s*[:：]\s*([A-Za-z0-9\-]{2,16})", 100),
            RegexRule("入院日期-1", "ADMISSION_DATE", r"[入进]院日期?\s*[:：]\s*(\d{4}[-\/年.]\d{1,2}[-\/月.]\d{1,2}日?)", 100),
            RegexRule("手术日期-1", "SURGERY_DATE", r"手术日期?\s*[:：]\s*(\d{4}[-\/年.]\d{1,2}[-\/月.]\d{1,2}日?\s*\d{0,2}[:：时]?\d{0,2}分?)", 100),
            RegexRule("手术日期-2", "SURGERY_DATE", r"手术时间\s*[:：]\s*(\d{4}[-\/年.]\d{1,2}[-\/月.]\d{1,2}日?\s*\d{0,2}[:：时]?\d{0,2}分?)", 95),
            RegexRule("手术名称-1", "SURGERY_NAME", r"手术名称\s*[:：]\s*([\u4e00-\u9fa5A-Za-z0-9\-\+\(\)（）\s]{2,128})", 100),
            RegexRule("手术名称-2", "SURGERY_NAME", r"拟行手术[:：]\s*([\u4e00-\u9fa5A-Za-z0-9\-\+\(\)（）\s]{2,128})", 90),
            RegexRule("手术名称-3", "SURGERY_NAME", r"在全麻下行\s*([\u4e00-\u9fa5A-Za-z0-9\-\+\(\)（）\s]{2,128}?)(?:术|治疗)", 85),
            RegexRule("手术等级-1", "SURGERY_LEVEL", r"手术等级\s*[:：]\s*(一|二|三|四|1|2|3|4)级", 100),
            RegexRule("切口等级-1", "INCISION_LEVEL", r"切口[等級]级\s*[:：]?\s*([ⅠⅡⅢ一二III123])\s*[类級级]?", 100),
            RegexRule("切口等级-2", "INCISION_LEVEL", r"切口分类\s*[:：]?\s*([ⅠⅡⅢ一二III123])\s*[类級级]?", 90),
            RegexRule("切口分类-3", "INCISION_HEALING", r"(?:切口愈合|愈合等级)\s*[:：]?\s*([甲乙丙ABC])", 100),
            RegexRule("麻醉方式-1", "ANESTHESIA_TYPE", r"麻醉方式\s*[:：]\s*([\u4e00-\u9fa5A-Za-z0-9\-\+\(\)（）\s]{2,64})", 100),
            RegexRule("麻醉方式-2", "ANESTHESIA_TYPE", r"麻醉方法\s*[:：]\s*([\u4e00-\u9fa5A-Za-z0-9\-\+\(\)（）\s]{2,64})", 95),
            RegexRule("麻醉方式-3", "ANESTHESIA_TYPE", r"(?:行|采用|予以)([全硬腰静]麻|全麻|硬膜外麻醉|腰麻|腰硬联合麻醉|局部麻醉|颈丛麻醉|臂丛麻醉|静脉麻醉|吸入麻醉)", 85),
            RegexRule("失血量-1", "BLOOD_LOSS", r"(?:失血量|出血量)\s*[:：约]?\s*(\d+(?:\.\d+)?)\s*ml", 100, unit="ml"),
            RegexRule("失血量-2", "BLOOD_LOSS", r"术中出血\s*[:：约]?\s*(\d+(?:\.\d+)?)\s*ml", 90, unit="ml"),
            RegexRule("失血量-3", "BLOOD_LOSS", r"出血约\s*(\d+(?:\.\d+)?)\s*(?:ml|毫升)", 80, unit="ml"),
            RegexRule("输血量-1", "BLOOD_TRANSFUSION", r"输血量\s*[:：约]?\s*(\d+(?:\.\d+)?)\s*ml", 100, unit="ml"),
            RegexRule("输血量-2", "BLOOD_TRANSFUSION", r"输血\s*[:：约]?\s*(\d+(?:\.\d+)?)\s*(?:ml|毫升)", 90, unit="ml"),
            RegexRule("输液量-1", "FLUID_INFUSION", r"输液量\s*[:：约]?\s*(\d+(?:\.\d+)?)\s*ml", 100, unit="ml"),
            RegexRule("输液量-2", "FLUID_INFUSION", r"(?:补液|输液)\s*[:：约]?\s*(\d+(?:\.\d+)?)\s*(?:ml|毫升)", 90, unit="ml"),
            RegexRule("尿量-1", "URINE_OUTPUT", r"尿量\s*[:：约]?\s*(\d+(?:\.\d+)?)\s*(?:ml|毫升)", 100, unit="ml"),
            RegexRule("手术医生-1", "SURGEON", r"(?:术者|主刀|主刀医师|手术者)\s*[:：]\s*([\u4e00-\u9fa5]{2,6})", 100),
            RegexRule("手术医生-2", "SURGEON", r"术者[为是]\s*([\u4e00-\u9fa5]{2,6})", 90),
            RegexRule("助手-1", "ASSISTANT", r"(?:助手|手术助手)\s*[:：]\s*([\u4e00-\u9fa5\s、,，]{2,64})", 100),
            RegexRule("助手-2", "ASSISTANT", r"第一助手\s*[:：]\s*([\u4e00-\u9fa5\s、,，]{2,32})", 95),
            RegexRule("麻醉医生-1", "ANESTHESIOLOGIST", r"(?:麻醉医师|麻醉医生|麻醉师)\s*[:：]\s*([\u4e00-\u9fa5]{2,6})", 100),
            RegexRule("器械护士-1", "SCRUB_NURSE", r"(?:器械护士|洗手护士)\s*[:：]\s*([\u4e00-\u9fa5]{2,6})", 100),
            RegexRule("巡回护士-1", "CIRCULATING_NURSE", r"巡回护士\s*[:：]\s*([\u4e00-\u9fa5]{2,6})", 100),
            RegexRule("术前诊断-1", "PREOP_DIAGNOSIS", r"(?:术前诊断|入院诊断)\s*[:：]\s*([\u4e00-\u9fa5A-Za-z0-9，,、；;\s\.]{2,256})", 100),
            RegexRule("术后诊断-1", "POSTOP_DIAGNOSIS", r"(?:术后诊断|出院诊断)\s*[:：]\s*([\u4e00-\u9fa5A-Za-z0-9，,、；;\s\.]{2,256})", 100),
            RegexRule("并发症-1", "COMPLICATION", r"(?:并[发併]症|术中意外|术中情况)\s*[:：]\s*([\u4e00-\u9fa5，,。；;、\s\.（）\(\)A-Za-z0-9]{2,256})", 100),
            RegexRule("并发症-2", "COMPLICATION", r"术[中后]出现\s*([^。\n]{2,128})", 80),
        ]
        self.rules.extend(default_rules)

        rules_file = self.settings.regex_rules_file
        if os.path.exists(rules_file):
            try:
                with open(rules_file, "r", encoding="utf-8") as f:
                    custom_rules = json.load(f)
                    for r in custom_rules:
                        self.rules.append(RegexRule(
                            rule_name=r.get("name", ""),
                            entity_type=r["entity_type"],
                            pattern=r["pattern"],
                            priority=r.get("priority", 50),
                            unit=r.get("unit"),
                            group_index=r.get("group_index", 1)
                        ))
                logger.info(f"加载自定义正则规则: {len(custom_rules)}条")
            except Exception as e:
                logger.warning(f"加载自定义规则失败: {e}")

        self.rules.sort(key=lambda x: -x.priority)
        logger.info(f"共加载正则规则: {len(self.rules)}条")

    def _load_dict_rules(self):
        dict_dir = self.settings.dict_dir
        if not os.path.exists(dict_dir):
            return
        for filename in os.listdir(dict_dir):
            if not filename.endswith(".txt"):
                continue
            entity_type = filename.replace(".txt", "").upper()
            filepath = os.path.join(dict_dir, filename)
            try:
                with open(filepath, "r", encoding="utf-8") as f:
                    words = [line.strip() for line in f if line.strip()]
                if words:
                    pattern = "|".join(re.escape(w) for w in sorted(words, key=len, reverse=True))
                    self.rules.append(RegexRule(
                        rule_name=f"字典-{entity_type}",
                        entity_type=entity_type,
                        pattern=f"({pattern})",
                        priority=70
                    ))
                    logger.info(f"加载字典 {entity_type}: {len(words)}个词")
            except Exception as e:
                logger.warning(f"加载字典失败 {filename}: {e}")

    def extract(self, text: str) -> List[Dict[str, Any]]:
        if not text:
            return []

        entities = []
        seen = set()

        for rule in self.rules:
            try:
                matches = list(re.finditer(rule.pattern, text))
                for match in matches:
                    try:
                        value = match.group(rule.group_index).strip()
                        if not value or len(value) > 256:
                            continue
                        if len(value) < 2 and rule.entity_type not in ("GENDER", "AGE", "INCISION_LEVEL", "INCISION_HEALING"):
                            continue

                        start, end = match.span(rule.group_index)
                        key = (rule.entity_type, value, start, end)
                        if key in seen:
                            continue
                        seen.add(key)

                        confidence = min(0.95, 0.6 + rule.priority * 0.0035)

                        entity = {
                            "entity_type": rule.entity_type,
                            "entity_value": self._normalize_value(rule.entity_type, value),
                            "entity_unit": rule.unit,
                            "confidence": round(confidence, 4),
                            "source": "REGEX",
                            "start_pos": start,
                            "end_pos": end,
                            "original_text": value
                        }
                        entities.append(entity)
                    except (IndexError, AttributeError):
                        continue
            except re.error as e:
                logger.warning(f"正则匹配失败 rule={rule.rule_name}: {e}")

        logger.info(f"正则抽取完成: {len(entities)}个实体")
        return entities

    def _normalize_value(self, entity_type: str, value: str) -> str:
        value = value.strip()

        if entity_type == "INCISION_LEVEL":
            mapping = {"一": "Ⅰ", "二": "Ⅱ", "三": "Ⅲ", "1": "Ⅰ", "2": "Ⅱ", "3": "Ⅲ",
                       "I": "Ⅰ", "II": "Ⅱ", "III": "Ⅲ"}
            value = mapping.get(value, value)
        elif entity_type == "INCISION_HEALING":
            mapping = {"A": "甲", "B": "乙", "C": "丙"}
            value = mapping.get(value.upper(), value)
        elif entity_type == "SURGERY_LEVEL":
            mapping = {"1": "一级", "2": "二级", "3": "三级", "4": "四级",
                       "一": "一级", "二": "二级", "三": "三级", "四": "四级"}
            value = mapping.get(value, value)
        elif entity_type in ("BLOOD_LOSS", "BLOOD_TRANSFUSION", "FLUID_INFUSION", "AGE", "URINE_OUTPUT"):
            value = re.sub(r"[^\d.]", "", value)
        elif entity_type in ("SURGERY_DATE", "ADMISSION_DATE"):
            value = value.replace("年", "-").replace("月", "-").replace("日", "").replace("时", ":").replace("分", "")
        elif entity_type in ("ASSISTANT", "COMPLICATION"):
            value = re.sub(r"\s+", "", value)

        return value.strip()
