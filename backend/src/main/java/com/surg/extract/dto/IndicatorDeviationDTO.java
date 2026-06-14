package com.surg.extract.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class IndicatorDeviationDTO {
    private String indicatorCode;
    private String indicatorName;
    private String indicatorCategory;
    private String unit;
    private BigDecimal actualValue;
    private BigDecimal benchmarkValue;
    private BigDecimal warningThreshold;
    private BigDecimal criticalThreshold;
    private String direction;
    private BigDecimal deviationValue;
    private BigDecimal deviationRate;
    private String deviationLevel;
    private String deviationLevelLabel;
    private String department;
    private Integer dataCount;
}
