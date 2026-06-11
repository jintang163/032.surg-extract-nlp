package com.surg.extract.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class NlpOcrRequest {

    @JsonProperty("record_id")
    private Long recordId;

    @JsonProperty("file_path")
    private String filePath;

    @JsonProperty("file_type")
    private String fileType;
}
