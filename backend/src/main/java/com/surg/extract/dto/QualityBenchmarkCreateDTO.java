package com.surg.extract.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class QualityBenchmarkCreateDTO {
    private Long id;
    private String indicatorCode;
    private String indicatorName;
    private String indicatorCategory;
    private String unit;
    private BigDecimal benchmarkValue;
    private BigDecimal warningThreshold;
    private BigDecimal criticalThreshold;
    private String direction;
    private String source;
    private String region;
    private String department;
    private Integer benchmarkYear;
    private Integer benchmarkQuarter;
    private String description;
    private BigDecimal sortOrder;
    private Integer enabled;
}
