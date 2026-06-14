package com.surg.extract.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("model_train_log")
public class ModelTrainLog implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String trainBatchNo;

    private String modelName;

    private String modelVersion;

    private String previousVersion;

    private String trainType;

    private Integer feedbackCount;

    private Integer newSampleCount;

    private Integer totalSampleCount;

    private BigDecimal trainLoss;

    private BigDecimal devLoss;

    private BigDecimal precisionScore;

    private BigDecimal recallScore;

    private BigDecimal f1Score;

    private BigDecimal previousF1Score;

    private BigDecimal f1Improvement;

    private String entityTypeBreakdown;

    private String trainStatus;

    private String failReason;

    private LocalDateTime trainStartTime;

    private LocalDateTime trainEndTime;

    private Integer trainDurationSec;

    private String trainParams;

    private Long triggeredBy;

    private String triggeredByName;

    private String modelPath;

    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedTime;

    @TableLogic
    private Integer deleted;
}
