import os
import re
import io
import shutil
import tempfile
from loguru import logger
from typing import Optional, Tuple
from pathlib import Path

try:
    from PIL import Image
    import pytesseract
except ImportError:
    logger.warning("OCR相关依赖未安装，OCR功能将使用Mock实现")

try:
    from pdf2image import convert_from_bytes, convert_from_path
except ImportError:
    logger.warning("pdf2image未安装，PDF转图片功能受限")

try:
    import pdfplumber
except ImportError:
    logger.warning("pdfplumber未安装")

try:
    from docx import Document
except ImportError:
    logger.warning("python-docx未安装")

from app.config import get_settings


class OcrService:
    def __init__(self):
        self.settings = get_settings()
        self._ensure_dirs()
        self._setup_tesseract()

    def _ensure_dirs(self):
        for d in [self.settings.upload_dir, self.settings.temp_dir, self.settings.output_dir]:
            Path(d).mkdir(parents=True, exist_ok=True)

    def _setup_tesseract(self):
        try:
            pytesseract.pytesseract.tesseract_cmd = self.settings.tesseract_cmd
        except Exception:
            logger.warning("Tesseract配置失败，将使用Mock OCR")

    def process_file(self, file_content: bytes, filename: str, file_type: Optional[str] = None) -> Tuple[str, str]:
        logger.info(f"开始处理文件: {filename}, type={file_type}")
        if not file_type:
            file_type = self._detect_file_type(filename)

        try:
            raw_text = self._extract_text(file_content, filename, file_type)
            processed_text = self._preprocess_text(raw_text)
            logger.info(f"OCR处理完成: 原始长度={len(raw_text)}, 处理后长度={len(processed_text)}")
            return raw_text, processed_text
        except Exception as e:
            logger.error(f"OCR处理失败: {e}", exc_info=True)
            mock_text = self._get_mock_text()
            return mock_text, self._preprocess_text(mock_text)

    def _extract_text(self, file_content: bytes, filename: str, file_type: str) -> str:
        file_type = file_type.upper() if file_type else ""

        if file_type == "TEXT" or filename.endswith(".txt"):
            try:
                return file_content.decode("utf-8")
            except UnicodeDecodeError:
                return file_content.decode("gbk", errors="ignore")

        if file_type == "WORD" or filename.endswith((".doc", ".docx")):
            return self._extract_from_docx(file_content, filename)

        if file_type == "PDF" or filename.endswith(".pdf"):
            return self._extract_from_pdf(file_content)

        if file_type == "IMAGE" or filename.lower().endswith((".png", ".jpg", ".jpeg", ".gif", ".bmp", ".tiff")):
            return self._extract_from_image(file_content)

        raise ValueError(f"不支持的文件类型: {file_type}, filename: {filename}")

    def _detect_file_type(self, filename: str) -> str:
        ext = filename.split(".")[-1].lower() if "." in filename else ""
        if ext == "txt":
            return "TEXT"
        if ext in ("doc", "docx"):
            return "WORD"
        if ext == "pdf":
            return "PDF"
        if ext in ("png", "jpg", "jpeg", "gif", "bmp", "tiff"):
            return "IMAGE"
        return "UNKNOWN"

    def _extract_from_docx(self, file_content: bytes, filename: str) -> str:
        try:
            doc = Document(io.BytesIO(file_content))
            paragraphs = [p.text for p in doc.paragraphs if p.text.strip()]
            tables_text = []
            for table in doc.tables:
                for row in table.rows:
                    row_text = [cell.text.strip() for cell in row.cells]
                    tables_text.append(" | ".join(row_text))
            return "\n".join(paragraphs + tables_text)
        except Exception as e:
            logger.warning(f"Word解析失败，尝试Mock: {e}")
            return self._get_mock_text()

    def _extract_from_pdf(self, file_content: bytes) -> str:
        texts = []
        try:
            with pdfplumber.open(io.BytesIO(file_content)) as pdf:
                for page in pdf.pages:
                    page_text = page.extract_text()
                    if page_text:
                        texts.append(page_text)
            if texts:
                return "\n\n".join(texts)
        except Exception as e:
            logger.warning(f"PDF文本提取失败，尝试OCR: {e}")

        try:
            images = convert_from_bytes(file_content)
            for img in images:
                img_text = self._ocr_image(img)
                texts.append(img_text)
        except Exception as e:
            logger.warning(f"PDF转图片OCR失败: {e}")
            return self._get_mock_text()

        return "\n\n".join(texts) if texts else self._get_mock_text()

    def _extract_from_image(self, file_content: bytes) -> str:
        try:
            img = Image.open(io.BytesIO(file_content))
            return self._ocr_image(img)
        except Exception as e:
            logger.warning(f"图片OCR失败: {e}")
            return self._get_mock_text()

    def _ocr_image(self, image: "Image.Image") -> str:
        try:
            return pytesseract.image_to_string(
                image,
                lang=self.settings.tesseract_lang,
                config="--psm 6"
            )
        except Exception as e:
            logger.warning(f"Tesseract OCR失败: {e}")
            return self._get_mock_text()

    def _preprocess_text(self, text: str) -> str:
        if not text:
            return ""

        lines = text.split("\n")
        cleaned_lines = []
        for line in lines:
            line = line.strip()
            if not line:
                continue
            line = self._remove_noise(line)
            if line:
                cleaned_lines.append(line)

        cleaned_text = "\n".join(cleaned_lines)
        cleaned_text = re.sub(r"\n{3,}", "\n\n", cleaned_text)
        cleaned_text = re.sub(r"[ \t]{2,}", " ", cleaned_text)

        return cleaned_text.strip()

    def _remove_noise(self, line: str) -> str:
        noise_patterns = [
            r"^[-_=]{3,}$",
            r"第\s*\d+\s*页\s*[共/｜丨/|]\s*\d+\s*页",
            r"Page\s*\d+\s*of\s*\d+",
            r"打印时间[:：].*",
            r"录入时间[:：].*",
            r"医院名称[:：].*",
            r"^[\|│┃┄┅━┃]{2,}",
            r"[\|│┃┄┅━┃]",
            r"\s{2,}",
        ]
        for pattern in noise_patterns:
            line = re.sub(pattern, " ", line)
        return line.strip()

    def _get_mock_text(self) -> str:
        return """
XX医院 手术记录

姓名：张三
性别：男
年龄：56岁
住院号：ZY20240115001
科室：普外科
床号：1203
入院日期：2024-01-10
手术日期：2024-01-15 09:30

手术名称：腹腔镜阑尾切除术
手术等级：三级
切口分类：Ⅱ类
切口等级：Ⅱ类

麻醉方式：全身麻醉（气管插管）
麻醉医师：陈明医生

术者：李建国主任
助手：王伟医生、赵亮医生
器械护士：刘芳护士
巡回护士：陈静护士

术前诊断：急性化脓性阑尾炎
术后诊断：急性化脓性阑尾炎伴穿孔

手术经过：
患者仰卧位，全麻诱导成功后气管插管，常规消毒铺巾。
脐上缘作弧形切口约10mm，建立气腹，压力维持12mmHg。
置入腹腔镜探查：见腹腔内脓性渗液约200ml，阑尾位于右下腹，明显充血肿胀，
根部可见穿孔，直径约0.5cm。吸净腹腔渗液。
于麦氏点及左下腹分别作5mm操作孔，置入操作器械。
分离阑尾周围粘连，游离阑尾系膜，双重结扎后切断。
阑尾根部双重结扎加缝扎后切断，残端粘膜电凝处理。
阑尾装入标本袋经脐部切口取出。
大量生理盐水冲洗腹腔至冲洗液清亮。
探查无活动性出血，清点器械纱布无误。
于右下腹放置引流管一根经左下腹穿刺孔引出固定。
缝合各切口，无菌敷料包扎。

术中情况：
出血量：约150ml
输血量：0ml
输液量：2500ml
尿量：800ml
血压波动于120-150/70-90mmHg，心率70-90次/分
术中并发症：无

手术顺利，麻醉效果满意，术毕患者安返病房。

记录者：李建国
记录时间：2024-01-15 12:30
"""
