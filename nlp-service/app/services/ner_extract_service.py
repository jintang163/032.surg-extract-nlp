import re
import time
import jieba
from loguru import logger
from typing import List, Dict, Any

from app.config import get_settings
from app.services.ocr_service import OcrService
from app.services.regex_extractor import RegexExtractor
from app.services.ner_model_service import NerModelService
from app.services.rule_engine import RuleEngine


class TextPreprocessor:
    def __init__(self):
        self.settings = get_settings()
        self._init_jieba()

    def _init_jieba(self):
        dict_dir = self.settings.dict_dir
        import os
        if os.path.exists(dict_dir):
            for filename in os.listdir(dict_dir):
                if filename.endswith(".txt"):
                    dict_path = os.path.join(dict_dir, filename)
                    try:
                        jieba.load_userdict(dict_path)
                        logger.info(f"加载用户词典: {filename}")
                    except Exception as e:
                        logger.warning(f"加载用户词典失败 {filename}: {e}")

    def split_sentences(self, text: str) -> List[str]:
        if not text:
            return []
        text = re.sub(r"\r\n", "\n", text)
        sentences = re.split(r"(?<=[。！？；;!?\n])", text)
        sentences = [s.strip() for s in sentences if s.strip()]
        return sentences

    def tokenize(self, text: str) -> List[str]:
        if not text:
            return []
        stopwords = self._get_stopwords()
        words = jieba.lcut(text)
        words = [w.strip() for w in words if w.strip() and w.strip() not in stopwords]
        return words

    def _get_stopwords(self) -> set:
        defaults = {
            "的", "了", "在", "是", "我", "有", "和", "就", "不", "人", "都", "一", "一个",
            "上", "也", "很", "到", "说", "要", "去", "你", "会", "着", "没有", "看", "好",
            "自己", "这", "那", "他", "她", "它", "们", "什么", "怎么", "这个", "那个",
            "可以", "可能", "应该", "但是", "然后", "因为", "所以", "如果", "虽然",
            "以及", "等等", "一些", "还有", "进行", "予以", "给予", "未见", "无明显",
            "示", "见", "行", "为", "对", "将", "于", "从", "被", "把", "让", "使"
        }
        return defaults

    def clean_text(self, text: str) -> str:
        if not text:
            return ""

        text = text.replace("\u3000", " ")
        text = re.sub(r"\x00-\x1f\x7f-\x9f", "", text)
        text = re.sub(r"[\x0b\x0c]", "\n", text)

        text = self._normalize_fullwidth(text)
        text = re.sub(r"[ \t]+", " ", text)
        text = re.sub(r"\n{3,}", "\n\n", text)

        return text.strip()

    def _normalize_fullwidth(self, text: str) -> str:
        result = []
        for char in text:
            code = ord(char)
            if 0xFF01 <= code <= 0xFF5E:
                code -= 0xFEE0
                result.append(chr(code))
            elif code == 0x3000:
                result.append(" ")
            else:
                result.append(char)
        return "".join(result)


