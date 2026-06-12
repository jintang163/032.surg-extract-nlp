package com.surg.extract.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("department_custom_field")
public class DepartmentCustomField implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String department;

    private String fieldCode;

    private String fieldName;

    private String fieldType;

    private String unit;

    @TableField(typeHandler = org.apache.ibatis.type.StringTypeHandler.class)
    private String enumOptions;

    private String description;

    private Integer sortOrder;

    private Integer required;

    private Integer nerEnabled;

    private String modelStatus;

    private String modelVersion;

    private LocalDateTime lastTrainTime;

    private Integer sampleCount;

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
