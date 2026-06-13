package com.surg.extract.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("medical_term_alias")
public class MedicalTermAlias implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long termId;

    private String aliasName;

    private String pinyin;

    private String pinyinAbbr;

    private String aliasType;

    private BigDecimal similarityScore;

    private String source;

    private Integer usageCount;

    private Integer matchCount;

    private String graphNodeId;

    private String reviewStatus;

    private Long reviewedBy;

    private LocalDateTime reviewedTime;

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
