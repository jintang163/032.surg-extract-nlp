package com.surg.extract.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.util.List;

@Data
public class NlpNerResponse {

    @JsonProperty("success")
    private Boolean success;

    @JsonProperty("entities")
    private List<NerEntityDTO> entities;

    @JsonProperty("error_message")
    private String errorMessage;

    @JsonProperty("processing_time_ms")
    private Long processingTimeMs;
}
