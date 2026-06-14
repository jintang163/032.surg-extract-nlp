package com.surg.extract.dto;

import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;

@Data
public class DoctorFeedbackCreateDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long recordId;

    private Long entityId;

    private String entityType;

    private String originalValue;

    private String originalUnit;

    private BigDecimal originalConfidence;

    private String originalSource;

    private Integer originalStartPos;

    private Integer originalEndPos;

    private String originalText;

    private String correctedValue;

    private String correctedUnit;

    private String correctionType;

    private String feedbackRemark;

    private Integer qualityScore;
}
