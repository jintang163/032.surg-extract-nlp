package com.surg.extract.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class InstrumentRecognitionResponseDTO {

    @JsonProperty("success")
    private Boolean success;

    @JsonProperty("instruments")
    private List<DetectedInstrumentDTO> instruments;

    @JsonProperty("mode_used")
    private String modeUsed;

    @JsonProperty("image_size")
    private Map<String, Integer> imageSize;

    @JsonProperty("error_message")
    private String errorMessage;

    @JsonProperty("processing_time_ms")
    private Long processingTimeMs;
}
