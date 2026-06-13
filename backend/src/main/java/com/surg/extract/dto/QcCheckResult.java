package com.surg.extract.dto;

import lombok.Data;
import java.util.ArrayList;
import java.util.List;

@Data
public class QcCheckResult {

    private List<QcViolation> violations = new ArrayList<>();
    private int totalChecks = 0;
    private int passedChecks = 0;
    private int failedChecks = 0;

    public void addViolation(QcViolation violation) {
        this.violations.add(violation);
        this.failedChecks++;
    }

    public void addPass() {
        this.passedChecks++;
    }

    public void incrementTotal() {
        this.totalChecks++;
    }

    public boolean isPassed() {
        return violations.isEmpty();
    }

    public double getPassRate() {
        return totalChecks > 0 ? (double) passedChecks / totalChecks * 100 : 0;
    }

    @Data
    public static class QcViolation {
        private String ruleCode;
        private String ruleName;
        private String category;
        private String severity;
        private String message;
        private List<String> relatedFields;

        public QcViolation() {}

        public QcViolation(String ruleCode, String ruleName, String category,
                           String severity, String message, List<String> relatedFields) {
            this.ruleCode = ruleCode;
            this.ruleName = ruleName;
            this.category = category;
            this.severity = severity;
            this.message = message;
            this.relatedFields = relatedFields;
        }
    }
}
