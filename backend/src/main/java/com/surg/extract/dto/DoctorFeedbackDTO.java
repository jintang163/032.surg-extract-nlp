package com.surg.extract.dto;

import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class DoctorFeedbackDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private Long recordId;

    private String recordNo;

    private Long entityId;

    private String entityType;

    private String entityTypeLabel;

    private String originalValue;

    private String originalUnit;

    private BigDecimal originalConfidence;

    private String originalSource;

    private String originalSourceLabel;

    private String correctedValue;

    private String correctedUnit;

    private String correctionType;

    private String correctionTypeLabel;

    private String department;

    private Long feedbackUserId;

    private String feedbackUserName;

    private String feedbackRemark;

    private Integer qualityScore;

    private Integer usedForTraining;

    private String trainBatchNo;

    private LocalDateTime createdTime;
}
