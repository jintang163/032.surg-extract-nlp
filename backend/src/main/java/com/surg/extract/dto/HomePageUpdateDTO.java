package com.surg.extract.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class HomePageUpdateDTO {

    private Long recordId;

    private String patientName;

    private String gender;

    private Integer age;

    private String hospitalNo;

    private LocalDate admissionDate;

    private LocalDate dischargeDate;

    private String department;

    private LocalDateTime surgeryDate;

    private String surgeryName;

    private String surgeryLevel;

    private String incisionLevel;

    private String incisionHealing;

    private String anesthesiaType;

    private BigDecimal bloodLoss;

    private BigDecimal bloodTransfusion;

    private BigDecimal fluidInfusion;

    private List<String> complications;

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

    private String complicationsStr;

    private Integer fillDuration;

    private LocalDateTime fillStartTime;

    private LocalDateTime fillEndTime;

    private String status;
}
