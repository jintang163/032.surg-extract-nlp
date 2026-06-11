package com.surg.extract.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("field_mapping")
public class FieldMapping implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String entityType;

    private String targetTable;

    private String targetField;

    private String fieldLabel;

    private Integer required;

    private String dataType;

    private String unit;

    @TableField(typeHandler = org.apache.ibatis.type.StringTypeHandler.class)
    private String enumValues;

    private Integer sortOrder;

    private Integer enabled;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedTime;
}
