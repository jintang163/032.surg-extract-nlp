package com.surg.extract.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("medical_term_icd")
public class MedicalTermIcd implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String icdCode;

    private String icdName;

    private String icdVersion;

    private String chapter;

    private String block;

    private String categoryCode;

    private String description;

    private String inclusionTerms;

    private String exclusionTerms;

    private Integer enabled;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedTime;
}
