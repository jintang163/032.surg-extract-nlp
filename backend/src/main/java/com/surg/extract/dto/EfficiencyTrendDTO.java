package com.surg.extract.dto;

import lombok.Data;

@Data
public class EfficiencyTrendDTO {
    private String date;
    private String department;
    private String surgeon;
    private Double avgManualDuration;
    private Double avgActualDuration;
    private Double timeSavedRate;
    private Integer recordCount;
}
