package com.surg.extract.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class DetectedInstrumentDTO {

    @JsonProperty("name")
    private String name;

    @JsonProperty("instrument_name")
    private String instrumentName;

    @JsonProperty("instrument_code")
    private String instrumentCode;

    @JsonProperty("confidence")
    private Double confidence;

    @JsonProperty("bbox")
    private List<Double> bbox;

    @JsonProperty("position")
    private Map<String, Double> position;

    @JsonProperty("category")
    private String category;

    @JsonProperty("specialty")
    private String specialty;

    @JsonProperty("count")
    private Integer count;
}
