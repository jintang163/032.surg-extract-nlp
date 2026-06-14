package com.surg.extract.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("batch_task")
public class BatchTask implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String taskName;

    private String department;

    private String taskType;

    private String originalFileName;

    private String filePath;

    private Long fileSize;

    private Integer totalCount;

    private Integer successCount;

    private Integer failedCount;

    private Integer pendingCount;

    private String status;

    private String errorMessage;

    private String notifyType;

    private String notifyTarget;

    private Boolean notified;

    private Integer retryCount;

    private Integer maxRetryCount;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private Long createdBy;

    private String createdByName;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedTime;

    @TableLogic
    private Integer deleted;
}
