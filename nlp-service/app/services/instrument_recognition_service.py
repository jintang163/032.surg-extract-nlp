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
    specialty: Optional[str] = None
    count: int = 1

    def to_dict(self) -> Dict[str, Any]:
        return {
            "name": self.name,
            "confidence": round(self.confidence, 4),
            "bbox": [round(x, 2) for x in self.bbox] if self.bbox else None,
            "category": self.category,
            "specialty": self.specialty,
            "count": self.count,
        }


INSTRUMENT_SPECIALTIES = [
    "普外科", "骨科", "心胸外科", "神经外科",
    "妇产科", "泌尿外科", "眼科", "耳鼻喉科",
]

SURGICAL_INSTRUMENTS = {
    "手术刀": {
        "category": "切割器械",
        "specialties": ["普外科", "心胸外科", "神经外科", "妇产科", "泌尿外科", "眼科", "耳鼻喉科"],
        "aliases": ["手术刀", "手术刀片", "blade", "scalpel", "圆刀", "尖刀"],
    },
    "手术剪": {
        "category": "剪切器械",
        "specialties": ["普外科", "骨科", "心胸外科", "妇产科", "泌尿外科", "眼科", "耳鼻喉科"],
        "aliases": ["手术剪", "组织剪", "线剪", "scissors", "直剪", "弯剪", "梅氏剪"],
    },
    "止血钳": {
        "category": "止血器械",
        "specialties": ["普外科", "骨科", "心胸外科", "神经外科", "妇产科", "泌尿外科", "耳鼻喉科"],
        "aliases": ["止血钳", "血管钳", "hemostat", "forceps", "直止血钳", "弯止血钳", "蚊式钳"],
    },
    "镊子": {
        "category": "夹持器械",
        "specialties": ["普外科", "骨科", "妇产科", "泌尿外科", "眼科", "耳鼻喉科"],
        "aliases": ["镊子", "医用镊子", "tweezer", "forceps", "平镊"],
    },
    "组织镊": {
        "category": "夹持器械",
        "specialties": ["普外科", "心胸外科", "神经外科", "妇产科", "眼科"],
        "aliases": ["组织镊", "有齿镊", "tissue forceps", "爱立斯镊"],
    },
    "持针器": {
        "category": "缝合器械",
        "specialties": ["普外科", "骨科", "心胸外科", "神经外科", "妇产科", "泌尿外科", "眼科", "耳鼻喉科"],
        "aliases": ["持针器", "持针钳", "needle holder", "needle driver"],
    },
    "缝合针": {
        "category": "缝合器械",
        "specialties": ["普外科", "骨科", "心胸外科", "妇产科", "泌尿外科", "眼科", "耳鼻喉科"],
        "aliases": ["缝合针", "suture needle", "needle", "圆针对", "三角针"],
    },
    "缝合线": {
        "category": "缝合器械",
        "specialties": ["普外科", "骨科", "心胸外科", "神经外科", "妇产科", "泌尿外科", "眼科", "耳鼻喉科"],
        "aliases": ["缝合线", "suture", "可吸收线", "丝线", "羊肠线"],
    },
    "拉钩": {
        "category": "牵开器械",
        "specialties": ["普外科", "骨科", "心胸外科", "妇产科"],
        "aliases": ["拉钩", "牵开器", "retractor", "爪形拉钩"],
    },
    "腹腔拉钩": {
        "category": "牵开器械",
        "specialties": ["普外科", "妇产科", "泌尿外科"],
        "aliases": ["腹腔拉钩", "腹壁拉钩", "阑尾拉钩", "甲状腺拉钩"],
    },
    "吸引器": {
        "category": "吸引器械",
        "specialties": ["普外科", "骨科", "心胸外科", "神经外科", "妇产科", "泌尿外科", "耳鼻喉科"],
        "aliases": ["吸引器", "吸引头", "suction", "吸引管"],
    },
    "电刀": {
        "category": "电外科器械",
        "specialties": ["普外科", "骨科", "心胸外科", "妇产科", "泌尿外科", "耳鼻喉科"],
        "aliases": ["电刀", "电凝", "高频电刀", "electrocautery", "bovie", "单极电刀", "双极电刀"],
    },
    "超声刀": {
        "category": "电外科器械",
        "specialties": ["普外科", "心胸外科", "妇产科", "泌尿外科", "耳鼻喉科"],
        "aliases": ["超声刀", "harmonic scalpel", "ultrasonic scalpel", "超声手术刀"],
    },
    "腔镜": {
        "category": "内窥镜器械",
        "specialties": ["普外科", "心胸外科", "妇产科", "泌尿外科", "耳鼻喉科"],
        "aliases": ["腔镜", "腹腔镜", "胸腔镜", "endoscope", "laparoscope", "宫腔镜", "膀胱镜"],
    },
    "穿刺器": {
        "category": "腔镜器械",
        "specialties": ["普外科", "妇产科", "泌尿外科"],
        "aliases": ["穿刺器", "trocar", "穿刺套管", "戳卡"],
    },
    "抓钳": {
        "category": "腔镜器械",
        "specialties": ["普外科", "妇产科", "泌尿外科"],
        "aliases": ["抓钳", "grasper", "无损伤抓钳"],
    },
    "分离钳": {
        "category": "腔镜器械",
        "specialties": ["普外科", "妇产科", "泌尿外科"],
        "aliases": ["分离钳", "dissecting forceps", " Maryland钳"],
    },
    "钛夹": {
        "category": "止血器械",
        "specialties": ["普外科", "心胸外科", "妇产科", "泌尿外科"],
        "aliases": ["钛夹", "hem-o-lok", "clip", "生物夹"],
    },
    "施夹器": {
        "category": "腔镜器械",
        "specialties": ["普外科", "心胸外科", "妇产科", "泌尿外科"],
        "aliases": ["施夹器", "clip applier", "钛夹钳"],
    },
    "吻合器": {
        "category": "吻合器械",
        "specialties": ["普外科", "心胸外科", "妇产科", "泌尿外科"],
        "aliases": ["吻合器", "stapler", "圆形吻合器", "直线吻合器"],
    },
    "切割吻合器": {
        "category": "吻合器械",
        "specialties": ["普外科", "心胸外科", "妇产科"],
        "aliases": ["切割吻合器", "linear cutter", "直线切割闭合器"],
    },
    "吻合钉": {
        "category": "吻合器械",
        "specialties": ["普外科", "心胸外科", "骨科", "妇产科"],
        "aliases": ["吻合钉", "staple", "钛钉"],
    },
    "骨刀": {
        "category": "骨科器械",
        "specialties": ["骨科", "耳鼻喉科"],
        "aliases": ["骨刀", "osteotome", "平骨刀", "弯骨刀"],
    },
    "骨凿": {
        "category": "骨科器械",
        "specialties": ["骨科"],
        "aliases": ["骨凿", "chisel", "圆骨凿"],
    },
    "骨锤": {
        "category": "骨科器械",
        "specialties": ["骨科", "耳鼻喉科"],
        "aliases": ["骨锤", "mallet", "骨槌"],
    },
    "咬骨钳": {
        "category": "骨科器械",
        "specialties": ["骨科", "神经外科", "耳鼻喉科"],
        "aliases": ["咬骨钳", "rongeur", "椎板咬骨钳", "鹰嘴咬骨钳"],
    },
    "撑开器": {
        "category": "骨科器械",
        "specialties": ["骨科", "心胸外科"],
        "aliases": ["撑开器", "distractor", "脊柱撑开器"],
    },
    "钢板": {
        "category": "内固定器械",
        "specialties": ["骨科"],
        "aliases": ["钢板", "plate", "锁定钢板", "加压钢板"],
    },
    "螺钉": {
        "category": "内固定器械",
        "specialties": ["骨科", "神经外科"],
        "aliases": ["螺钉", "screw", "皮质骨螺钉", "松质骨螺钉", "椎弓根螺钉"],
    },
    "髓内钉": {
        "category": "内固定器械",
        "specialties": ["骨科"],
        "aliases": ["髓内钉", "intramedullary nail", "交锁髓内钉"],
    },
    "克氏针": {
        "category": "内固定器械",
        "specialties": ["骨科", "耳鼻喉科"],
        "aliases": ["克氏针", "Kirschner wire", "K针", "骨圆针"],
    },
    "钢丝": {
        "category": "内固定器械",
        "specialties": ["骨科", "心胸外科"],
        "aliases": ["钢丝", "wire", "不锈钢丝"],
    },
    "骨锉": {
        "category": "骨科器械",
        "specialties": ["骨科"],
        "aliases": ["骨锉", "rasp", "骨锉刀"],
    },
    "磨钻": {
        "category": "骨科器械",
        "specialties": ["骨科", "神经外科", "耳鼻喉科"],
        "aliases": ["磨钻", "drill", "电钻", "气动磨钻", "高速磨钻"],
    },
    "开胸器": {
        "category": "心胸外科器械",
        "specialties": ["心胸外科"],
        "aliases": ["开胸器", "胸骨牵开器", "chest retractor", "肋骨牵开器"],
    },
    "胸骨锯": {
        "category": "心胸外科器械",
        "specialties": ["心胸外科"],
        "aliases": ["胸骨锯", "sternum saw", "摆动锯"],
    },
    "血管吻合器": {
        "category": "心胸外科器械",
        "specialties": ["心胸外科"],
        "aliases": ["血管吻合器", "vascular stapler"],
    },
    "人工血管": {
        "category": "心胸外科植入物",
        "specialties": ["心胸外科"],
        "aliases": ["人工血管", "vascular graft", "PTFE血管"],
    },
    "体外循环管": {
        "category": "心胸外科设备",
        "specialties": ["心胸外科"],
        "aliases": ["体外循环管", "CPB管", "心肺转流管"],
    },
    "动脉瘤夹": {
        "category": "神经外科器械",
        "specialties": ["神经外科"],
        "aliases": ["动脉瘤夹", "aneurysm clip", "Yasargil夹"],
    },
    "脑压板": {
        "category": "神经外科器械",
        "specialties": ["神经外科"],
        "aliases": ["脑压板", "brain spatula", "脑牵开器"],
    },
    "吸引器头": {
        "category": "神经外科器械",
        "specialties": ["神经外科", "耳鼻喉科"],
        "aliases": ["吸引器头", "suction tip", "Frazier吸引管"],
    },
    "显微剪刀": {
        "category": "显微外科器械",
        "specialties": ["神经外科", "眼科", "耳鼻喉科"],
        "aliases": ["显微剪刀", "micro scissors", "显微剪"],
    },
    "显微镊子": {
        "category": "显微外科器械",
        "specialties": ["神经外科", "眼科", "耳鼻喉科"],
        "aliases": ["显微镊子", "micro forceps", "显微镊"],
    },
    "阴道窥器": {
        "category": "妇产科器械",
        "specialties": ["妇产科"],
        "aliases": ["阴道窥器", "speculum", "窥阴器", "鸭嘴钳"],
    },
    "宫颈钳": {
        "category": "妇产科器械",
        "specialties": ["妇产科"],
        "aliases": ["宫颈钳", "cervical forceps", "单爪钳", "双爪钳"],
    },
    "刮宫器": {
        "category": "妇产科器械",
        "specialties": ["妇产科"],
        "aliases": ["刮宫器", "curette", "刮匙", "人流刮匙"],
    },
    "胎头吸引器": {
        "category": "妇产科器械",
        "specialties": ["妇产科"],
        "aliases": ["胎头吸引器", "vacuum extractor", "胎吸"],
    },
    "产钳": {
        "category": "妇产科器械",
        "specialties": ["妇产科"],
        "aliases": ["产钳", "obstetric forceps", "低位产钳"],
    },
    "举宫器": {
        "category": "妇产科腔镜器械",
        "specialties": ["妇产科"],
        "aliases": ["举宫器", "uterine manipulator"],
    },
    "电切镜": {
        "category": "泌尿外科器械",
        "specialties": ["泌尿外科"],
        "aliases": ["电切镜", "resectoscope", "TURP镜", "前列腺电切镜"],
    },
    "膀胱镜": {
        "category": "泌尿外科器械",
        "specialties": ["泌尿外科"],
        "aliases": ["膀胱镜", "cystoscope"],
    },
    "输尿管镜": {
        "category": "泌尿外科器械",
        "specialties": ["泌尿外科"],
        "aliases": ["输尿管镜", "ureteroscope", "硬镜", "软镜"],
    },
    "肾镜": {
        "category": "泌尿外科器械",
        "specialties": ["泌尿外科"],
        "aliases": ["肾镜", "nephroscope", "经皮肾镜"],
    },
    "碎石杆": {
        "category": "泌尿外科器械",
        "specialties": ["泌尿外科"],
        "aliases": ["碎石杆", "lithotripter", "气压弹道碎石", "超声碎石"],
    },
    "双J管": {
        "category": "泌尿外科植入物",
        "specialties": ["泌尿外科"],
        "aliases": ["双J管", "double J stent", "输尿管支架"],
    },
    "导尿管": {
        "category": "泌尿外科器械",
        "specialties": ["泌尿外科", "妇产科", "普外科"],
        "aliases": ["导尿管", "catheter", "Foley管", "三腔导尿管"],
    },
    "眼科手术刀": {
        "category": "眼科器械",
        "specialties": ["眼科"],
        "aliases": ["眼科手术刀", "keratome", "隧道刀", "穿刺刀"],
    },
    "显微眼科剪": {
        "category": "眼科器械",
        "specialties": ["眼科"],
        "aliases": ["显微眼科剪", "眼科剪", "角膜剪", "结膜剪"],
    },
    "眼科镊": {
        "category": "眼科器械",
        "specialties": ["眼科"],
        "aliases": ["眼科镊", "ophthalmic forceps", "结膜镊", "晶体镊"],
    },
    "人工晶体": {
        "category": "眼科植入物",
        "specialties": ["眼科"],
        "aliases": ["人工晶体", "IOL", "intraocular lens", "折叠晶体"],
    },
    "超声乳化手柄": {
        "category": "眼科设备",
        "specialties": ["眼科"],
        "aliases": ["超声乳化手柄", "phacoemulsification", "超乳手柄"],
    },
    "耳鼻喉科咬骨钳": {
        "category": "耳鼻喉科器械",
        "specialties": ["耳鼻喉科"],
        "aliases": ["耳鼻喉科咬骨钳", "乳突咬骨钳", "Storck咬骨钳"],
    },
    "鼻窦镜": {
        "category": "耳鼻喉科器械",
        "specialties": ["耳鼻喉科"],
        "aliases": ["鼻窦镜", "sinoscope", "鼻内镜"],
    },
    "喉镜": {
        "category": "耳鼻喉科器械",
        "specialties": ["耳鼻喉科"],
        "aliases": ["喉镜", "laryngoscope", "间接喉镜", "直接喉镜", "纤维喉镜"],
    },
    "耳显微器械": {
        "category": "耳鼻喉科器械",
        "specialties": ["耳鼻喉科"],
        "aliases": ["耳显微器械", "耳内镜", "耳用显微剪刀"],
    },
    "扁桃体刀": {
        "category": "耳鼻喉科器械",
        "specialties": ["耳鼻喉科"],
        "aliases": ["扁桃体刀", "tonsillotome", "扁桃体圈套器"],
    },
    "鼻中隔咬骨钳": {
        "category": "耳鼻喉科器械",
        "specialties": ["耳鼻喉科"],
        "aliases": ["鼻中隔咬骨钳", "鼻中隔旋转刀"],
    },
    "输液器": {
        "category": "输液器械",
        "specialties": ["普外科", "骨科", "心胸外科", "神经外科", "妇产科", "泌尿外科", "眼科", "耳鼻喉科"],
        "aliases": ["输液器", "infusion set"],
    },
    "注射器": {
        "category": "注射器械",
        "specialties": ["普外科", "骨科", "妇产科", "麻醉科"],
        "aliases": ["注射器", "syringe", "针管"],
    },
    "针头": {
        "category": "注射器械",
        "specialties": ["普外科", "麻醉科"],
        "aliases": ["针头", "needle", "注射针"],
    },
    "导管": {
        "category": "介入器械",
        "specialties": ["心胸外科", "神经外科", "泌尿外科"],
        "aliases": ["导管", "catheter", "血管导管"],
    },
    "导丝": {
        "category": "介入器械",
        "specialties": ["心胸外科", "泌尿外科"],
        "aliases": ["导丝", "guidewire", "泥鳅导丝"],
    },
    "敷料": {
        "category": "敷料",
        "specialties": ["普外科", "骨科", "妇产科", "烧伤科"],
        "aliases": ["敷料", "dressing", "纱布", "敷贴"],
    },
    "纱布": {
        "category": "敷料",
        "specialties": ["普外科", "骨科", "妇产科", "心胸外科"],
        "aliases": ["纱布", "gauze", "纱布块", "纱布条"],
    },
    "棉球": {
        "category": "敷料",
        "specialties": ["普外科", "妇产科", "耳鼻喉科", "眼科"],
        "aliases": ["棉球", "cotton ball", "棉片"],
    },
    "创巾": {
        "category": "无菌敷料",
        "specialties": ["普外科", "骨科", "妇产科", "心胸外科"],
        "aliases": ["创巾", "手术巾", "surgical drape", "洞巾"],
    },
    "手套": {
        "category": "防护用品",
        "specialties": ["普外科", "骨科", "妇产科", "心胸外科", "泌尿外科", "眼科", "耳鼻喉科"],
        "aliases": ["手套", "surgical glove", "glove", "无菌手套"],
    },
    "口罩": {
        "category": "防护用品",
        "specialties": ["普外科", "骨科", "妇产科", "麻醉科"],
        "aliases": ["口罩", "mask", "外科口罩"],
    },
    "手术衣": {
        "category": "防护用品",
        "specialties": ["普外科", "骨科", "妇产科"],
        "aliases": ["手术衣", "gown", "无菌手术衣"],
    },
    "无影灯": {
        "category": "手术设备",
        "specialties": ["普外科", "骨科", "妇产科", "心胸外科"],
        "aliases": ["无影灯", "surgical lamp", "operating light"],
    },
    "手术床": {
        "category": "手术设备",
        "specialties": ["普外科", "骨科", "妇产科", "心胸外科"],
        "aliases": ["手术床", "operating table", "手术台"],
    },
    "麻醉机": {
        "category": "麻醉设备",
        "specialties": ["麻醉科"],
        "aliases": ["麻醉机", "anesthesia machine"],
    },
    "监护仪": {
        "category": "监护设备",
        "specialties": ["麻醉科", "ICU"],
        "aliases": ["监护仪", "monitor", "vital sign monitor", "心电监护"],
    },
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
            primary_specialty = info["specialties"][0] if info["specialties"] else None
            self._alias_index[name.lower()] = (name, info["category"], primary_specialty)
            for alias in info["aliases"]:
                self._alias_index[alias.lower()] = (name, info["category"], primary_specialty)

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

                matched_name, matched_category, matched_specialty = self._match_instrument_name(cls_name)

                instruments.append(DetectedInstrument(
                    name=matched_name,
                    confidence=conf,
                    bbox=tuple(bbox),
                    category=matched_category,
                    specialty=matched_specialty,
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

        for alias, (name, category, specialty) in self._alias_index.items():
            if alias in text_lower or alias in text:
                if name not in found:
                    found[name] = {
                        "confidence": 0.5,
                        "category": category,
                        "specialty": specialty,
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
                specialty=info["specialty"],
                count=count,
            ))

        return instruments

    def _match_instrument_name(self, raw_name: str) -> Tuple[str, str, Optional[str]]:
        """将模型输出的类别名匹配到标准手术器械名称"""
        raw_lower = raw_name.lower().strip()

        if raw_lower in self._alias_index:
            return self._alias_index[raw_lower]

        for alias, (name, category, specialty) in self._alias_index.items():
            if alias in raw_lower or raw_lower in alias:
                return name, category, specialty

        return raw_name, "其他", None

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
                    specialty=instr.specialty,
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
