package com.surg.extract.dto;

import lombok.Data;
import java.util.List;

@Data
public class NlpNerResponse {

    private Boolean success;

    private List<NerEntityDTO> entities;

    private String errorMessage;

    private Long processingTimeMs;
}
