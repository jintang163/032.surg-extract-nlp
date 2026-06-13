package com.surg.extract.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class MedicalTermAliasUpdateDTO {

    @NotNull(message = "ID不能为空")
    private Long id;

    @NotBlank(message = "别名不能为空")
    @Size(max = 255, message = "别名长度不能超过255")
    private String aliasName;

    private String aliasType;

    private BigDecimal similarityScore;

    private Integer enabled;
}
