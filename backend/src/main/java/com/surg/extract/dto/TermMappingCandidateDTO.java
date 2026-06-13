package com.surg.extract.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class TermMappingCandidateDTO {

    private Long termId;

    private String termName;

    private String termCode;

    private String icdCode;

    private String icdName;

    private String matchMethod;

    private BigDecimal similarityScore;

    private String matchedText;

    private Integer rank;
}
