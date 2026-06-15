package com.surg.extract.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class Icd10PcsRecommendResultDTO {

    private Boolean success;

    private Map<String, Object> parsedEntities;

    private List<Icd10PcsRecommendationDTO> recommendations;

    private Icd10PcsRecommendationDTO topCode;

    private Integer processingTimeMs;

    private String errorMessage;
}
