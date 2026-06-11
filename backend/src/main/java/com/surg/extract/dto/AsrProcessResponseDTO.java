package com.surg.extract.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.util.List;

@Data
public class AsrProcessResponseDTO {

    @JsonProperty("success")
    private Boolean success;

    @JsonProperty("full_text")
    private String fullText;

    @JsonProperty("segments")
    private List<AsrSegmentDTO> segments;

    @JsonProperty("duration")
    private Double duration;

    @JsonProperty("language")
    private String language;

    @JsonProperty("source_type")
    private String sourceType;

    @JsonProperty("error_message")
    private String errorMessage;

    @JsonProperty("processing_time_ms")
    private Long processingTimeMs;
}
