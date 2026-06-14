package com.surg.extract.dto;

import lombok.Data;

@Data
public class ExportFieldConfigDTO {

    private String fieldCode;

    private String fieldLabel;

    private String sourceTable;

    private String sourceField;

    private String fhirPath;

    private String dataType;

    private String unit;

    private String targetUnit;

    private String conversionFormula;

    private Integer sortOrder;

    private Integer enabled;

    private Integer required;

    private String defaultValue;

    private String valueMapping;
}
