package com.surg.extract.dto;

import lombok.Data;

@Data
public class PlaceholderDTO {

    private String name;

    private String label;

    private String entityType;

    private String description;

    private Boolean required;

    private String defaultValue;
}
