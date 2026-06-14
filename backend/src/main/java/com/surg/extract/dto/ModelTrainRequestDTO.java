package com.surg.extract.dto;

import lombok.Data;
import java.io.Serializable;

@Data
public class ModelTrainRequestDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String trainType;

    private Integer maxFeedbackCount;

    private Integer minQualityScore;

    private Integer epochs;

    private Integer batchSize;

    private Double learningRate;

    private String remark;
}
