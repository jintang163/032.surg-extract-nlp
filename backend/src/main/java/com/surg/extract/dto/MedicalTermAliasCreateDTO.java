package com.surg.extract.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class MedicalTermAliasCreateDTO {

    @NotNull(message = "标准术语ID不能为空")
    private Long termId;

    @NotBlank(message = "别名不能为空")
    @Size(max = 255, message = "别名长度不能超过255")
    private String aliasName;

    private String aliasType;

    private BigDecimal similarityScore;

    private String source;

    private String reviewStatus;

    private Integer enabled;
}
