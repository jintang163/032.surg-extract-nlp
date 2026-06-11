"""
多模态融合服务 - 融合OCR文本 + ASR语音文本 + 器械图像识别结果

融合策略:
  1. 文本级融合: OCR文本 + ASR文本 = 合并后的增强文本
  2. 实体级融合: 多来源实体对齐 + 置信度加权 + 冲突消解
  3. 器械辅助增强: 器械识别结果辅助推断手术名称、手术类型
  4. 来源优先级: MANUAL > RULE > REGEX > OCR_MODEL > ASR_MODEL > IMAGE_MODEL
"""

import re
from loguru import logger
from typing import List, Dict, Any, Optional, Tuple
from dataclasses import dataclass

from app.config import get_settings


@dataclass
class FusionResult:
    enhanced_text: str
    entities: List[Dict[str, Any]]
    instruments: List[Dict[str, Any]]
    fusion_stats: Dict[str, Any]
    source_breakdown: Dict[str, int]


SOURCE_PRIORITY = {
    "MANUAL": 100,
    "RULE": 90,
    "REGEX": 80,
    "MODEL": 60,
    "OCR_MODEL": 55,
    "ASR_MODEL": 50,
    "ASR_KEYWORD": 45,
    "IMAGE_MODEL": 40,
    "TEXT_INSTRUMENT": 35,
    "FUSION": 75,
}


