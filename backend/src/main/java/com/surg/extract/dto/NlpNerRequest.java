package com.surg.extract.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.util.List;

@Data
public class NlpNerRequest {

    @JsonProperty("record_id")
    private Long recordId;

    @JsonProperty("text")
    private String text;

    @JsonProperty("domain")
    private String domain;

    @JsonProperty("include_confidence")
    private Boolean includeConfidence;

    @JsonProperty("department")
    private String department;

    @JsonProperty("entity_types")
    private List<String> entityTypes;
}
