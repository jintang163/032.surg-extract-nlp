package com.surg.extract.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class NerEntityDTO {

    @JsonProperty("entity_type")
    private String entityType;

    @JsonProperty("entity_value")
    private String entityValue;

    @JsonProperty("entity_unit")
    private String entityUnit;

    @JsonProperty("confidence")
    private Double confidence;

    @JsonProperty("source")
    private String source;

    @JsonProperty("start_pos")
    private Integer startPos;

    @JsonProperty("end_pos")
    private Integer endPos;

    @JsonProperty("original_text")
    private String originalText;
}
