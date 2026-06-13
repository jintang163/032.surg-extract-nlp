package com.surg.extract.dto;

import lombok.Data;

@Data
public class TermGraphStatsDTO {

    private long totalStandardTerms;

    private long totalAliasTerms;

    private long totalGraphNodes;

    private long totalSynonymRelationships;

    private long totalMappingCount;

    private long todayMappingCount;

    private double mappingSuccessRate;

    private long totalIcdCodes;
}
