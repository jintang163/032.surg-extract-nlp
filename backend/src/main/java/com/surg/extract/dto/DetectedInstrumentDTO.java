package com.surg.extract.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.util.List;

@Data
public class DetectedInstrumentDTO {

    @JsonProperty("name")
    private String name;

    @JsonProperty("confidence")
    private Double confidence;

    @JsonProperty("bbox")
    private List<Double> bbox;

    @JsonProperty("category")
    private String category;

    @JsonProperty("count")
    private Integer count;
}
