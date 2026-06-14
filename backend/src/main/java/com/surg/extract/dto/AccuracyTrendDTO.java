package com.surg.extract.dto;

import lombok.Data;

@Data
public class AccuracyTrendDTO {
    private String date;
    private String department;
    private String entityType;
    private Integer totalEntities;
    private Integer verifiedEntities;
    private Integer highConfidenceEntities;
    private Double accuracyRate;
}
