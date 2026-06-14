package com.surg.extract.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("quality_benchmark")
public class QualityBenchmark implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String indicatorCode;

    private String indicatorName;

    private String indicatorCategory;

    private String unit;

    private BigDecimal benchmarkValue;

    private BigDecimal warningThreshold;

    private BigDecimal criticalThreshold;

    private String direction;

    private String source;

    private String region;

    private String department;

    private Integer benchmarkYear;

    private Integer benchmarkQuarter;

    private String description;

    private BigDecimal sortOrder;

    private Integer enabled;

    @TableField(fill = FieldFill.INSERT)
    private Long createUserId;

    @TableField(fill = FieldFill.INSERT)
    private String createUserName;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedTime;

    @TableLogic
    private Integer deleted;
}
