package com.surg.extract.dto;

import lombok.Data;

@Data
public class SurgeryTypeStatsDTO {
    private String surgeryName;
    private Integer recordCount;
    private Double coverageRate;
    private Double avgAccuracyRate;
}
