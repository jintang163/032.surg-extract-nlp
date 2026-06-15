import re
import json
import os
from dataclasses import dataclass, field
from typing import List, Dict, Any, Optional, Tuple
from loguru import logger


@dataclass
class PcsCodeComponent:
    section: str = ""
    body_system: str = ""
    root_operation: str = ""
    body_part: str = ""
    approach: str = ""
    device: str = ""
    qualifier: str = ""


@dataclass
class PcsCodeRecommendation:
    pcs_code: str
    code_components: PcsCodeComponent
    description: str
    confidence: float
    match_path: List[str] = field(default_factory=list)
    matched_rules: List[str] = field(default_factory=list)
    missing_fields: List[str] = field(default_factory=list)
    is_complete: bool = False


@dataclass
class DecisionTreeNode:
    node_id: str
    field: str
    operator: str
    value: Any
    children: List["DecisionTreeNode"] = field(default_factory=list)
    pcs_component: Optional[Dict[str, str]] = None
    description: Optional[str] = None
    is_leaf: bool = False
    confidence: float = 1.0
    rule_name: str = ""


class Icd10PcsKnowledgeBase:
    SECTION_MAP = {
        "0": "Medical and Surgical",
        "B": "Imaging",
        "C": "Nuclear Medicine",
        "D": "Radiation Therapy",
        "F": "Physical Rehabilitation and Diagnostic Audiology",
        "G": "Mental Health",
        "H": "Substance Abuse Treatment",
        "X": "New Technology",
    }

    BODY_SYSTEM_MAP = {
        "central_nervous": "0",
        "head_and_face": "0",
        "cranium": "0",
        "brain": "0",
        "skull": "0",
        "peripheral_nervous": "1",
        "endocrine": "2",
        "eye": "3",
        "ear": "3",
        "head_face": "0",
        "respiratory": "4",
        "lung": "4",
        "chest": "4",
        "trachea": "4",
        "bronchus": "4",
        "cardiac": "5",
        "heart": "5",
        "coronary": "5",
        "vascular": "5",
        "blood_vessel": "5",
        "lymphatic": "6",
        "hematopoietic": "7",
        "bone_marrow": "7",
        "spleen": "7",
        "gastrointestinal": "D",
        "stomach": "D",
        "intestine": "D",
        "colon": "D",
        "rectum": "D",
        "esophagus": "D",
        "liver": "F",
        "biliary": "F",
        "pancreas": "F",
        "musculoskeletal": "K",
        "bone": "K",
        "joint": "K",
        "knee": "K",
        "hip": "K",
        "spine": "K",
        "vertebra": "K",
        "upper_extremity": "K",
        "lower_extremity": "K",
        "muscle": "L",
        "tendon": "L",
        "ligament": "L",
        "urinary": "T",
        "kidney": "T",
        "bladder": "T",
        "ureter": "T",
        "urethra": "T",
        "male_reproductive": "V",
        "female_reproductive": "U",
        "uterus": "U",
        "ovary": "U",
        "vagina": "U",
        "breast": "0",
        "integumentary": "H",
        "skin": "H",
        "subcutaneous": "J",
        "obstetrics": "1",
        "placement": "2",
    }

    ROOT_OPERATION_MAP = {
        "resection": "0",
        "excision": "0",
        "切除术": "0",
        "局部切除": "0",
        "活检": "B",
        "活检术": "B",
        "biopsy": "B",
        "excision_diagnostic": "B",
        "removal": "0",
        "切除": "0",
        "切除根治": "0",
        "根治切除": "0",
        "extirpation": "C",
        "取异物": "C",
        "异物取出": "C",
        "取石": "C",
        "取石术": "C",
        "drainage": "9",
        "引流": "9",
        "引流术": "9",
        "造瘘": "B",
        "造瘘术": "B",
        "造口": "B",
        "ostomy": "B",
        "inspection": "J",
        "探查": "J",
        "探查术": "J",
        "内镜检查": "J",
        "关节镜检查": "J",
        "腹腔镜检查": "J",
        "arthroscopy": "J",
        "endoscopy": "J",
        "laparoscopy": "J",
        "repair": "Q",
        "修补": "Q",
        "修补术": "Q",
        "缝合": "Q",
        "缝合术": "Q",
        "suture": "Q",
        "replacement": "R",
        "置换": "R",
        "置换术": "R",
        "replantation": "R",
        "移植": "R",
        "移植术": "R",
        "transplant": "R",
        "reconstruction": "0",
        "重建": "0",
        "重建术": "0",
        "plasty": "0",
        "成形": "0",
        "成形术": "0",
        "anastomosis": "B",
        "吻合": "B",
        "吻合术": "B",
        "bypass": "B",
        "搭桥": "B",
        "搭桥术": "B",
        "bypass_graft": "B",
        "fusion": "0",
        "融合": "0",
        "融合术": "0",
        "spinal_fusion": "0",
        "脊柱融合": "0",
        "fixation": "4",
        "固定": "4",
        "内固定": "4",
        "内固定术": "4",
        "reduction": "N",
        "复位": "N",
        "复位术": "N",
        "incision": "8",
        "切开": "8",
        "切开术": "8",
        "lysis": "N",
        "松解": "N",
        "松解术": "N",
        "adhesiolysis": "N",
        "ligation": "7",
        "结扎": "7",
        "结扎术": "7",
        "coagulation": "7",
        "电凝": "7",
        "电凝术": "7",
        "debridement": "8",
        "清创": "8",
        "清创术": "8",
        "graft": "K",
        "植皮": "K",
        "植皮术": "K",
        "skin_graft": "K",
        "bone_graft": "7",
        "植骨": "7",
        "植骨术": "7",
        "amputation": "0",
        "截肢": "0",
        "截肢术": "0",
        "occlusion": "7",
        "栓塞": "7",
        "栓塞术": "7",
        "embolization": "7",
        "ablation": "B",
        "消融": "B",
        "消融术": "B",
        "radiofrequency_ablation": "B",
        "射频消融": "B",
        "dilation": "7",
        "扩张": "7",
        "扩张术": "7",
        "stent_placement": "0",
        "支架置入": "0",
        "支架植入": "0",
        "stent": "0",
        "catheterization": "0",
        "导管置入": "0",
        "导管植入": "0",
        "monitoring": "4",
        "监测": "4",
        "监测术": "4",
        "fragmentation": "F",
        "碎石": "F",
        "碎石术": "F",
        "lithotripsy": "F",
        "control_hemorrhage": "3",
        "止血": "3",
        "止血术": "3",
    }

    BODY_PART_MAP = {
        "大脑": "0",
        "脑": "0",
        "额叶": "1",
        "颞叶": "2",
        "顶叶": "3",
        "枕叶": "4",
        "小脑": "9",
        "脑干": "C",
        "丘脑": "7",
        "基底节": "6",
        "脑室": "5",
        "垂体": "4",
        "甲状腺": "0",
        "甲状旁腺": "1",
        "肾上腺": "2",
        "胰腺内分泌": "3",
        "松果体": "7",
        "眼球": "0",
        "眼": "0",
        "视网膜": "8",
        "晶状体": "7",
        "角膜": "6",
        "结膜": "3",
        "泪器": "9",
        "眼外肌": "B",
        "眼眶": "Z",
        "中耳": "C",
        "内耳": "D",
        "外耳": "B",
        "鼓膜": "A",
        "听小骨": "D",
        "前庭": "E",
        "耳蜗": "F",
        "鼻腔": "0",
        "鼻窦": "1",
        "鼻": "9",
        "喉": "C",
        "声带": "D",
        "会厌": "B",
        "咽": "B",
        "口咽": "B",
        "鼻咽": "A",
        "下咽": "C",
        "气管": "0",
        "支气管": "1",
        "左主支气管": "2",
        "右主支气管": "3",
        "肺": "B",
        "左肺": "C",
        "右肺": "B",
        "肺上叶": "F",
        "肺中叶": "G",
        "肺下叶": "H",
        "纵隔": "0",
        "胸膜": "C",
        "心脏": "2",
        "心": "2",
        "心房": "3",
        "心室": "4",
        "左心房": "3",
        "右心房": "4",
        "左心室": "5",
        "右心室": "6",
        "二尖瓣": "9",
        "主动脉瓣": "B",
        "三尖瓣": "8",
        "肺动脉瓣": "C",
        "心包": "7",
        "冠状动脉": "D",
        "主动脉": "0",
        "升主动脉": "0",
        "降主动脉": "2",
        "腹主动脉": "3",
        "颈动脉": "3",
        "颈总动脉": "3",
        "髂动脉": "4",
        "股动脉": "5",
        "腘动脉": "6",
        "肺动脉": "F",
        "上腔静脉": "A",
        "下腔静脉": "B",
        "门静脉": "G",
        "脾静脉": "H",
        "食道": "2",
        "食管": "2",
        "胃": "D",
        "十二指肠": "F",
        "小肠": "H",
        "空肠": "H",
        "回肠": "J",
        "结肠": "K",
        "升结肠": "K",
        "横结肠": "L",
        "降结肠": "M",
        "乙状结肠": "N",
        "直肠": "P",
        "肛管": "Q",
        "阑尾": "E",
        "肝脏": "7",
        "肝": "7",
        "胆囊": "F",
        "胆管": "F",
        "胆总管": "F",
        "肝内胆管": "E",
        "胰": "G",
        "胰腺": "G",
        "脾脏": "7",
        "脾": "7",
        "腹膜": "0",
        "腹膜腔": "0",
        "网膜": "A",
        "肠系膜": "D",
        "肾": "1",
        "肾脏": "1",
        "左肾": "2",
        "右肾": "1",
        "肾盂": "3",
        "输尿管": "4",
        "膀胱": "T",
        "尿道": "T",
        "前列腺": "C",
        "睾丸": "8",
        "附睾": "9",
        "精索": "B",
        "精囊": "A",
        "阴茎": "D",
        "阴囊": "E",
        "子宫": "9",
        "宫颈": "B",
        "卵巢": "A",
        "输卵管": "7",
        "阴道": "D",
        "外阴": "C",
        "乳房": "0",
        "乳腺": "0",
        "脊柱": "0",
        "颈椎": "1",
        "胸椎": "2",
        "腰椎": "3",
        "骶椎": "4",
        "椎间盘": "J",
        "椎体": "H",
        "椎板": "K",
        "颅骨": "0",
        "额骨": "1",
        "顶骨": "3",
        "颞骨": "2",
        "枕骨": "4",
        "下颌骨": "7",
        "上颌骨": "6",
        "颧骨": "8",
        "锁骨": "B",
        "肩胛骨": "C",
        "肱骨": "D",
        "尺骨": "F",
        "桡骨": "E",
        "腕骨": "G",
        "掌骨": "H",
        "指骨": "J",
        "骨盆": "P",
        "髂骨": "Q",
        "骶骨": "R",
        "股骨": "S",
        "胫骨": "T",
        "腓骨": "U",
        "髌骨": "V",
        "跗骨": "W",
        "跖骨": "X",
        "趾骨": "Y",
        "膝关节": "S",
        "髋关节": "Q",
        "肩关节": "D",
        "肘关节": "D",
        "踝关节": "T",
        "腕关节": "G",
        "皮肤": "0",
        "皮": "0",
        "头皮": "0",
        "面部皮肤": "1",
        "颈部皮肤": "2",
        "躯干皮肤": "3",
        "四肢皮肤": "4",
    }

    APPROACH_MAP = {
        "open": "0",
        "开放": "0",
        "切开": "0",
        "开腹": "0",
        "开胸": "0",
        "开颅": "0",
        "percutaneous": "3",
        "经皮": "3",
        "经皮穿刺": "3",
        "percutaneous_endoscopic": "4",
        "经皮内镜": "4",
        "经皮腔镜": "4",
        "percutaneous_laparoscopic": "4",
        "percutaneous_arthroscopic": "4",
        "laparoscopic": "4",
        "腹腔镜": "4",
        "腔镜": "4",
        "微创": "4",
        "微创手术": "4",
        "arthroscopic": "4",
        "关节镜": "4",
        "endoscopic": "8",
        "内镜": "8",
        "内窥镜": "8",
        "胃镜": "8",
        "肠镜": "8",
        "食管镜": "8",
        "支气管镜": "8",
        "thoracoscopic": "4",
        "胸腔镜": "4",
        "thoracotomy": "0",
        "mediastinoscopic": "4",
        "纵隔镜": "4",
        "hysteroscopic": "8",
        "宫腔镜": "8",
        "cystoscopic": "8",
        "膀胱镜": "8",
        "ureteroscopic": "8",
        "输尿管镜": "8",
        "nephroscopic": "4",
        "经皮肾镜": "4",
        "natural_orifice": "7",
        "经自然腔道": "7",
        "transurethral": "8",
        "经尿道": "8",
        "transoral": "8",
        "经口": "8",
        "transanal": "8",
        "经肛": "8",
        "经肛门": "8",
        "transvaginal": "8",
        "经阴道": "8",
        "external": "X",
        "体外": "X",
        "经皮椎体成形": "3",
    }

    DEVICE_MAP = {
        "none": "Z",
        "无": "Z",
        "stent": "D",
        "支架": "D",
        "金属支架": "D",
        "覆膜支架": "E",
        "prosthesis": "J",
        "假体": "J",
        "人工关节": "J",
        "人工股骨头": "J",
        "全髋关节": "J",
        "全膝关节": "J",
        "mesh": "U",
        "补片": "U",
        "钉板": "4",
        "钢板": "4",
        "钢板螺钉": "4",
        "内固定钢板": "4",
        "screw": "6",
        "螺钉": "6",
        "髓内钉": "B",
        "interlocking_nail": "B",
        "graft_autologous": "7",
        "自体骨": "7",
        "自体植骨": "7",
        "graft_allogeneic": "8",
        "异体骨": "8",
        "异体植骨": "8",
        "synthetic_graft": "K",
        "人工骨": "K",
        "引流管": "0",
        "引流装置": "0",
        "导管": "2",
        "起搏器": "J",
        "pacemaker": "J",
        "人工瓣膜": "J",
        "prosthetic_valve": "J",
        "clip": "3",
        "钛夹": "3",
        "夹": "3",
        "radioactive_brachytherapy": "1",
        "放射性粒子": "1",
        "栓塞材料": "0",
        "embolic": "0",
        "suture": "Z",
        "缝合线": "Z",
        "bone_cement": "3",
        "骨水泥": "3",
        "balloon": "0",
        "球囊": "0",
    }

    QUALIFIER_MAP = {
        "none": "Z",
        "无": "Z",
        "bilateral": "B",
        "双侧": "B",
        "unilateral": "Z",
        "单侧": "Z",
        "left": "L",
        "左": "L",
        "左侧": "L",
        "right": "R",
        "右": "R",
        "右侧": "R",
        "proximal": "1",
        "近端": "1",
        "distal": "2",
        "远端": "2",
        "anterior": "A",
        "前": "A",
        "前路": "A",
        "前侧": "A",
        "posterior": "P",
        "后": "P",
        "后路": "P",
        "后侧": "P",
        "lateral": "Q",
        "侧方": "Q",
        "side": "Q",
        "midline": "M",
        "正中": "M",
        "median": "M",
        "radical": "5",
        "根治": "5",
        "根治性": "5",
        "palliative": "6",
        "姑息": "6",
        "姑息性": "6",
        "open_reduction_internal_fixation": "Z",
        "切开复位内固定": "Z",
        "minimally_invasive": "Z",
        "partial": "Z",
        "部分": "Z",
        "total": "0",
        "全": "0",
        "完全": "0",
        "subtotal": "Z",
        "次全": "Z",
        "radical_prophylactic": "7",
        "预防性": "7",
        "diagnostic": "X",
        "诊断性": "X",
        "therapeutic": "Z",
        "治疗性": "Z",
        "resection_with_anastomosis": "Z",
    }

    OPERATION_SYNONYMS = {
        "切除术": ["切除", "切除术", "根治切除", "根治切除术", "根治性切除", "部分切除", "次全切除", "全切除"],
        "修补术": ["修补", "修补术", "修复", "修复术", "缝合", "缝合术"],
        "成形术": ["成形", "成形术", "整形", "整形术", "重建", "重建术"],
        "置换术": ["置换", "置换术", "替换", "替换术", "更换", "更换术"],
        "吻合术": ["吻合", "吻合术", "端端吻合", "侧侧吻合", "端侧吻合"],
        "造瘘术": ["造瘘", "造瘘术", "造口", "造口术"],
        "切开术": ["切开", "切开术", "开放手术"],
        "结扎术": ["结扎", "结扎术", "缝扎", "缝扎术"],
        "活检术": ["活检", "活检术", "穿刺活检", "切取活检", "切除活检"],
        "探查术": ["探查", "探查术", "检查", "镜检", "内镜检查"],
        "植骨术": ["植骨", "植骨术", "骨移植"],
        "内固定术": ["内固定", "内固定术", "钢板固定", "螺钉固定", "髓内钉固定", "ORIF"],
        "融合术": ["融合", "融合术", "关节融合", "脊柱融合"],
        "取石术": ["取石", "取石术", "碎石取石", "碎石术"],
        "引流术": ["引流", "引流术", "穿刺引流", "切开引流"],
        "搭桥术": ["搭桥", "搭桥术", "旁路手术", "CABG"],
        "消融术": ["消融", "消融术", "射频消融", "冷冻消融"],
        "扩张术": ["扩张", "扩张术", "球囊扩张"],
        "支架置入术": ["支架置入", "支架植入", "支架置入术", "支架植入术", "stent"],
        "截肢术": ["截肢", "截肢术", "截指", "截趾"],
        "松解术": ["松解", "松解术", "粘连松解", "关节松解"],
        "复位术": ["复位", "复位术", "手法复位", "切开复位"],
        "清创术": ["清创", "清创术", "扩创", "扩创术"],
        "植皮术": ["植皮", "植皮术", "皮片移植", "皮瓣移植"],
        "栓塞术": ["栓塞", "栓塞术", "介入栓塞"],
        "止血术": ["止血", "止血术", "电凝止血", "压迫止血"],
        "减压术": ["减压", "减压术", "椎管减压", "切开减压"],
        "固定术": ["固定", "固定术"],
        "穿刺术": ["穿刺", "穿刺术"],
    }

    BODY_PART_KEYWORDS = {
        "胃": ["胃", "胃部", "胃窦", "胃体", "胃底", "贲门", "幽门"],
        "胆囊": ["胆囊", "胆囊窝", "胆总管"],
        "阑尾": ["阑尾", "盲肠末端"],
        "结肠": ["结肠", "升结肠", "横结肠", "降结肠", "乙状结肠"],
        "直肠": ["直肠", "肛管"],
        "小肠": ["小肠", "空肠", "回肠", "十二指肠"],
        "肝脏": ["肝", "肝脏", "肝叶", "肝段"],
        "胰腺": ["胰", "胰腺", "胰头", "胰体", "胰尾"],
        "脾脏": ["脾", "脾脏"],
        "食管": ["食管", "食道"],
        "肺": ["肺", "肺部", "肺叶", "肺段"],
        "心脏": ["心", "心脏", "心肌", "心室", "心房", "瓣膜"],
        "肾脏": ["肾", "肾脏", "肾盂"],
        "输尿管": ["输尿管"],
        "膀胱": ["膀胱"],
        "前列腺": ["前列腺"],
        "子宫": ["子宫", "宫体", "宫颈"],
        "卵巢": ["卵巢"],
        "乳腺": ["乳腺", "乳房"],
        "甲状腺": ["甲状腺", "甲状腺叶"],
        "膝关节": ["膝", "膝关节", "髌骨", "半月板", "交叉韧带"],
        "髋关节": ["髋", "髋关节", "股骨头", "髋臼"],
        "肩关节": ["肩", "肩关节", "肱骨头", "肩袖"],
        "脊柱": ["脊柱", "椎", "椎体", "椎间盘", "椎板", "椎管"],
        "腰椎": ["腰", "腰椎", "L1", "L2", "L3", "L4", "L5"],
        "颈椎": ["颈", "颈椎", "C1", "C2", "C3", "C4", "C5", "C6", "C7"],
        "胸椎": ["胸", "胸椎", "T1", "T2", "T3", "T4", "T5", "T6", "T7", "T8", "T9", "T10", "T11", "T12"],
        "股骨": ["股骨", "股骨干"],
        "胫骨": ["胫骨", "胫骨干"],
        "肱骨": ["肱骨", "肱骨干"],
        "桡骨": ["桡骨"],
        "尺骨": ["尺骨"],
        "颅骨": ["颅", "颅骨", "头皮", "颅脑"],
        "脑": ["脑", "大脑", "小脑", "脑干", "颅内"],
        "皮肤": ["皮", "皮肤", "表皮"],
        "主动脉": ["主动脉", "升主动脉", "降主动脉", "腹主动脉"],
        "颈动脉": ["颈动脉", "颈总动脉"],
        "冠状动脉": ["冠脉", "冠状动脉"],
    }


