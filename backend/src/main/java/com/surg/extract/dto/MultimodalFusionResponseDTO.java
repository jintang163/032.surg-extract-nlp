package com.surg.extract.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class MultimodalFusionResponseDTO {

    @JsonProperty("success")
    private Boolean success;

    @JsonProperty("enhanced_text")
    private String enhancedText;

    @JsonProperty("entities")
    private List<NerEntityDTO> entities;

    @JsonProperty("instruments")
    private List<DetectedInstrumentDTO> instruments;

    @JsonProperty("fusion_stats")
    private Map<String, Object> fusionStats;

    @JsonProperty("source_breakdown")
    private Map<String, Integer> sourceBreakdown;

    @JsonProperty("error_message")
    private String errorMessage;

    @JsonProperty("processing_time_ms")
    private Long processingTimeMs;
}
