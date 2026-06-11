package com.surg.extract.dto;

import lombok.Data;

@Data
public class NlpOcrResponse {

    private Boolean success;

    private String ocrText;

    private String processedText;

    private String errorMessage;

    private Long processingTimeMs;
}
