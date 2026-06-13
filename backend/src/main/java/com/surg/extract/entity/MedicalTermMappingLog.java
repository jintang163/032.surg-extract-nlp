package com.surg.extract.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("medical_term_mapping_log")
public class MedicalTermMappingLog implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long recordId;

    private String sourceText;

    private Long standardTermId;

    private String standardTermName;

    private String icdCode;

    private String matchMethod;

    private BigDecimal similarityScore;

    private String matchPath;

    private Integer mappingSuccess;

    private String failReason;

    private LocalDateTime mappingTime;

    private Integer costMs;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdTime;
}
