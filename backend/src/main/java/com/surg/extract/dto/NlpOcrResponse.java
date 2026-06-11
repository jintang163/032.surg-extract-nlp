package com.surg.extract.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class NlpOcrResponse {

    @JsonProperty("success")
    private Boolean success;

    @JsonProperty("ocr_text")
    private String ocrText;

    @JsonProperty("processed_text")
    private String processedText;

    @JsonProperty("error_message")
    private String errorMessage;

    @JsonProperty("processing_time_ms")
    private Long processingTimeMs;
}
