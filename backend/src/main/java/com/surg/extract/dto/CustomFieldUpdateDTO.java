package com.surg.extract.dto;

import lombok.Data;

import java.util.List;

@Data
public class CustomFieldUpdateDTO {

    private String fieldName;

    private String fieldType;

    private String unit;

    private List<String> enumOptions;

    private String description;

    private Integer sortOrder;

    private Integer required;

    private Integer nerEnabled;

    private Integer enabled;
}
