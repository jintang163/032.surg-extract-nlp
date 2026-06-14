package com.surg.extract.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("doctor_feedback")
public class DoctorFeedback implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long recordId;

    private String recordNo;

    private Long entityId;

    private String entityType;

    private String originalValue;

    private String originalUnit;

    private BigDecimal originalConfidence;

    private String originalSource;

    private Integer originalStartPos;

    private Integer originalEndPos;

    private String originalText;

    private String correctedValue;

    private String correctedUnit;

    private String correctionType;

    private String feedbackSource;

    private String department;

    private Long feedbackUserId;

    private String feedbackUserName;

    private String feedbackRemark;

    private Integer qualityScore;

    private Integer usedForTraining;

    private LocalDateTime usedTime;

    private String trainBatchNo;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedTime;

    @TableLogic
    private Integer deleted;
}
