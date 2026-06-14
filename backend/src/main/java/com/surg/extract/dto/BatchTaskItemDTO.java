package com.surg.extract.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class BatchTaskItemDTO {
    private Long id;
    private Long taskId;
    private Long recordId;
    private String fileName;
    private String patientName;
    private String hospitalNo;
    private String fileType;
    private String status;
    private String errorMessage;
    private Integer retryCount;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private LocalDateTime createdTime;
}
