package com.surg.extract.dto;

import lombok.Data;

@Data
public class LowConfidenceDistributionDTO {
    private String entityType;
    private String entityLabel;
    private Integer count;
    private Double avgConfidence;
}
