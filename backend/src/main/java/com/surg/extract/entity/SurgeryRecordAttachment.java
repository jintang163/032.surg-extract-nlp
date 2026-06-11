package com.surg.extract.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("surgery_record_attachment")
public class SurgeryRecordAttachment implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long recordId;

    private String originalFileName;

    private String filePath;

    private String fileType;

    private Long fileSize;

    private String attachmentType;

    private String processStatus;

    private String processMessage;

    @TableField(typeHandler = org.apache.ibatis.type.StringTypeHandler.class)
    private String extractedText;

    private Integer sortOrder;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedTime;

    @TableLogic
    private Integer deleted;
}
