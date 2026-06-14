package com.surg.extract.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class StandardHomePageDTO {

    private String patientId;

    private String patientName;

    private String gender;

    private Integer age;

    private String idCardNo;

    private String hospitalNo;

    private LocalDate admissionDate;

    private LocalDate dischargeDate;

    private Integer admissionDays;

    private String department;

    private String bedNo;

    private String admissionDiagnosis;

    private String dischargeDiagnosis;

    private LocalDateTime surgeryDate;

    private String surgeryName;

    private String surgeryCode;

    private String surgeryLevel;

    private String incisionLevel;

    private String incisionHealing;

    private String anesthesiaType;

    private String anesthesiaCode;

    private BigDecimal bloodLoss;

    private String bloodLossUnit;

    private BigDecimal bloodTransfusion;

    private String bloodTransfusionUnit;

    private BigDecimal fluidInfusion;

    private String fluidInfusionUnit;

    private String surgeon;

    private String chiefSurgeon;

    private String assistant1;

    private String assistant2;

    private String anesthesiologist;

    private String scrubNurse;

    private String circulatingNurse;

    private Integer criticalPatient;

    private BigDecimal hospitalizationFee;

    private BigDecimal surgeryFee;

    private BigDecimal anesthesiaFee;

    private BigDecimal drugFee;

    private BigDecimal examFee;

    private BigDecimal treatmentFee;

    private BigDecimal bedFee;

    private BigDecimal otherFee;

    private String status;

    private String recordNo;

    private LocalDateTime extractTime;
}
