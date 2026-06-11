package com.surg.extract.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.time.LocalDateTime;

@Data
public class RecordAttachmentDTO {

    private Long id;

    @JsonProperty("record_id")
    private Long recordId;

    @JsonProperty("original_file_name")
    private String originalFileName;

    @JsonProperty("file_path")
    private String filePath;

    @JsonProperty("file_type")
    private String fileType;

    @JsonProperty("file_size")
    private Long fileSize;

    @JsonProperty("attachment_type")
    private String attachmentType;

    @JsonProperty("process_status")
    private String processStatus;

    @JsonProperty("process_message")
    private String processMessage;

    @JsonProperty("extracted_text")
    private String extractedText;

    @JsonProperty("sort_order")
    private Integer sortOrder;

    @JsonProperty("created_time")
    private LocalDateTime createdTime;
}
