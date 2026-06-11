package com.surg.extract.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class NlpNerRequest {

    @JsonProperty("record_id")
    private Long recordId;

    @JsonProperty("text")
    private String text;
}
