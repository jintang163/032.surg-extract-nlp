package com.surg.extract.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("surgery_template")
public class SurgeryTemplate implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String templateCode;

    private String templateName;

    private String surgeryType;

    private String surgeryCode;

    private String department;

    @TableField(typeHandler = org.apache.ibatis.type.StringTypeHandler.class)
    private String templateContent;

    @TableField(typeHandler = org.apache.ibatis.type.StringTypeHandler.class)
    private String placeholders;

    private Integer currentVersion;

    private String status;

    private Integer isDefault;

    private String description;

    private String tags;

    private Integer sortOrder;

    private Integer useCount;

    private Long createdUserId;

    private String createdUserName;

    private Long updatedUserId;

    private String updatedUserName;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedTime;

    @TableLogic
    private Integer deleted;
}
