package com.surg.extract.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

@Data
public class QualityBenchmarkDashboardDTO {
    private Integer totalIndicators;
    private Integer passedCount;
    private Integer warningCount;
    private Integer criticalCount;
    private BigDecimal overallPassRate;
    private BigDecimal compositeScore;
    private Integer evaluatedDepartments;
    private List<IndicatorDeviationDTO> topDeviations;
    private List<DepartmentRankingDTO> departmentRankings;
}
