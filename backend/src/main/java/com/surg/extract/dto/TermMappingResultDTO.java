package com.surg.extract.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
public class TermMappingResultDTO {

    private String sourceText;

    private Long standardTermId;

    private String standardTermName;

    private String standardTermCode;

    private String icdCode;

    private String icdName;

    private String icdVersion;

    private String matchMethod;

    private BigDecimal similarityScore;

    private List<Map<String, Object>> matchPath;

    private List<TermMappingCandidateDTO> candidates;

    private Boolean mappingSuccess;

    private String failReason;

    private LocalDateTime mappingTime;

    private Integer costMs;
}
