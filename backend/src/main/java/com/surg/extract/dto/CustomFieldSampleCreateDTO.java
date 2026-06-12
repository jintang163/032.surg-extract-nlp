package com.surg.extract.dto;

import lombok.Data;

@Data
public class CustomFieldSampleCreateDTO {

    private Long fieldId;

    private String text;

    private String entityValue;

    private Integer startPos;

    private Integer endPos;

    private String source;

    private String remark;
}
