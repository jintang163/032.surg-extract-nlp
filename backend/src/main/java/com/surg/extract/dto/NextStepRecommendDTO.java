package com.surg.extract.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "智能推荐下一步文书")
public class NextStepRecommendDTO {

    @Schema(description = "模板ID")
    private Long templateId;

    @Schema(description = "模板编码")
    private String templateCode;

    @Schema(description = "模板名称")
    private String templateName;

    @Schema(description = "文书类型：出院小结/术后医嘱/手术记录/病程记录/麻醉记录/知情同意书/护理记录/查房记录/术前讨论/术后病程/其他文书")
    private String documentType;

    @Schema(description = "模板描述")
    private String description;

    @Schema(description = "模板标签列表")
    private List<String> tags;

    @Schema(description = "适用科室")
    private String department;

    @Schema(description = "适用手术类型")
    private String surgeryType;

    @Schema(description = "历史使用次数")
    private Integer useCount;

    @Schema(description = "是否科室默认模板")
    private Boolean isDefault;

    @Schema(description = "综合推荐得分(0-1)")
    private Double score;

    @Schema(description = "协同过滤得分(基于医生行为序列)")
    private Double collaborativeScore;

    @Schema(description = "内容相似度得分(基于手术/诊断关键词)")
    private Double contentScore;

    @Schema(description = "热度/流行度得分(基于历史使用次数)")
    private Double popularityScore;

    @Schema(description = "推荐排名(从1开始)")
    private Integer rank;

    @Schema(description = "占位符数量(即需要人工确认或补充的字段数)")
    private Integer placeholdersCount;

    @Schema(description = "推荐理由")
    private String recommendedReason;

    @Schema(description = "预计生成并完善需要的分钟数")
    private Integer expectedDurationMinutes;
}
