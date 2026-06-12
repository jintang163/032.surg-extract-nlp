package com.surg.extract.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("medical_record_home_ext")
public class MedicalRecordHomeExt implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long recordId;

    private Long fieldId;

    private String fieldCode;

    private String fieldName;

    private String fieldValue;

    private BigDecimal confidence;

    private String source;

    private Integer verified;

    private Long verifiedBy;

    private LocalDateTime verifiedTime;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedTime;
}
