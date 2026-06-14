package com.surg.extract.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
@Schema(description = "历史病例统计分析结果")
public class CaseStatsAnalysisDTO {

    @Schema(description = "查询条件匹配的病例总数")
    private Long totalCases;

    @Schema(description = "统计时间范围描述")
    private String timeRangeDescription;

    @Schema(description = "数值字段统计")
    private Map<String, NumericFieldStats> numericStats;

    @Schema(description = "分类字段统计（枚举分布）")
    private Map<String, List<CategoryBucket>> categoryStats;

    @Schema(description = "差异对比：当前值 vs 历史典型值")
    private Map<String, FieldComparison> fieldComparisons;

    @Data
    @Schema(description = "数值字段统计信息")
    public static class NumericFieldStats {
        @Schema(description = "字段显示名称")
        private String fieldLabel;

        @Schema(description = "样本数量")
        private Long count;

        @Schema(description = "平均值")
        private BigDecimal avg;

        @Schema(description = "中位数")
        private BigDecimal median;

        @Schema(description = "最小值")
        private BigDecimal min;

        @Schema(description = "最大值")
        private BigDecimal max;

        @Schema(description = "标准差")
        private BigDecimal stdDev;

        @Schema(description = "第25百分位")
        private BigDecimal percentile25;

        @Schema(description = "第75百分位")
        private BigDecimal percentile75;

        @Schema(description = "典型区间（25%-75%分位数）")
        private String typicalRange;

        @Schema(description = "单位")
        private String unit;
    }

    @Data
    @Schema(description = "分类分布桶")
    public static class CategoryBucket {
        @Schema(description = "分类值")
        private String value;

        @Schema(description = "数量")
        private Long count;

        @Schema(description = "占比(%)")
        private Double percentage;

        @Schema(description = "是否为最常见值")
        private Boolean isMostFrequent;
    }

    @Data
    @Schema(description = "字段差异对比")
    public static class FieldComparison {
        @Schema(description = "字段类型: NUMERIC / CATEGORY")
        private String fieldType;

        @Schema(description = "字段显示名称")
        private String fieldLabel;

        @Schema(description = "当前提取值")
        private String currentValue;

        @Schema(description = "历史典型值（均值或众数）")
        private String typicalValue;

        @Schema(description = "历史典型区间（仅数值型）")
        private String typicalRange;

        @Schema(description = "偏差方向: HIGHER/LOWER/WITHIN_RANGE/DIFFERENT/UNKNOWN（仅数值型）")
        private String deviationDirection;

        @Schema(description = "偏差百分比（仅数值型）")
        private BigDecimal deviationPercent;

        @Schema(description = "偏差程度: NORMAL/MILD/MODERATE/SEVERE（仅数值型）")
        private String deviationLevel;

        @Schema(description = "单位")
        private String unit;

        @Schema(description = "提示信息")
        private String tip;
    }
}
