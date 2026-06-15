package com.surg.extract.dto;

import lombok.Data;

import java.util.Map;

@Data
public class Icd10PcsConfirmRequestDTO {

    private Long recordId;

    private String pcsCode;

    private String description;

    private Map<String, String> codeComponents;

    private Double recommendationConfidence;

    private String source;

    private Map<String, Object> additionalData;
}
