package com.surg.extract.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class QcCheckFact {

    private Long recordId;
    private String patientName;
    private String gender;
    private Integer age;
    private String hospitalNo;
    private String department;
    private String surgeryDate;
    private String surgeryName;
    private String surgeryLevel;
    private String incisionLevel;
    private String incisionHealing;
    private String anesthesiaType;
    private BigDecimal bloodLoss;
    private BigDecimal bloodTransfusion;
    private BigDecimal fluidInfusion;
    private String complications;
    private String surgeon;
    private String chiefSurgeon;
    private String assistant1;
    private String assistant2;
    private String anesthesiologist;
    private String scrubNurse;
    private String circulatingNurse;
    private Integer criticalPatient;
    private String admissionDiagnosis;
    private String dischargeDiagnosis;
    private String bedNo;
    private String admissionDate;
}
