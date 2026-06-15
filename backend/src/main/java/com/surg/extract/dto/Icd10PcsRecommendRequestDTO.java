package com.surg.extract.dto;

import lombok.Data;

import java.util.List;

@Data
public class Icd10PcsRecommendRequestDTO {

    private Long recordId;

    private List<NerEntityDTO> entities;

    private String surgeryText;

    private Integer topK = 5;
}
