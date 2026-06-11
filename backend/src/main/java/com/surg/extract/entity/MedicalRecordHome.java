package com.surg.extract.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("medical_record_home")
public class MedicalRecordHome implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long recordId;

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

    private BigDecimal bloodTransfusion;

    private BigDecimal fluidInfusion;

    @TableField(typeHandler = org.apache.ibatis.type.StringTypeHandler.class)
    private String complications;

    private String surgeon;

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

    private Long fillUserId;

    private String fillUserName;

    private LocalDateTime fillStartTime;

    private LocalDateTime fillEndTime;

    private Integer fillDuration;

    private Integer manualDurationEst;

    private String status;

    private Long auditUserId;

    private String auditUserName;

    private LocalDateTime auditTime;

    private String auditRemark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedTime;

    @TableLogic
    private Integer deleted;
}