class Icd10PcsDecisionTree:
    def __init__(self, kb: Icd10PcsKnowledgeBase):
        self.kb = kb
        self.root_nodes = self._build_tree()

    def _build_tree(self) -> List[DecisionTreeNode]:
        root = DecisionTreeNode(
            node_id="root",
            field="all",
            operator="exists",
            value=True,
            rule_name="入口"
        )

        for op_key, op_variants in self.kb.OPERATION_SYNONYMS.items():
            op_node = DecisionTreeNode(
                node_id=f"op_{op_key}",
                field="surgery_name",
                operator="contains_any",
                value=op_variants,
                pcs_component={"root_operation": self.kb.ROOT_OPERATION_MAP.get(op_key, "Z")},
                rule_name=f"手术方式识别:{op_key}"
            )

            for part_key, part_keywords in self.kb.BODY_PART_KEYWORDS.items():
                part_node = DecisionTreeNode(
                    node_id=f"op_{op_key}_part_{part_key}",
                    field="body_part",
                    operator="contains_any",
                    value=part_keywords,
                    pcs_component={"body_part": self.kb.BODY_PART_MAP.get(part_key, "Z")},
                    rule_name=f"手术部位识别:{part_key}"
                )

                approach_map = self._build_approach_children(part_key)
                part_node.children.extend(approach_map)

                op_node.children.append(part_node)

            root.children.append(op_node)

        return [root]

    def _build_approach_children(self, body_part_key: str) -> List[DecisionTreeNode]:
        children = []
        approach_rules = [
            ("经皮内镜", ["经皮内镜", "经皮腔镜", "经皮肾镜", "经皮椎体成形"], "percutaneous_endoscopic"),
            ("腹腔镜/关节镜/胸腔镜", ["腹腔镜", "胸腔镜", "关节镜", "纵隔镜", "微创", "微创手术"], "laparoscopic"),
            ("内镜", ["内镜", "内窥镜", "胃镜", "肠镜", "膀胱镜", "输尿管镜", "宫腔镜", "食管镜", "支气管镜"], "endoscopic"),
            ("经尿道", ["经尿道"], "transurethral"),
            ("经口", ["经口"], "transoral"),
            ("经肛", ["经肛", "经肛门"], "transanal"),
            ("经阴道", ["经阴道"], "transvaginal"),
            ("经自然腔道", ["经自然腔道", "NOTES"], "natural_orifice"),
            ("经皮穿刺", ["经皮穿刺", "穿刺", "PTC"], "percutaneous"),
            ("体外", ["体外"], "external"),
            ("开放", ["开放", "切开", "开腹", "开胸", "开颅", "根治", "根治性", "ORIF"], "open"),
        ]
        for rule_name, keywords, approach_key in approach_rules:
            child = DecisionTreeNode(
                node_id=f"approach_{body_part_key}_{approach_key}",
                field="approach",
                operator="contains_any",
                value=keywords,
                pcs_component={"approach": self.kb.APPROACH_MAP.get(approach_key, "Z")},
                rule_name=f"入路识别:{rule_name}"
            )
            children.append(child)
        return children


