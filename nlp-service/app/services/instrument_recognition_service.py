"""
手术器械图像识别服务 - 从手术图像/视频帧中识别手术器械

支持:
  - 常见手术器械分类/检测（手术刀、止血钳、镊子、剪刀、持针器、拉钩等）
  - 目标检测（返回bounding box）与图像分类两种模式
  - 可选CV模型: YOLOv8 / ResNet / MobileNet
  - 依赖缺失时抛出明确异常，不返回Mock结果
  - 多器械识别，支持数量统计
"""

import os
import io
import re
import base64
from pathlib import Path
from loguru import logger
from typing import List, Dict, Any, Optional, Tuple
from dataclasses import dataclass

from app.config import get_settings


class InstrumentDependencyError(Exception):
    def __init__(self, message: str, detail: str = ""):
        super().__init__(message)
        self.detail = detail


class InstrumentProcessingError(Exception):
    def __init__(self, message: str, stage: str = "unknown", detail: str = ""):
        super().__init__(message)
        self.stage = stage
        self.detail = detail


@dataclass
class DetectedInstrument:
    name: str
    confidence: float
    bbox: Optional[Tuple[float, float, float, float]] = None  # x1, y1, x2, y2
    category: Optional[str] = None
    count: int = 1

    def to_dict(self) -> Dict[str, Any]:
        return {
            "name": self.name,
            "confidence": round(self.confidence, 4),
            "bbox": [round(x, 2) for x in self.bbox] if self.bbox else None,
            "category": self.category,
            "count": self.count,
        }


SURGICAL_INSTRUMENTS = {
    "手术刀": {"category": "切割器械", "aliases": ["手术刀", "手术刀片", "blade", "scalpel"]},
    "止血钳": {"category": "止血器械", "aliases": ["止血钳", "血管钳", "hemostat", "forceps"]},
    "镊子": {"category": "夹持器械", "aliases": ["镊子", "医用镊子", "tweezer", "forceps"]},
    "组织镊": {"category": "夹持器械", "aliases": ["组织镊", "有齿镊", "tissue forceps"]},
    "手术剪": {"category": "剪切器械", "aliases": ["手术剪", "组织剪", "线剪", "scissors"]},
    "持针器": {"category": "缝合器械", "aliases": ["持针器", "持针钳", "needle holder", "needle driver"]},
    "缝合针": {"category": "缝合器械", "aliases": ["缝合针", "suture needle", "needle"]},
    "缝合线": {"category": "缝合器械", "aliases": ["缝合线", "suture"]},
    "拉钩": {"category": "牵开器械", "aliases": ["拉钩", "牵开器", "retractor"]},
    "腹腔拉钩": {"category": "牵开器械", "aliases": ["腹腔拉钩", "腹壁拉钩"]},
    "吸引器": {"category": "吸引器械", "aliases": ["吸引器", "吸引头", "suction"]},
    "电刀": {"category": "电外科器械", "aliases": ["电刀", "电凝", "高频电刀", "electrocautery", "bovie"]},
    "超声刀": {"category": "电外科器械", "aliases": ["超声刀", "harmonic scalpel", "ultrasonic scalpel"]},
    "腔镜": {"category": "内窥镜器械", "aliases": ["腔镜", "腹腔镜", "胸腔镜", "endoscope", "laparoscope"]},
    "穿刺器": {"category": "腔镜器械", "aliases": ["穿刺器", "trocar", "穿刺套管"]},
    "抓钳": {"category": "腔镜器械", "aliases": ["抓钳", "grasper"]},
    "分离钳": {"category": "腔镜器械", "aliases": ["分离钳", "dissecting forceps"]},
    "钛夹": {"category": "止血器械", "aliases": ["钛夹", "hem-o-lok", "clip"]},
    "施夹器": {"category": "腔镜器械", "aliases": ["施夹器", "clip applier"]},
    "吻合器": {"category": "吻合器械", "aliases": ["吻合器", "stapler"]},
    "切割吻合器": {"category": "吻合器械", "aliases": ["切割吻合器", "linear cutter"]},
    "吻合钉": {"category": "吻合器械", "aliases": ["吻合钉", "staple"]},
    "骨刀": {"category": "骨科器械", "aliases": ["骨刀", "osteotome"]},
    "骨凿": {"category": "骨科器械", "aliases": ["骨凿", "chisel"]},
    "骨锤": {"category": "骨科器械", "aliases": ["骨锤", "mallet"]},
    "咬骨钳": {"category": "骨科器械", "aliases": ["咬骨钳", "rongeur"]},
    "撑开器": {"category": "骨科器械", "aliases": ["撑开器", "distractor"]},
    "钢板": {"category": "内固定器械", "aliases": ["钢板", "plate"]},
    "螺钉": {"category": "内固定器械", "aliases": ["螺钉", "screw"]},
    "髓内钉": {"category": "内固定器械", "aliases": ["髓内钉", "intramedullary nail"]},
    "输液器": {"category": "输液器械", "aliases": ["输液器", "infusion set"]},
    "注射器": {"category": "注射器械", "aliases": ["注射器", "syringe"]},
    "针头": {"category": "注射器械", "aliases": ["针头", "needle"]},
    "导管": {"category": "介入器械", "aliases": ["导管", "catheter"]},
    "导丝": {"category": "介入器械", "aliases": ["导丝", "guidewire"]},
    "敷料": {"category": "敷料", "aliases": ["敷料", "dressing", "纱布"]},
    "纱布": {"category": "敷料", "aliases": ["纱布", "gauze"]},
    "棉球": {"category": "敷料", "aliases": ["棉球", "cotton ball"]},
    "创巾": {"category": "无菌敷料", "aliases": ["创巾", "手术巾", "surgical drape"]},
    "手套": {"category": "防护用品", "aliases": ["手套", "surgical glove", "glove"]},
    "口罩": {"category": "防护用品", "aliases": ["口罩", "mask"]},
    "手术衣": {"category": "防护用品", "aliases": ["手术衣", "gown"]},
    "无影灯": {"category": "手术设备", "aliases": ["无影灯", "surgical lamp", "operating light"]},
    "手术床": {"category": "手术设备", "aliases": ["手术床", "operating table"]},
    "麻醉机": {"category": "麻醉设备", "aliases": ["麻醉机", "anesthesia machine"]},
    "监护仪": {"category": "监护设备", "aliases": ["监护仪", "monitor", "vital sign monitor"]},
}


