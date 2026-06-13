package com.surg.extract.dto;

import lombok.Data;
import java.util.List;

@Data
public class QcScorecardDTO {

    private Long recordId;
    private String recordNo;
    private String patientName;
    private String surgeryName;
    private double completenessScore;
    private double logicConsistencyScore;
    private double overallScore;
    private String grade;
    private List<FieldCheck> fieldChecks;
    private List<QcCheckResult.QcViolation> violations;
    private int totalFields;
    private int filledFields;
    private int requiredFields;
    private int requiredFilled;
    private int logicRuleCount;
    private int logicPassed;
    private int logicFailed;

    @Data
    public static class FieldCheck {
        private String fieldName;
        private String fieldLabel;
        private boolean filled;
        private boolean required;
        private boolean valid;
        private String issue;
    }
}
