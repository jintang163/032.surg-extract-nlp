package com.surg.extract.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Schema(description = "相似病例检索请求")
public class SimilarCaseSearchRequestDTO {

    @Schema(description = "当前记录ID（用于排除自身）", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private Long excludeRecordId;

    @Schema(description = "手术名称", requiredMode = Schema.RequiredMode.REQUIRED)
    private String surgeryName;

    @Schema(description = "术前诊断", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String preopDiagnosis;

    @Schema(description = "术后诊断", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String postopDiagnosis;

    @Schema(description = "科室", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String department;

    @Schema(description = "时间范围(月)，默认6个月", requiredMode = Schema.RequiredMode.NOT_REQUIRED, defaultValue = "6")
    private Integer timeRangeMonths = 6;

    @Schema(description = "返回数量，默认10条", requiredMode = Schema.RequiredMode.NOT_REQUIRED, defaultValue = "10")
    private Integer topN = 10;

    @Schema(description = "最小相似度阈值 0-1，默认0.5", requiredMode = Schema.RequiredMode.NOT_REQUIRED, defaultValue = "0.5")
    private Double minScore = 0.5;
}
