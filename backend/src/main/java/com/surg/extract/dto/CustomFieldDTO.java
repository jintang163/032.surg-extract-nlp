package com.surg.extract.dto;

import lombok.Data;
import java.util.List;

@Data
public class CustomFieldDTO {

    private Long id;

    private String department;

    private String fieldCode;

    private String fieldName;

    private String fieldType;

    private String unit;

    private List<String> enumOptions;

    private String description;

    private Integer sortOrder;

    private Integer required;

    private Integer nerEnabled;

    private String modelStatus;

    private String modelVersion;

    private String lastTrainTime;

    private Integer sampleCount;

    private Integer enabled;
}
