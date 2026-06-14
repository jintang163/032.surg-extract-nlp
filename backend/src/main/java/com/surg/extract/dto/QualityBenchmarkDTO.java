package com.surg.extract.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class QualityBenchmarkDTO {
    private Long id;
    private String indicatorCode;
    private String indicatorName;
    private String indicatorCategory;
    private String unit;
    private BigDecimal benchmarkValue;
    private BigDecimal warningThreshold;
    private BigDecimal criticalThreshold;
    private String direction;
    private String directionLabel;
    private String source;
    private String region;
    private String department;
    private Integer benchmarkYear;
    private Integer benchmarkQuarter;
    private String description;
    private BigDecimal sortOrder;
    private Integer enabled;
    private String createUserName;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
}
