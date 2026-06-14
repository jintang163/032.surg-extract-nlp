package com.surg.extract.dto;

import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;

@Data
public class DoctorFeedbackStatsDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long feedbackUserId;

    private String feedbackUserName;

    private String department;

    private Long feedbackCount;

    private Long correctionCount;

    private BigDecimal avgQualityScore;

    private BigDecimal contributionScore;
}
