package com.surg.extract.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class InstrumentRecognitionRequestDTO {

    @JsonProperty("record_id")
    private Long recordId;

    @JsonProperty("file_path")
    private String filePath;

    @JsonProperty("mode")
    private String mode;

    @JsonProperty("confidence_threshold")
    private Double confidenceThreshold;
}
