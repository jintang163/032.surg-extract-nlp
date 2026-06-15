package com.surg.extract.types;

import java.util.HashMap;
import java.util.Map;

public enum EntityType {
    PATIENT_NAME("患者姓名"),
    PATIENT_ID("患者ID"),
    HOSPITAL_NO("住院号"),
    GENDER("性别"),
    PATIENT_GENDER("性别"),
    AGE("年龄"),
    PATIENT_AGE("年龄"),
    DEPARTMENT("科室"),
    BED_NO("床号"),
    ADMISSION_DATE("入院日期"),
    DISCHARGE_DATE("出院日期"),

    SURGERY_DATE("手术日期"),
    SURGERY_NAME("手术名称"),
    SURGERY_CODE("手术编码"),
    ICD_CODE("ICD编码"),
    SURGERY_BODY_PART("手术部位"),
    BODY_PART("身体部位"),
    ANATOMY("解剖结构"),
    ANATOMICAL_SITE("解剖部位"),
    SURGERY_APPROACH("手术入路"),
    APPROACH("入路"),
    SURGICAL_APPROACH("外科入路"),
    SURGERY_LEVEL("手术等级"),
    SURGERY_SCOPE("手术范围"),
    LATERALITY("侧别"),

    INCISION_LEVEL("切口等级"),
    INCISION_HEALING("切口愈合"),
    ANESTHESIA_TYPE("麻醉方式"),
    ANESTHESIA_CODE("麻醉编码"),
    BLOOD_LOSS("失血量"),
    BLOOD_TRANSFUSION("输血量"),
    FLUID_INFUSION("输液量"),
    URINE_OUTPUT("尿量"),
    COMPLICATION("术中并发症"),

    SURGEON("手术医生"),
    CHIEF_SURGEON("主刀医生"),
    ASSISTANT("助手"),
    ASSISTANT1("一助"),
    ASSISTANT2("二助"),
    ANESTHESIOLOGIST("麻醉医生"),
    SCRUB_NURSE("器械护士"),
    CIRCULATING_NURSE("巡回护士"),

    PREOP_DIAGNOSIS("术前诊断"),
    POSTOP_DIAGNOSIS("术后诊断"),
    ADMISSION_DIAGNOSIS("入院诊断"),
    DISCHARGE_DIAGNOSIS("出院诊断"),
    DIAGNOSIS("诊断"),
    DIAGNOSIS_CODE("诊断编码"),

    INSTRUMENT("手术器械"),
    SURGERY_INSTRUMENT("手术器械"),
    DEVICE("植入物/装置"),
    IMPLANT("植入物"),
    SURGICAL_MATERIAL("手术材料"),

    HOSPITAL_FEE("住院总费用"),
    SURGERY_FEE("手术费"),
    ANESTHESIA_FEE("麻醉费"),
    DRUG_FEE("药品费"),
    EXAM_FEE("检查费"),
    TREATMENT_FEE("治疗费"),
    BED_FEE("床位费"),
    OTHER_FEE("其他费用"),

    TEXT("原文"),
    NARRATIVE("叙述文本");

    private final String label;

    EntityType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public static Map<String, String> getLabelMap() {
        Map<String, String> map = new HashMap<>();
        for (EntityType e : values()) {
            map.put(e.name(), e.label);
        }
        return map;
    }

    public static String fromCode(String code) {
        if (code == null) return null;
        for (EntityType e : values()) {
            if (e.name().equalsIgnoreCase(code)) {
                return e.label;
            }
        }
        return null;
    }

    public static EntityType safeValueOf(String name) {
        if (name == null) return null;
        try {
            return valueOf(name);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
