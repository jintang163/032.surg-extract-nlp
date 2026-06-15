package com.surg.extract.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class Icd10PcsRecommendationDTO {

    private String pcsCode;

    private Map<String, String> codeComponents;

    private String description;

    private Double confidence;

    private List<String> matchPath;

    private List<String> matchedRules;

    private List<String> missingFields;

    private Boolean isComplete;
}
