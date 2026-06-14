package com.surg.extract.dto;

import lombok.Data;

@Data
public class SurgeonStatsDTO {
    private String surgeon;
    private Integer recordCount;
    private Double avgTimeSavedRate;
    private Double coverageRate;
}
