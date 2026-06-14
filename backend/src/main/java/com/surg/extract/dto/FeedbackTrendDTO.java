package com.surg.extract.dto;

import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;

@Data
public class FeedbackTrendDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String date;

    private String department;

    private Long feedbackCount;

    private Long correctionCount;

    private Long additionCount;

    private Long deletionCount;

    private Long usedForTrainingCount;

    private BigDecimal avgQualityScore;

    private Long activeDoctorCount;
}
