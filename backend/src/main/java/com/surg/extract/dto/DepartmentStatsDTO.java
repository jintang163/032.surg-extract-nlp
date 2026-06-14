package com.surg.extract.dto;

import lombok.Data;

@Data
public class DepartmentStatsDTO {
    private String department;
    private Integer totalRecords;
    private Integer extractedRecords;
    private Double coverageRate;
    private Double avgTimeSavedRate;
    private Double avgAccuracyRate;
}
