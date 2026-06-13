package com.surg.extract.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("medical_term")
public class MedicalTerm implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String termCode;

    private String standardName;

    private String pinyin;

    private String pinyinAbbr;

    private Long categoryId;

    private String termType;

    private Long icdId;

    private String icdCode;

    private String icdName;

    private String icdVersion;

    private String definition;

    private Integer usageCount;

    private Integer matchCount;

    private BigDecimal confidence;

    private String reviewStatus;

    private Long reviewedBy;

    private LocalDateTime reviewedTime;

    private String reviewRemark;

    private Integer enabled;

    private Long createdUserId;

    private String createdUserName;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedTime;

    @TableLogic
    private Integer deleted;
}
