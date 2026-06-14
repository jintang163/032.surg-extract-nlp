package com.surg.extract.dto;

import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;

@Data
public class FeedbackOverviewDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long totalFeedbackCount;

    private Long usedForTrainingCount;

    private Long pendingTrainingCount;

    private BigDecimal averageQualityScore;

    private Long correctionCount;

    private Long additionCount;

    private Long deletionCount;

    private Long totalTrainCount;

    private BigDecimal latestF1Score;

    private BigDecimal f1Improvement;

    private Long activeDoctorCount;
}
