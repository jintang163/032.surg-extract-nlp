package com.surg.extract.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class AsrProcessRequestDTO {

    @JsonProperty("record_id")
    private Long recordId;

    @JsonProperty("file_path")
    private String filePath;

    @JsonProperty("file_type")
    private String fileType;

    @JsonProperty("language")
    private String language;
}
