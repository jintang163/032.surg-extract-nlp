package com.surg.extract.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("surgery_entity")
public class SurgeryEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long recordId;

    private String entityType;

    private String entityValue;

    private String entityUnit;

    private BigDecimal confidence;

    private String source;

    private Integer startPos;

    private Integer endPos;

    private String originalText;

    private Integer verified;

    private Long verifiedBy;

    private LocalDateTime verifiedTime;

    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedTime;

    @TableLogic
    private Integer deleted;
}
