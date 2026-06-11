package com.surg.extract.dto;

import lombok.Data;

@Data
public class NerEntityDTO {

    private String entityType;

    private String entityValue;

    private String entityUnit;

    private Double confidence;

    private String source;

    private Integer startPos;

    private Integer endPos;

    private String originalText;
}