class NerExtractService:
    def __init__(self):
        self.settings = get_settings()
        self.ocr_service = OcrService()
        self.preprocessor = TextPreprocessor()
        self.regex_extractor = RegexExtractor()
        self.model_service = NerModelService()
        self.rule_engine = RuleEngine()
        logger.info("NLP抽取服务初始化完成")

    def process_ocr(self, file_content: bytes, filename: str, file_type: str = None) -> Dict[str, Any]:
        start_time = time.time()

        try:
            raw_text, processed_text = self.ocr_service.process_file(
                file_content, filename, file_type
            )
            processed_text = self.preprocessor.clean_text(processed_text)

            elapsed = int((time.time() - start_time) * 1000)
            return {
                "success": True,
                "ocr_text": raw_text,
                "processed_text": processed_text,
                "error_message": None,
                "processing_time_ms": elapsed
            }
        except Exception as e:
            logger.error(f"OCR处理失败: {e}", exc_info=True)
            error_msg = self._format_ocr_error(e)
            return {
                "success": False,
                "ocr_text": None,
                "processed_text": None,
                "error_message": error_msg,
                "processing_time_ms": int((time.time() - start_time) * 1000)
            }

    def process_ocr_by_path(self, file_path: str, file_type: str = None) -> Dict[str, Any]:
        start_time = time.time()

        try:
            raw_text, processed_text = self.ocr_service.process_file_by_path(
                file_path, file_type
            )
            processed_text = self.preprocessor.clean_text(processed_text)

            elapsed = int((time.time() - start_time) * 1000)
            return {
                "success": True,
                "ocr_text": raw_text,
                "processed_text": processed_text,
                "error_message": None,
                "processing_time_ms": elapsed
            }
        except Exception as e:
            logger.error(f"OCR按路径处理失败: {e}", exc_info=True)
            error_msg = self._format_ocr_error(e)
            return {
                "success": False,
                "ocr_text": None,
                "processed_text": None,
                "error_message": error_msg,
                "processing_time_ms": int((time.time() - start_time) * 1000)
            }

    def _format_ocr_error(self, e: Exception) -> str:
        try:
            from app.services.ocr_service import OcrProcessingError, OcrDependencyError
            if isinstance(e, OcrDependencyError):
                msg = f"[OCR依赖缺失] {str(e)}"
                if hasattr(e, 'detail') and e.detail:
                    msg = f"{msg}。详情: {e.detail}"
                return msg
            if isinstance(e, OcrProcessingError):
                stage = getattr(e, 'stage', 'unknown')
                msg = f"[OCR失败-{stage}] {str(e)}"
                if hasattr(e, 'detail') and e.detail:
                    msg = f"{msg}。详情: {e.detail}"
                return msg
        except Exception:
            pass
        return f"OCR处理失败: {str(e)}"

    def extract_entities(self, text: str, record_id: int = None) -> Dict[str, Any]:
        start_time = time.time()

        if not text or not text.strip():
            return {
                "success": False,
                "entities": [],
                "error_message": "输入文本为空",
                "processing_time_ms": 0
            }

        try:
            text = self.preprocessor.clean_text(text)

            model_entities = self.model_service.predict(text)
            logger.info(f"模型抽取: {len(model_entities)}个实体")

            regex_entities = self.regex_extractor.extract(text)
            logger.info(f"正则抽取: {len(regex_entities)}个实体")

            all_entities = model_entities + regex_entities

            final_entities = self.rule_engine.apply_rules(all_entities, text)
            final_entities = self._resolve_conflicts(final_entities)

            final_entities.sort(key=lambda x: (x.get("start_pos", 999999), -x.get("confidence", 0)))

            elapsed = int((time.time() - start_time) * 1000)
            logger.info(f"实体抽取完成: 最终{len(final_entities)}个实体, 耗时{elapsed}ms")

            return {
                "success": True,
                "entities": final_entities,
                "error_message": None,
                "processing_time_ms": elapsed
            }
        except Exception as e:
            logger.error(f"实体抽取失败: {e}", exc_info=True)
            return {
                "success": False,
                "entities": [],
                "error_message": str(e),
                "processing_time_ms": int((time.time() - start_time) * 1000)
            }

    def _resolve_conflicts(self, entities: List[Dict[str, Any]]) -> List[Dict[str, Any]]:
        type_priority = {
            "REGEX": 3,
            "RULE": 3,
            "MANUAL": 5,
            "MODEL": 2,
        }

        entities.sort(key=lambda x: (
            -(type_priority.get(x.get("source", "MODEL"), 1) * 100 + int(x.get("confidence", 0) * 100))
        ))

        filtered = []
        used_positions = set()

        for entity in entities:
            start = entity.get("start_pos")
            end = entity.get("end_pos")
            etype = entity["entity_type"]

            pos_key = None
            if start is not None and end is not None:
                pos_key = (etype, start, end)

            if pos_key and pos_key in used_positions:
                continue

            if start is not None and end is not None:
                overlap = False
                for used_type, used_start, used_end in used_positions:
                    if used_type == etype and not (end <= used_start or start >= used_end):
                        if (end - start) <= (used_end - used_start):
                            overlap = True
                            break
                if overlap:
                    continue

            filtered.append(entity)
            if pos_key:
                used_positions.add(pos_key)

        type_groups = {}
        for entity in filtered:
            etype = entity["entity_type"]
            if etype not in type_groups:
                type_groups[etype] = []
            type_groups[etype].append(entity)

        final = []
        single_types = {
            "PATIENT_NAME", "GENDER", "AGE", "HOSPITAL_NO", "DEPARTMENT",
            "SURGERY_DATE", "INCISION_LEVEL", "INCISION_HEALING",
            "BLOOD_LOSS", "BLOOD_TRANSFUSION", "FLUID_INFUSION"
        }

        for etype, group in type_groups.items():
            if etype in single_types:
                group.sort(key=lambda x: -x.get("confidence", 0))
                final.append(group[0])
            else:
                final.extend(group)

        return final
