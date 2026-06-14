package com.surg.extract.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "采纳历史典型值并回写到抽取结果的请求")
public class AdoptTypicalValueRequestDTO {

    @Schema(description = "字段键名: bloodLoss/bloodTransfusion/fluidInfusion/urineOutput/incisionLevel/incisionHealing/surgeryLevel/anesthesiaType", required = true)
    private String fieldKey;

    @Schema(description = "字段类型: NUMERIC / CATEGORY", required = true)
    private String fieldType;

    @Schema(description = "采纳的值（文本形式，例如 '200' 或 'II级'）", required = true)
    private String adoptedValue;

    @Schema(description = "单位（仅数值型），如 ml")
    private String unit;
}
