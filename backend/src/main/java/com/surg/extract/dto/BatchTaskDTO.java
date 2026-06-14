package com.surg.extract.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class BatchTaskDTO {
    private Long id;
    private String taskName;
    private String department;
    private String taskType;
    private String originalFileName;
    private Long fileSize;
    private Integer totalCount;
    private Integer successCount;
    private Integer failedCount;
    private Integer pendingCount;
    private String status;
    private String errorMessage;
    private Integer progress;
    private String notifyType;
    private String notifyTarget;
    private Integer retryCount;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String createdByName;
    private LocalDateTime createdTime;
}
