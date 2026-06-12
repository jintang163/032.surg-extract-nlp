package com.surg.extract.dto;

import lombok.Data;

@Data
public class ModelTrainRequestDTO {

    private Long fieldId;

    private String department;

    private String fieldCode;

    private String trainMethod;
}
