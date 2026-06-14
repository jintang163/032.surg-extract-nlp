package com.surg.extract.dto;

import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;

@Data
public class EntityFeedbackStatsDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String entityType;

    private String entityTypeLabel;

    private Long feedbackCount;

    private Long correctionCount;

    private Long additionCount;

    private Long deletionCount;

    private BigDecimal avgOriginalConfidence;

    private BigDecimal usedForTrainingRate;
}
