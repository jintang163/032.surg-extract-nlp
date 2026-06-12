package com.surg.extract.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("surgery_template_version")
public class SurgeryTemplateVersion implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long templateId;

    private Integer versionNo;

    @TableField(typeHandler = org.apache.ibatis.type.StringTypeHandler.class)
    private String templateContent;

    @TableField(typeHandler = org.apache.ibatis.type.StringTypeHandler.class)
    private String placeholders;

    private String changeLog;

    private Integer isCurrent;

    private Long createdUserId;

    private String createdUserName;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdTime;
}
