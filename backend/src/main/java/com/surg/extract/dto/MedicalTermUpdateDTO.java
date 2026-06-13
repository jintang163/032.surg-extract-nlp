package com.surg.extract.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class MedicalTermUpdateDTO {

    @NotNull(message = "术语ID不能为空")
    private Long id;

    @NotBlank(message = "标准名称不能为空")
    @Size(max = 255, message = "标准名称长度不能超过255")
    private String standardName;

    private Long categoryId;

    @NotBlank(message = "术语类型不能为空")
    private String termType;

    private Long icdId;

    private String icdCode;

    private String icdName;

    private String icdVersion;

    private String definition;

    private BigDecimal confidence;

    private Integer enabled;
}
