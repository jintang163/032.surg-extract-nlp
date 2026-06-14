package com.surg.extract.dto;

import lombok.Data;

@Data
public class CoverageTrendDTO {
    private String date;
    private String department;
    private Integer totalRecords;
    private Integer extractedRecords;
    private Double coverageRate;
}
