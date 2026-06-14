package com.surg.extract.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Schema(description = "相似病例检索结果")
public class SimilarCaseResultDTO {

    @Schema(description = "记录ID")
    private Long recordId;

    @Schema(description = "记录编号")
    private String recordNo;

    @Schema(description = "相似度得分 0-1")
    private Double score;

    @Schema(description = "科室")
    private String department;

    @Schema(description = "手术名称")
    private String surgeryName;

    @Schema(description = "术前诊断")
    private String preopDiagnosis;

    @Schema(description = "术后诊断")
    private String postopDiagnosis;

    @Schema(description = "手术等级")
    private String surgeryLevel;

    @Schema(description = "切口等级")
    private String incisionLevel;

    @Schema(description = "麻醉方式")
    private String anesthesiaType;

    @Schema(description = "失血量(ml)")
    private BigDecimal bloodLoss;

    @Schema(description = "输血量(ml)")
    private BigDecimal bloodTransfusion;

    @Schema(description = "输液量(ml)")
    private BigDecimal fluidInfusion;

    @Schema(description = "手术医生")
    private String surgeon;

    @Schema(description = "手术日期")
    private LocalDateTime surgeryDate;

    @Schema(description = "上传时间")
    private LocalDateTime uploadTime;
}
