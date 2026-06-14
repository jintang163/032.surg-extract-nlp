package com.surg.extract.types;

import java.util.HashMap;
import java.util.Map;

public enum EntityType {
    PATIENT_NAME("患者姓名"),
    HOSPITAL_NO("住院号"),
    GENDER("性别"),
    AGE("年龄"),
    DEPARTMENT("科室"),
    SURGERY_DATE("手术日期"),
    SURGERY_NAME("手术名称"),
    SURGERY_LEVEL("手术等级"),
    INCISION_LEVEL("切口等级"),
    INCISION_HEALING("切口愈合"),
    ANESTHESIA_TYPE("麻醉方式"),
    BLOOD_LOSS("失血量"),
    BLOOD_TRANSFUSION("输血量"),
    FLUID_INFUSION("输液量"),
    URINE_OUTPUT("尿量"),
    COMPLICATION("术中并发症"),
    SURGEON("手术医生"),
    ASSISTANT("助手"),
    ANESTHESIOLOGIST("麻醉医生"),
    SCRUB_NURSE("器械护士"),
    CIRCULATING_NURSE("巡回护士"),
    PREOP_DIAGNOSIS("术前诊断"),
    POSTOP_DIAGNOSIS("术后诊断"),
    BED_NO("床号"),
    ADMISSION_DATE("入院日期");

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
}