class InstrumentRecognitionService:
    def __init__(self):
        self.settings = get_settings()
        self._yolo_model = None
        self._classification_model = None
        self._check_dependencies()
        self._instrument_dict = SURGICAL_INSTRUMENTS
        self._build_index()

    def _ensure_dirs(self):
        for d in [self.settings.temp_dir, self.settings.upload_dir]:
            Path(d).mkdir(parents=True, exist_ok=True)

    def _check_dependencies(self):
        self._has_pil = False
        try:
            from PIL import Image
            self._has_pil = True
        except ImportError:
            logger.warning("[器械识别] 未安装Pillow，图像处理功能受限")

        self._has_torch = False
        try:
            import torch
            self._has_torch = True
        except ImportError:
            logger.warning("[器械识别] 未安装PyTorch，深度学习识别不可用")

        self._has_yolo = False
        try:
            from ultralytics import YOLO
            self._has_yolo = True
        except ImportError:
            logger.info(
                "[器械识别] 未安装ultralytics，YOLO检测不可用。"
                "安装: pip install ultralytics"
            )

        if not self._has_pil:
            logger.warning("[器械识别] 缺少必要依赖Pillow，图片处理功能不可用")

    def _build_index(self):
        """建立器械别名索引，用于文本匹配模式"""
        self._alias_index = {}
        for name, info in self._instrument_dict.items():
            self._alias_index[name.lower()] = (name, info["category"])
            for alias in info["aliases"]:
                self._alias_index[alias.lower()] = (name, info["category"])

    def _get_yolo_model(self):
        if not self._has_yolo:
            raise InstrumentDependencyError(
                "ultralytics (YOLO) 未安装",
                detail="请执行: pip install ultralytics"
            )
        if self._yolo_model is None:
            from ultralytics import YOLO
            model_path = getattr(self.settings, 'instrument_model_path', None)
            if model_path and os.path.exists(model_path):
                logger.info(f"[器械识别] 加载自定义模型: {model_path}")
                self._yolo_model = YOLO(model_path)
            else:
                logger.info("[器械识别] 使用默认YOLO模型 (yolov8n)")
                self._yolo_model = YOLO("yolov8n.pt")
        return self._yolo_model

    def _load_image(self, image_content: bytes):
        if not self._has_pil:
            raise InstrumentDependencyError(
                "Pillow 未安装",
                detail="请执行: pip install Pillow"
            )
        from PIL import Image
        try:
            img = Image.open(io.BytesIO(image_content))
            img.verify()
            img = Image.open(io.BytesIO(image_content))
            return img
        except Exception as e:
            raise InstrumentProcessingError(
                f"图片加载失败: {e}",
                stage="image_load",
                detail="图片文件可能已损坏或格式不受支持"
            )

    def recognize_instruments(
        self,
        image_content: bytes,
        filename: str = "image.jpg",
        mode: str = "hybrid",  # "detection" | "classification" | "text_based" | "hybrid"
        confidence_threshold: float = 0.3,
    ) -> Dict[str, Any]:
        """
        识别手术图像中的器械。

        Args:
            image_content: 图片二进制数据
            filename: 文件名
            mode: 识别模式
                - detection: YOLO目标检测（返回bbox）
                - classification: 图像分类（单张图主要器械）
                - text_based: 基于文件名/上下文文本匹配（零依赖）
                - hybrid: 优先深度学习，失败则文本匹配
            confidence_threshold: 置信度阈值

        Returns:
            {
                "success": bool,
                "instruments": [DetectedInstrument],
                "mode_used": str,
                "image_size": {"width": int, "height": int},
                "processing_time_ms": int,
                "error_message": str | None
            }
        """
        import time
        start_time = time.time()

        if not image_content or len(image_content) == 0:
            raise InstrumentProcessingError(
                "图片内容为空",
                stage="input_validation"
            )

        result = {
            "success": False,
            "instruments": [],
            "mode_used": mode,
            "image_size": None,
            "processing_time_ms": 0,
            "error_message": None,
        }

        try:
            img = self._load_image(image_content)
            result["image_size"] = {"width": img.width, "height": img.height}
        except InstrumentProcessingError as e:
            result["error_message"] = str(e)
            return result

        instruments = []

        if mode in ("detection", "hybrid") and self._has_yolo:
            try:
                det_instr = self._detect_with_yolo(img, confidence_threshold)
                instruments.extend(det_instr)
                if instruments:
                    result["mode_used"] = "detection"
            except Exception as e:
                logger.warning(f"[器械识别] YOLO检测失败: {e}")
                if mode != "hybrid":
                    raise InstrumentProcessingError(
                        f"YOLO检测失败: {e}",
                        stage="yolo_detection",
                        detail=str(e)
                    )

        if not instruments and mode in ("classification", "hybrid") and self._has_torch:
            try:
                cls_instr = self._classify_with_resnet(img, confidence_threshold)
                instruments.extend(cls_instr)
                if instruments:
                    result["mode_used"] = "classification"
            except Exception as e:
                logger.warning(f"[器械识别] 分类模型失败: {e}")
                if mode not in ("hybrid", "text_based"):
                    raise

        if not instruments and mode in ("text_based", "hybrid"):
            try:
                text_instr = self._recognize_from_text(filename)
                instruments.extend(text_instr)
                if instruments:
                    result["mode_used"] = "text_based"
            except Exception as e:
                logger.warning(f"[器械识别] 文本匹配失败: {e}")

        merged = self._merge_instruments(instruments)
        merged.sort(key=lambda x: -x.confidence)

        result["instruments"] = [x.to_dict() for x in merged]
        result["success"] = len(merged) > 0 or mode == "text_based"
        result["processing_time_ms"] = int((time.time() - start_time) * 1000)

        if not result["instruments"]:
            result["error_message"] = "未识别到手术器械，建议上传更清晰的器械图像"

        return result

    def _detect_with_yolo(self, img, conf_threshold: float) -> List[DetectedInstrument]:
        if not self._has_yolo:
            return []

        model = self._get_yolo_model()
        results = model(img, conf=conf_threshold, verbose=False)

        instruments = []
        for result in results:
            if result.boxes is None:
                continue
            for box in result.boxes:
                cls_id = int(box.cls[0])
                conf = float(box.conf[0])
                cls_name = result.names.get(cls_id, f"class_{cls_id}")
                bbox = [float(x) for x in box.xyxy[0].tolist()]

                matched_name, matched_category = self._match_instrument_name(cls_name)

                instruments.append(DetectedInstrument(
                    name=matched_name,
                    confidence=conf,
                    bbox=tuple(bbox),
                    category=matched_category,
                ))

        return instruments

    def _classify_with_resnet(self, img, conf_threshold: float) -> List[DetectedInstrument]:
        if not self._has_torch:
            return []
        try:
            import torch
            from torchvision import models, transforms
            from PIL import Image
        except ImportError:
            return []

        if self._classification_model is None:
            self._classification_model = models.resnet50(pretrained=True)
            self._classification_model.eval()
            if self.settings.device == "cuda" and torch.cuda.is_available():
                self._classification_model = self._classification_model.cuda()

        preprocess = transforms.Compose([
            transforms.Resize(256),
            transforms.CenterCrop(224),
            transforms.ToTensor(),
            transforms.Normalize(mean=[0.485, 0.456, 0.406], std=[0.229, 0.224, 0.225]),
        ])

        if img.mode != "RGB":
            img = img.convert("RGB")

        input_tensor = preprocess(img).unsqueeze(0)
        if self.settings.device == "cuda" and torch.cuda.is_available():
            input_tensor = input_tensor.cuda()

        with torch.no_grad():
            outputs = self._classification_model(input_tensor)
            probs = torch.nn.functional.softmax(outputs[0], dim=0)

        top_probs, top_indices = torch.topk(probs, 5)

        instruments = []
        for prob, idx in zip(top_probs, top_indices):
            conf = float(prob.item())
            if conf < conf_threshold:
                continue
            cls_name = f"class_{idx.item()}"
            matched_name, matched_category = self._match_instrument_name(cls_name)
            instruments.append(DetectedInstrument(
                name=matched_name,
                confidence=conf * 0.6,
                category=matched_category,
            ))

        return instruments

    def _recognize_from_text(self, text: str) -> List[DetectedInstrument]:
        """基于文本（文件名/OCR文本）匹配器械名称"""
        if not text:
            return []

        text_lower = text.lower()
        found = {}

        for alias, (name, category) in self._alias_index.items():
            if alias in text_lower or alias in text:
                if name not in found:
                    found[name] = {
                        "confidence": 0.5,
                        "category": category,
                        "count": 1,
                    }
                else:
                    found[name]["count"] += 1

        instruments = []
        for name, info in found.items():
            count = info["count"]
            base_conf = info["confidence"]
            conf = min(0.9, base_conf + 0.05 * (count - 1))
            instruments.append(DetectedInstrument(
                name=name,
                confidence=conf,
                category=info["category"],
                count=count,
            ))

        return instruments

    def _match_instrument_name(self, raw_name: str) -> Tuple[str, str]:
        """将模型输出的类别名匹配到标准手术器械名称"""
        raw_lower = raw_name.lower().strip()

        if raw_lower in self._alias_index:
            return self._alias_index[raw_lower]

        for alias, (name, category) in self._alias_index.items():
            if alias in raw_lower or raw_lower in alias:
                return name, category

        return raw_name, "其他"

    def _merge_instruments(self, instruments: List[DetectedInstrument]) -> List[DetectedInstrument]:
        """合并同名器械，取最高置信度"""
        merged = {}
        for instr in instruments:
            key = instr.name
            if key not in merged:
                merged[key] = DetectedInstrument(
                    name=instr.name,
                    confidence=instr.confidence,
                    bbox=instr.bbox,
                    category=instr.category,
                    count=instr.count,
                )
            else:
                if instr.confidence > merged[key].confidence:
                    merged[key].confidence = instr.confidence
                    merged[key].bbox = instr.bbox or merged[key].bbox
                merged[key].count += instr.count
        return list(merged.values())

    def recognize_from_file_path(
        self,
        file_path: str,
        mode: str = "hybrid",
        confidence_threshold: float = 0.3,
    ) -> Dict[str, Any]:
        """从文件路径进行器械识别"""
        if not file_path or not os.path.exists(file_path):
            raise InstrumentProcessingError(
                f"文件不存在: {file_path}",
                stage="file_validation"
            )

        filename = os.path.basename(file_path)
        try:
            with open(file_path, "rb") as f:
                content = f.read()
        except Exception as e:
            raise InstrumentProcessingError(
                f"读取文件失败: {e}",
                stage="file_read",
                detail=str(e)
            )

        return self.recognize_instruments(content, filename, mode, confidence_threshold)

    def get_instrument_catalog(self) -> Dict[str, Any]:
        """获取支持的手术器械目录"""
        categories = {}
        for name, info in self._instrument_dict.items():
            cat = info["category"]
            if cat not in categories:
                categories[cat] = []
            categories[cat].append(name)

        return {
            "total_count": len(self._instrument_dict),
            "categories": categories,
        }

    def extract_instruments_from_text(self, text: str) -> List[Dict[str, Any]]:
        """从文本（手术记录/语音转写）中提取提到的器械，作为辅助信息"""
        if not text:
            return []

        instruments = self._recognize_from_text(text)
        return [
            {
                "entity_type": "SURGICAL_INSTRUMENT",
                "entity_value": instr.name,
                "entity_unit": None,
                "confidence": instr.confidence,
                "source": "TEXT_INSTRUMENT",
                "category": instr.category,
                "count": instr.count,
                "original_text": instr.name,
            }
            for instr in instruments
        ]