class MultimodalFusionService:
    def __init__(self):
        self.settings = get_settings()
        logger.info("[多模态融合] 服务初始化完成")

    def fuse_texts(
        self,
        ocr_text: Optional[str] = None,
        asr_text: Optional[str] = None,
        asr_segments: Optional[List[Dict[str, Any]]] = None,
    ) -> str:
        """
        融合OCR文本和ASR语音转写文本，生成增强文本。

        策略:
        - 两者都有: OCR为主（结构化更强），ASR补充遗漏信息
        - 只有OCR: 直接使用
        - 只有ASR: 清洗后使用
        """
        ocr_text = ocr_text or ""
        asr_text = asr_text or ""

        if not ocr_text and not asr_text:
            return ""

        if not asr_text:
            return ocr_text.strip()

        if not ocr_text:
            return self._clean_asr_text(asr_text).strip()

        cleaned_asr = self._clean_asr_text(asr_text)

        ocr_lines = [l.strip() for l in ocr_text.split("\n") if l.strip()]
        asr_lines = [l.strip() for l in cleaned_asr.split("\n") if l.strip()]

        ocr_key_phrases = self._extract_key_phrases(ocr_text)
        asr_key_phrases = self._extract_key_phrases(cleaned_asr)

        missing_from_ocr = []
        for phrase in asr_key_phrases:
            if phrase and len(phrase) > 3 and phrase not in ocr_text:
                missing_from_ocr.append(phrase)

        enhanced_parts = [ocr_text.strip()]
        if missing_from_ocr:
            enhanced_parts.append("\n\n【语音补充信息】")
            enhanced_parts.extend(missing_from_ocr)

        return "\n".join(enhanced_parts)

    def _clean_asr_text(self, text: str) -> str:
        """清洗ASR转写文本"""
        if not text:
            return ""
        text = re.sub(r"[嗯啊呃哦呢吧啦]+[，。,.\s]*", " ", text)
        text = re.sub(r"\s+", " ", text)
        text = re.sub(r"[，。]{2,}", "。", text)
        return text.strip()

    def _extract_key_phrases(self, text: str) -> List[str]:
        """提取关键短语"""
        if not text:
            return []

        phrases = []

        sentence_pattern = r"[^。！？.!?\n]+[。！？.!?\n]"
        sentences = re.findall(sentence_pattern, text)
        if not sentences:
            sentences = text.split("\n")

        for sent in sentences:
            sent = sent.strip("。！？.!? \n")
            if len(sent) >= 5 and len(sent) <= 80:
                if any(kw in sent for kw in [
                    "手术", "麻醉", "出血", "输血", "切口", "器械",
                    "并发症", "术者", "主刀", "助手", "护士",
                    "胆囊", "阑尾", "胃", "肠", "子宫", "甲状腺",
                    "切除", "吻合", "修补", "置换", "探查",
                ]):
                    phrases.append(sent)

        return phrases

    def fuse_entities(
        self,
        ocr_entities: Optional[List[Dict[str, Any]]] = None,
        asr_entities: Optional[List[Dict[str, Any]]] = None,
        regex_entities: Optional[List[Dict[str, Any]]] = None,
        rule_entities: Optional[List[Dict[str, Any]]] = None,
        instrument_entities: Optional[List[Dict[str, Any]]] = None,
    ) -> Tuple[List[Dict[str, Any]], Dict[str, Any]]:
        """
        融合多来源实体。

        Returns:
            (final_entities, fusion_stats)
        """
        all_entities = []
        source_counts = {}

        entities_by_source = [
            ("OCR_MODEL", ocr_entities or []),
            ("ASR_MODEL", asr_entities or []),
            ("REGEX", regex_entities or []),
            ("RULE", rule_entities or []),
            ("IMAGE_MODEL", instrument_entities or []),
        ]

        for source, entities in entities_by_source:
            for ent in entities:
                ent = dict(ent)
                if "source" not in ent:
                    ent["source"] = source
                if "confidence" not in ent:
                    ent["confidence"] = 0.7
                all_entities.append(ent)
                source_counts[source] = source_counts.get(source, 0) + 1

        if not all_entities:
            return [], {"total": 0, "merged": 0, "sources": source_counts}

        merged = self._merge_entities(all_entities)

        stats = {
            "total_input": len(all_entities),
            "final_count": len(merged),
            "merged_count": len(all_entities) - len(merged),
            "sources": source_counts,
        }

        return merged, stats

    def _merge_entities(self, entities: List[Dict[str, Any]]) -> List[Dict[str, Any]]:
        """合并相同实体，加权置信度"""
        type_groups: Dict[str, List[Dict[str, Any]]] = {}
        for ent in entities:
            etype = ent.get("entity_type", "UNKNOWN")
            if etype not in type_groups:
                type_groups[etype] = []
            type_groups[etype].append(ent)

        final_entities = []

        for etype, group in type_groups.items():
            if etype == "SURGICAL_INSTRUMENT" or etype == "INTRAOP_FINDING":
                final_entities.extend(group)
                continue

            unique_ents = self._deduplicate_by_value(group)
            for ent in unique_ents:
                final_entities.append(self._boost_entity_confidence(ent))

        final_entities.sort(
            key=lambda x: (-SOURCE_PRIORITY.get(x.get("source", "MODEL"), 50), -x.get("confidence", 0))
        )

        return final_entities

    def _deduplicate_by_value(self, entities: List[Dict[str, Any]]) -> List[Dict[str, Any]]:
        """按实体值去重，保留来源优先级高且置信度高的"""
        value_map: Dict[str, Dict[str, Any]] = {}

        for ent in entities:
            value = (ent.get("entity_value") or "").strip()
            if not value:
                continue

            key = self._normalize_value(value)
            if key not in value_map:
                value_map[key] = dict(ent)
            else:
                existing = value_map[key]
                existing_src = existing.get("source", "MODEL")
                new_src = ent.get("source", "MODEL")
                existing_pri = SOURCE_PRIORITY.get(existing_src, 50)
                new_pri = SOURCE_PRIORITY.get(new_src, 50)

                if new_pri > existing_pri:
                    merged = dict(ent)
                    merged["confidence"] = self._weighted_confidence(
                        ent.get("confidence", 0.7),
                        existing.get("confidence", 0.5),
                        new_pri, existing_pri
                    )
                    value_map[key] = merged
                elif new_pri == existing_pri:
                    existing["confidence"] = max(
                        existing.get("confidence", 0),
                        ent.get("confidence", 0)
                    )
                    if "source" in existing and existing["source"] != new_src:
                        existing["source"] = "FUSION"
                else:
                    existing["confidence"] = self._weighted_confidence(
                        existing.get("confidence", 0.7),
                        ent.get("confidence", 0.5),
                        existing_pri, new_pri
                    )

        return list(value_map.values())

    def _weighted_confidence(
        self, primary_conf: float, secondary_conf: float,
        primary_pri: int, secondary_pri: int
    ) -> float:
        """加权融合置信度"""
        total_pri = primary_pri + secondary_pri
        weighted = (primary_conf * primary_pri + secondary_conf * secondary_pri) / total_pri
        boosted = min(0.99, weighted * 1.05)
        return round(boosted, 4)

    def _boost_entity_confidence(self, entity: Dict[str, Any]) -> Dict[str, Any]:
        """根据来源优先级调整置信度"""
        source = entity.get("source", "MODEL")
        base_conf = entity.get("confidence", 0.7)
        priority = SOURCE_PRIORITY.get(source, 50)

        factor = 0.7 + (priority / 100) * 0.3
        boosted = min(0.99, base_conf * factor)

        result = dict(entity)
        result["confidence"] = round(boosted, 4)
        return result

    @staticmethod
    def _normalize_value(value: str) -> str:
        value = value.strip().lower()
        value = re.sub(r"[，。、,.\s]+", "", value)
        value = re.sub(r"[（）()【】\[\]]+", "", value)
        return value

    def enhance_with_instruments(
        self,
        entities: List[Dict[str, Any]],
        instruments: List[Dict[str, Any]],
        text: str = "",
    ) -> List[Dict[str, Any]]:
        """
        使用器械识别结果增强实体抽取。

        - 器械直接作为 SURGICAL_INSTRUMENT 实体
        - 器械组合辅助推断手术名称
        - 补充手术类型信息
        """
        enhanced = [dict(e) for e in entities]

        for instr in instruments:
            name = instr.get("name", "")
            conf = instr.get("confidence", 0.5)
            category = instr.get("category", "")

            instrument_entity = {
                "entity_type": "SURGICAL_INSTRUMENT",
                "entity_value": name,
                "entity_unit": None,
                "confidence": round(conf * 0.9, 4),
                "source": "IMAGE_MODEL",
                "category": category,
                "count": instr.get("count", 1),
                "original_text": name,
                "start_pos": None,
                "end_pos": None,
            }
            enhanced.append(instrument_entity)

        surgery_name_entity = self._infer_surgery_from_instruments(instruments, text)
        if surgery_name_entity:
            enhanced.append(surgery_name_entity)

        return enhanced

    def _infer_surgery_from_instruments(
        self,
        instruments: List[Dict[str, Any]],
        text: str,
    ) -> Optional[Dict[str, Any]]:
        """根据器械组合推断手术名称"""
        if not instruments:
            return None

        instr_names = [i.get("name", "") for i in instruments]
        instr_names_lower = [n.lower() for n in instr_names]

        surgery_rules = [
            (["腹腔镜", "穿刺器", "抓钳", "分离钳"], "腹腔镜手术", 0.65),
            (["胆囊", "腹腔镜", "抓钳"], "腹腔镜胆囊切除术", 0.7),
            (["电刀", "止血钳", "手术刀", "缝合针"], "开放性手术", 0.55),
            (["吻合器", "切割吻合器", "吻合钉"], "消化道吻合术", 0.6),
            (["骨刀", "骨凿", "骨锤", "钢板", "螺钉"], "骨科内固定术", 0.65),
            (["咬骨钳", "髓内钉"], "骨科手术", 0.6),
            (["超声刀", "分离钳", "抓钳"], "微创手术", 0.6),
            (["持针器", "缝合针", "缝合线", "手术剪"], "缝合手术", 0.5),
        ]

        best_match = None
        best_score = 0.0

        for rule_instrs, surgery_name, base_conf in surgery_rules:
            matched = sum(1 for r in rule_instrs if any(r in n for n in instr_names_lower))
            if matched >= 2:
                score = matched / len(rule_instrs)
                if score > best_score:
                    best_score = score
                    best_match = (surgery_name, base_conf * score)

        if not best_match:
            return None

        surgery_name, conf = best_match

        if text and surgery_name in text:
            conf = min(0.95, conf + 0.1)

        if conf < 0.4:
            return None

        return {
            "entity_type": "SURGERY_NAME",
            "entity_value": surgery_name,
            "entity_unit": None,
            "confidence": round(conf, 4),
            "source": "IMAGE_INFERENCE",
            "original_text": surgery_name,
            "start_pos": None,
            "end_pos": None,
            "inferred_from": [i.get("name", "") for i in instruments[:5]],
        }

    def full_fusion_pipeline(
        self,
        ocr_text: Optional[str] = None,
        asr_result: Optional[Dict[str, Any]] = None,
        instrument_result: Optional[Dict[str, Any]] = None,
        ner_entities: Optional[List[Dict[str, Any]]] = None,
        regex_entities: Optional[List[Dict[str, Any]]] = None,
        rule_entities: Optional[List[Dict[str, Any]]] = None,
        rerun_ner: bool = True,
    ) -> FusionResult:
        """
        完整多模态融合流水线。

        1. 文本融合: OCR + ASR -> enhanced_text
        2. 重跑NER: 用增强文本重新抽取实体（可选）
        3. 实体融合: 重跑NER实体 + 正则实体 + 规则实体 + 器械实体
        4. 器械增强: 器械识别结果补充实体，辅助推断

        放宽融合条件：
        - 只要有OCR或ASR其中之一，即可进行文本融合增强
        - 只要有器械识别结果，即可进行器械辅助增强
        - 三者有其一即可执行融合流程
        """
        asr_text = None
        asr_keywords = []
        source_breakdown = {}

        if asr_result and asr_result.get("success"):
            asr_text = asr_result.get("full_text", "")
            if asr_text:
                from app.services.asr_service import AsrService
                asr_svc = AsrService()
                asr_keywords = asr_svc.extract_medical_keywords(asr_text)
                source_breakdown["ASR_KEYWORD"] = len(asr_keywords)

        enhanced_text = self.fuse_texts(ocr_text, asr_text)
        text_enhanced = bool((ocr_text and asr_text) or (enhanced_text and enhanced_text != (ocr_text or "")))

        instrument_entities = []
        instruments_list = []
        if instrument_result and instrument_result.get("success"):
            instruments_list = instrument_result.get("instruments", [])
            from app.services.instrument_recognition_service import InstrumentRecognitionService
            instr_svc = InstrumentRecognitionService()
            instrument_entities = instr_svc.extract_instruments_from_text(
                " ".join(i.get("name", "") for i in instruments_list)
            )
            source_breakdown["IMAGE_INSTRUMENT"] = len(instrument_entities)

        reran_ner = False
        final_ner_entities = ner_entities or []
        if rerun_ner and enhanced_text and len(enhanced_text.strip()) > 0:
            try:
                from app.services.ner_extract_service import NerExtractService
                ner_svc = NerExtractService()
                rerun_result = ner_svc.extract_entities(enhanced_text)
                if rerun_result and rerun_result.get("success"):
                    reran_entities = rerun_result.get("entities", [])
                    if reran_entities:
                        for ent in reran_entities:
                            if "source" not in ent:
                                ent["source"] = "FUSION_MODEL"
                        final_ner_entities = reran_entities
                        reran_ner = True
                        source_breakdown["ENHANCED_NER"] = len(reran_entities)
                        logger.info(f"[多模态融合] 增强文本重跑NER完成，实体数: {len(reran_entities)}")
            except Exception as e:
                logger.warning(f"[多模态融合] 增强文本重跑NER失败，将使用原始实体: {e}")
                if ner_entities:
                    source_breakdown["ORIGINAL_NER"] = len(ner_entities)
        else:
            if ner_entities:
                source_breakdown["ORIGINAL_NER"] = len(ner_entities)

        all_entities = []
        all_entities.extend(final_ner_entities)
        all_entities.extend(asr_keywords)
        all_entities.extend(regex_entities or [])
        all_entities.extend(rule_entities or [])
        all_entities.extend(instrument_entities)

        source_breakdown["REGEX"] = len(regex_entities or [])
        source_breakdown["RULE"] = len(rule_entities or [])

        merged_entities, stats = self.fuse_entities(
            ocr_entities=final_ner_entities,
            asr_entities=asr_keywords,
            regex_entities=regex_entities,
            rule_entities=rule_entities,
            instrument_entities=instrument_entities,
        )

        final_entities = self.enhance_with_instruments(
            merged_entities, instruments_list, enhanced_text
        )

        final_entities.sort(
            key=lambda x: -x.get("confidence", 0)
        )

        fusion_stats = {
            "text_enhanced": text_enhanced,
            "reran_ner": reran_ner,
            "ocr_text_length": len(ocr_text or ""),
            "asr_text_length": len(asr_text or ""),
            "enhanced_text_length": len(enhanced_text or ""),
            "ocr_entity_count": len(ner_entities or []),
            "asr_entity_count": len(asr_keywords),
            "fused_entity_count": len(final_entities),
            "instrument_count": len(instruments_list),
            "entity_stats": stats,
            "sources_used": list(source_breakdown.keys()),
        }

        return FusionResult(
            enhanced_text=enhanced_text,
            entities=final_entities,
            instruments=instruments_list,
            fusion_stats=fusion_stats,
            source_breakdown=source_breakdown,
        )
