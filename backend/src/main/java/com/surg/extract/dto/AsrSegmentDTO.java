package com.surg.extract.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class AsrSegmentDTO {

    @JsonProperty("start")
    private Double start;

    @JsonProperty("end")
    private Double end;

    @JsonProperty("text")
    private String text;

    @JsonProperty("confidence")
    private Double confidence;
}
