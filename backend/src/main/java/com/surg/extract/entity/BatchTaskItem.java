package com.surg.extract.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("batch_task_item")
public class BatchTaskItem implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long taskId;

    private Long recordId;

    private String fileName;

    private String filePath;

    private String patientName;

    private String hospitalNo;

    private String fileType;

    private String status;

    private String errorMessage;

    private Integer retryCount;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedTime;

    @TableLogic
    private Integer deleted;
}
