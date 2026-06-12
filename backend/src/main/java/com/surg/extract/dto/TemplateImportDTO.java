package com.surg.extract.dto;

import lombok.Data;
import java.util.List;

@Data
public class TemplateImportDTO {

    private String templateCode;

    private String templateName;

    private String surgeryType;

    private String surgeryCode;

    private String department;

    private String templateContent;

    private List<PlaceholderDTO> placeholders;

    private String description;

    private String tags;

    private Integer sortOrder;
}
