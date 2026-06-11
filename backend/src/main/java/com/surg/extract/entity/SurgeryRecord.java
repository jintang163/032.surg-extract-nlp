package com.surg.extract.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("surgery_record")
public class SurgeryRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String recordNo;

    private String patientId;

    private String patientName;

    private String hospitalNo;

    private String gender;

    private Integer age;

    private String department;

    private String originalFileName;

    private String fileType;

    private String filePath;

    private Long fileSize;

    @TableField(typeHandler = org.apache.ibatis.type.StringTypeHandler.class)
    private String ocrText;

    @TableField(typeHandler = org.apache.ibatis.type.StringTypeHandler.class)
    private String processedText;

    @TableField(typeHandler = org.apache.ibatis.type.StringTypeHandler.class)
    private String asrText;

    private Double audioDuration;

    @TableField(typeHandler = org.apache.ibatis.type.StringTypeHandler.class)
    private String asrSegments;

    @TableField(typeHandler = org.apache.ibatis.type.StringTypeHandler.class)
    private String enhancedText;

    @TableField(typeHandler = org.apache.ibatis.type.StringTypeHandler.class)
    private String instruments;

    @TableField(typeHandler = org.apache.ibatis.type.StringTypeHandler.class)
    private String fusionStats;

    private String multimodalStatus;

    private Long uploadUserId;

    private String uploadUserName;

    private LocalDateTime uploadTime;

    private String processStatus;

    private String processMessage;

    private LocalDateTime ocrStartTime;

    private LocalDateTime ocrEndTime;

    private LocalDateTime nerStartTime;

    private LocalDateTime nerEndTime;

    private Integer patientConfirmed;

    private Long confirmUserId;

    private LocalDateTime confirmTime;

    private Integer fillDuration;

    private Integer manualDurationEst;

    private Integer hisSynced;

    private LocalDateTime hisSyncTime;

    private String hisSyncMessage;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedTime;

    @TableLogic
    private Integer deleted;
}
