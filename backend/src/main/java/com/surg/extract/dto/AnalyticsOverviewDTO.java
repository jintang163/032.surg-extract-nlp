package com.surg.extract.dto;

import lombok.Data;

@Data
public class AnalyticsOverviewDTO {
    private Integer totalRecords;
    private Integer extractedRecords;
    private Double overallCoverageRate;
    private Double overallTimeSavedRate;
    private Double overallAccuracyRate;
    private Integer totalDepartments;
    private Integer totalSurgeons;
}
