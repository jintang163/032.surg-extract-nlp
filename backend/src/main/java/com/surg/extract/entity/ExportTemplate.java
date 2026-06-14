package com.surg.extract.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("export_template")
public class ExportTemplate implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String templateName;

    private String templateCode;

    private String description;

    private String exportFormat;

    private String targetSystem;

    private String department;

    @TableField(typeHandler = org.apache.ibatis.type.StringTypeHandler.class)
    private String fieldConfigs;

    @TableField(typeHandler = org.apache.ibatis.type.StringTypeHandler.class)
    private String unitConversions;

    private Integer sortOrder;

    private Integer isDefault;

    private Integer enabled;

    private Long createUserId;

    private String createUserName;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedTime;

    @TableLogic
    private Integer deleted;
}
