package com.surg.extract.dto;

import lombok.Data;

@Data
public class UnitConversionDTO {

    private String fieldCode;

    private String sourceUnit;

    private String targetUnit;

    private String formula;

    private Double multiplyFactor;

    private Double addOffset;

    private Integer decimalPlaces;
}
