package com.surg.extract.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

@Data
public class DepartmentRankingDTO {
    private String department;
    private Integer totalIndicators;
    private Integer passedIndicators;
    private Integer warningIndicators;
    private Integer criticalIndicators;
    private BigDecimal passRate;
    private BigDecimal compositeScore;
    private Integer ranking;
    private List<IndicatorDeviationDTO> indicatorDeviations;
}
