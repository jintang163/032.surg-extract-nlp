package com.surg.extract.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("icd10_pcs_confirmation")
public class Icd10PcsConfirmation implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long recordId;

    private String pcsCode;

    private String sectionCode;

    private String bodySystemCode;

    private String rootOperationCode;

    private String bodyPartCode;

    private String approachCode;

    private String deviceCode;

    private String qualifierCode;

    private String description;

    private Double recommendedConfidence;

    private String recommendedCode;

    private Integer isMatched;

    private String source;

    private String additionalData;

    private Long confirmUserId;

    private String confirmUserName;

    private LocalDateTime confirmTime;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedTime;

    @TableLogic
    private Integer deleted;
}