class Icd10PcsCodingService:
    def __init__(self):
        self.kb = Icd10PcsKnowledgeBase()
        self.tree = Icd10PcsDecisionTree(self.kb)
        self._confirmation_history: Dict[str, List[Dict[str, Any]]] = {}
        logger.info("ICD-10-PCS手术编码服务初始化完成")

    def parse_entities(self, entities: List[Dict[str, Any]]) -> Dict[str, Any]:
        result = {
            "surgery_name": "",
            "body_part": [],
            "approach": [],
            "instruments": [],
            "devices": [],
            "qualifiers": [],
            "side": "",
            "surgery_names_raw": [],
            "body_parts_raw": [],
        }

        for entity in entities:
            etype = entity.get("entity_type", "")
            evalue = entity.get("entity_value", "")
            if not evalue:
                continue

            if etype == "SURGERY_NAME":
                result["surgery_name"] += " " + evalue
                result["surgery_names_raw"].append(evalue)
            elif etype in ("BODY_PART", "SURGERY_BODY_PART", "ANATOMY", "ANATOMICAL_SITE"):
                result["body_part"].append(evalue)
                result["body_parts_raw"].append(evalue)
            elif etype in ("SURGERY_APPROACH", "APPROACH", "SURGICAL_APPROACH"):
                result["approach"].append(evalue)
            elif etype in ("INSTRUMENT", "SURGERY_INSTRUMENT", "DEVICE", "IMPLANT"):
                result["instruments"].append(evalue)
            elif etype in ("SURGERY_LEVEL", "SURGERY_SCOPE", "LATERALITY"):
                result["qualifiers"].append(evalue)

        result["surgery_name"] = result["surgery_name"].strip()

        if not result["approach"]:
            approach_keywords = []
            for kw_list in self.kb.APPROACH_MAP.keys():
                approach_keywords.append(kw_list)
            for raw in [result["surgery_name"]] + result["body_part"]:
                for ak, av in self.kb.APPROACH_MAP.items():
                    if ak in raw:
                        result["approach"].append(ak)

        side_match = re.search(r"(左|右|双|单)(侧)?", result["surgery_name"])
        if side_match:
            result["side"] = side_match.group(0)

        for raw in [result["surgery_name"]] + result["surgery_names_raw"]:
            for part_key, keywords in self.kb.BODY_PART_KEYWORDS.items():
                if any(kw in raw for kw in keywords):
                    if part_key not in result["body_part"]:
                        result["body_part"].append(part_key)

        return result

    def recommend_codes(self,
                        entities: List[Dict[str, Any]],
                        record_id: Optional[int] = None,
                        top_k: int = 5) -> Dict[str, Any]:
        start_time = __import__("time").time()

        try:
            parsed = self.parse_entities(entities)
            recommendations = self._match_decision_tree(parsed, top_k)

            elapsed_ms = int((__import__("time").time() - start_time) * 1000)
            return {
                "success": True,
                "parsed_entities": parsed,
                "recommendations": [self._rec_to_dict(r) for r in recommendations],
                "top_code": self._rec_to_dict(recommendations[0]) if recommendations else None,
                "processing_time_ms": elapsed_ms,
                "error_message": None,
            }
        except Exception as e:
            logger.error(f"ICD-10-PCS编码推荐失败: {e}", exc_info=True)
            elapsed_ms = int((__import__("time").time() - start_time) * 1000)
            return {
                "success": False,
                "parsed_entities": {},
                "recommendations": [],
                "top_code": None,
                "processing_time_ms": elapsed_ms,
                "error_message": str(e),
            }

    def _match_decision_tree(self, parsed: Dict[str, Any], top_k: int) -> List[PcsCodeRecommendation]:
        candidates = []

        def _walk(node: DecisionTreeNode,
                  context: Dict[str, Any],
                  components: PcsCodeComponent,
                  match_path: List[str],
                  matched_rules: List[str],
                  confidence: float):
            node_match, node_score = self._evaluate_node(node, parsed, context)
            if not node_match:
                return

            new_confidence = round(confidence * node_score, 4)
            new_path = match_path + [node.node_id]
            new_rules = matched_rules + ([node.rule_name] if node.rule_name else [])

            if node.pcs_component:
                for k, v in node.pcs_component.items():
                    if hasattr(components, k):
                        if not getattr(components, k):
                            setattr(components, k, v)

            if not node.children:
                candidates.append(PcsCodeRecommendation(
                    pcs_code="",
                    code_components=PcsCodeComponent(
                        section=components.section or "0",
                        body_system=components.body_system or "",
                        root_operation=components.root_operation or "",
                        body_part=components.body_part or "",
                        approach=components.approach or "",
                        device=components.device or "Z",
                        qualifier=components.qualifier or "Z",
                    ),
                    description="",
                    confidence=new_confidence,
                    match_path=new_path,
                    matched_rules=new_rules,
                    missing_fields=[],
                    is_complete=False,
                ))
                return

            for child in node.children:
                _walk(child, context, PcsCodeComponent(
                    section=components.section,
                    body_system=components.body_system,
                    root_operation=components.root_operation,
                    body_part=components.body_part,
                    approach=components.approach,
                    device=components.device,
                    qualifier=components.qualifier,
                ), new_path, new_rules, new_confidence)

        for root in self.tree.root_nodes:
            _walk(root, parsed, PcsCodeComponent(section="0"), [], [], 1.0)

        candidates = self._finalize_candidates(candidates, parsed)
        candidates.sort(key=lambda r: -r.confidence)
        return candidates[:top_k]

    def _evaluate_node(self, node: DecisionTreeNode,
                       parsed: Dict[str, Any],
                       context: Dict[str, Any]) -> Tuple[bool, float]:
        field = node.field
        operator = node.operator
        target = node.value

        if field == "all" and operator == "exists":
            return True, 1.0

        field_value = ""
        if field == "surgery_name":
            field_value = parsed.get("surgery_name", "")
        elif field == "body_part":
            field_value = " ".join(parsed.get("body_part", []))
            if not field_value:
                field_value = parsed.get("surgery_name", "")
        elif field == "approach":
            field_value = " ".join(parsed.get("approach", []))
            if not field_value:
                field_value = parsed.get("surgery_name", "")

        if operator == "contains_any":
            matched = [kw for kw in target if kw in field_value]
            if matched:
                score = min(0.6 + 0.1 * len(matched), 1.0)
                return True, score
            return False, 0.0

        if operator == "exact_match":
            if field_value == target:
                return True, 1.0
            return False, 0.0

        if operator == "regex_match":
            if re.search(target, field_value):
                return True, 0.9
            return False, 0.0

        return False, 0.0

    def _finalize_candidates(self, candidates: List[PcsCodeRecommendation],
                             parsed: Dict[str, Any]) -> List[PcsCodeRecommendation]:
        finalized = []
        seen_codes = set()

        for cand in candidates:
            comp = cand.code_components

            if not comp.root_operation:
                for op_key, variants in self.kb.OPERATION_SYNONYMS.items():
                    if any(v in parsed.get("surgery_name", "") for v in variants):
                        comp.root_operation = self.kb.ROOT_OPERATION_MAP.get(op_key, "Z")
                        cand.matched_rules.append(f"兜底识别:{op_key}")
                        cand.confidence = round(cand.confidence * 0.7, 4)
                        break

            if not comp.body_part:
                for part_key, kws in self.kb.BODY_PART_KEYWORMS.items():
                    search_space = " ".join(parsed.get("body_part", []) + [parsed.get("surgery_name", "")])
                    if any(kw in search_space for kw in kws):
                        comp.body_part = self.kb.BODY_PART_MAP.get(part_key, "Z")
                        comp.body_system = self._infer_body_system(part_key)
                        cand.matched_rules.append(f"兜底部位:{part_key}")
                        cand.confidence = round(cand.confidence * 0.8, 4)
                        break

            if not comp.approach:
                comp.approach = self.kb.APPROACH_MAP.get("open", "0")
                cand.matched_rules.append("默认入路:开放")
                cand.confidence = round(cand.confidence * 0.6, 4)

            if not comp.body_system:
                body_parts = parsed.get("body_part", [])
                search_name = parsed.get("surgery_name", "")
                for bs_key, bs_val in self.kb.BODY_SYSTEM_MAP.items():
                    if bs_key in search_name or any(bs_key in bp for bp in body_parts):
                        comp.body_system = bs_val
                        break
                if not comp.body_system:
                    comp.body_system = "0"

            if not comp.device:
                device_kws = self.kb.DEVICE_MAP
                instrs = " ".join(parsed.get("instruments", []))
                surgery_name = parsed.get("surgery_name", "")
                dev_search = f"{instrs} {surgery_name}"
                for dev_key, dev_val in device_kws.items():
                    if dev_key in dev_search:
                        comp.device = dev_val
                        break
                if not comp.device:
                    comp.device = "Z"

            if not comp.qualifier:
                side = parsed.get("side", "")
                if side:
                    if "左" in side:
                        comp.qualifier = self.kb.QUALIFIER_MAP.get("left", "L")
                    elif "右" in side:
                        comp.qualifier = self.kb.QUALIFIER_MAP.get("right", "R")
                    elif "双" in side:
                        comp.qualifier = self.kb.QUALIFIER_MAP.get("bilateral", "B")
                else:
                    comp.qualifier = "Z"

            code = f"{comp.section}{comp.body_system}{comp.root_operation}{comp.body_part}{comp.approach}{comp.device}{comp.qualifier}"

            if code in seen_codes:
                continue
            seen_codes.add(code)

            cand.pcs_code = code
            cand.description = self._describe_code(comp)

            missing = []
            if not comp.root_operation or comp.root_operation == "Z":
                missing.append("root_operation")
            if not comp.body_part or comp.body_part == "Z":
                missing.append("body_part")
            if not comp.approach or comp.approach == "Z":
                missing.append("approach")
            cand.missing_fields = missing
            cand.is_complete = len(missing) == 0

            finalized.append(cand)

        if not finalized:
            fallback = PcsCodeRecommendation(
                pcs_code="0ZZ00ZZ",
                code_components=PcsCodeComponent(
                    section="0", body_system="Z", root_operation="Z",
                    body_part="0", approach="0", device="Z", qualifier="Z"
                ),
                description="无法匹配的手术，请人工核对编码",
                confidence=0.2,
                match_path=["fallback"],
                matched_rules=["兜底默认编码"],
                missing_fields=["root_operation", "body_part", "approach"],
                is_complete=False,
            )
            finalized.append(fallback)

        return finalized

    def _infer_body_system(self, body_part_key: str) -> str:
        system_map = {
            "胃": "D", "胆囊": "F", "阑尾": "D", "结肠": "D", "直肠": "D", "小肠": "D",
            "肝脏": "F", "胰腺": "F", "脾脏": "7", "食管": "D",
            "肺": "4", "心脏": "5",
            "肾脏": "T", "输尿管": "T", "膀胱": "T", "前列腺": "V",
            "子宫": "U", "卵巢": "U", "乳腺": "0",
            "甲状腺": "2",
            "膝关节": "K", "髋关节": "K", "肩关节": "K",
            "脊柱": "K", "腰椎": "K", "颈椎": "K", "胸椎": "K",
            "股骨": "K", "胫骨": "K", "肱骨": "K", "桡骨": "K", "尺骨": "K",
            "颅骨": "0", "脑": "0",
            "皮肤": "H",
            "主动脉": "5", "颈动脉": "5", "冠状动脉": "5",
        }
        return system_map.get(body_part_key, "0")

    def _describe_code(self, comp: PcsCodeComponent) -> str:
        sec_desc = self.kb.SECTION_MAP.get(comp.section, "未知章节")
        op_desc = ""
        for k, v in self.kb.ROOT_OPERATION_MAP.items():
            if v == comp.root_operation:
                op_desc = k
                break
        part_desc = ""
        for k, v in self.kb.BODY_PART_MAP.items():
            if v == comp.body_part:
                part_desc = k
                break
        app_desc = ""
        for k, v in self.kb.APPROACH_MAP.items():
            if v == comp.approach:
                app_desc = k
                break
        dev_desc = ""
        for k, v in self.kb.DEVICE_MAP.items():
            if v == comp.device:
                dev_desc = k
                break
        qual_desc = ""
        for k, v in self.kb.QUALIFIER_MAP.items():
            if v == comp.qualifier:
                qual_desc = k
                break

        parts = [sec_desc]
        if op_desc:
            parts.append(f"操作:{op_desc}")
        if part_desc:
            parts.append(f"部位:{part_desc}")
        if app_desc:
            parts.append(f"入路:{app_desc}")
        if dev_desc and dev_desc not in ("none", "无"):
            parts.append(f"器械/装置:{dev_desc}")
        if qual_desc and qual_desc not in ("none", "无"):
            parts.append(f"修饰符:{qual_desc}")

        return " | ".join(parts)

    def _rec_to_dict(self, rec: PcsCodeRecommendation) -> Dict[str, Any]:
        return {
            "pcs_code": rec.pcs_code,
            "code_components": {
                "section": rec.code_components.section,
                "body_system": rec.code_components.body_system,
                "root_operation": rec.code_components.root_operation,
                "body_part": rec.code_components.body_part,
                "approach": rec.code_components.approach,
                "device": rec.code_components.device,
                "qualifier": rec.code_components.qualifier,
            },
            "description": rec.description,
            "confidence": rec.confidence,
            "match_path": rec.match_path,
            "matched_rules": rec.matched_rules,
            "missing_fields": rec.missing_fields,
            "is_complete": rec.is_complete,
        }

    def confirm_code(self, record_id: int, pcs_code: str, user_id: Optional[str] = None) -> Dict[str, Any]:
        try:
            key = str(record_id)
            if key not in self._confirmation_history:
                self._confirmation_history[key] = []

            entry = {
                "record_id": record_id,
                "pcs_code": pcs_code,
                "confirmed_at": __import__("time").time(),
                "confirmed_by": user_id or "system",
                "source": "manual_confirm",
            }
            self._confirmation_history[key].append(entry)

            logger.info(f"手术编码确认: record_id={record_id}, pcs_code={pcs_code}")
            return {"success": True, "confirmation": entry, "error_message": None}
        except Exception as e:
            logger.error(f"编码确认失败: {e}", exc_info=True)
            return {"success": False, "confirmation": None, "error_message": str(e)}

    def get_coding_history(self, record_id: Optional[int] = None,
                           limit: int = 100) -> Dict[str, Any]:
        try:
            if record_id is not None:
                key = str(record_id)
                history = self._confirmation_history.get(key, [])
            else:
                history = []
                for rec_list in self._confirmation_history.values():
                    history.extend(rec_list)
            history.sort(key=lambda x: -x.get("confirmed_at", 0))
            return {"success": True, "history": history[:limit], "total": len(history), "error_message": None}
        except Exception as e:
            logger.error(f"获取编码历史失败: {e}", exc_info=True)
            return {"success": False, "history": [], "total": 0, "error_message": str(e)}

    def get_coding_knowledge(self) -> Dict[str, Any]:
        return {
            "success": True,
            "sections": self.kb.SECTION_MAP,
            "root_operations": self.kb.ROOT_OPERATION_MAP,
            "body_parts": self.kb.BODY_PART_MAP,
            "approaches": self.kb.APPROACH_MAP,
            "devices": self.kb.DEVICE_MAP,
            "qualifiers": self.kb.QUALIFIER_MAP,
            "operation_synonyms": self.kb.OPERATION_SYNONYMS,
            "body_part_keywords": self.kb.BODY_PART_KEYWORDS,
        }


_icd10pcs_service: Optional[Icd10PcsCodingService] = None


def get_icd10pcs_service() -> Icd10PcsCodingService:
    global _icd10pcs_service
    if _icd10pcs_service is None:
        _icd10pcs_service = Icd10PcsCodingService()
    return _icd10pcs_service
