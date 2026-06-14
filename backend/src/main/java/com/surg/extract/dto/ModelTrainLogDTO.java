package com.surg.extract.dto;

import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class ModelTrainLogDTO implements Serializable {

    private static final long serialVersionUID = 1L;

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

    private String triggeredByName;

    private String modelPath;

    private String remark;

    private LocalDateTime createdTime;
}
