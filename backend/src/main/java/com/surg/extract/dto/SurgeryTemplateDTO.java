package com.surg.extract.dto;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class SurgeryTemplateDTO {

    private Long id;

    private String templateCode;

    private String templateName;

    private String surgeryType;

    private String surgeryCode;

    private String department;

    private String templateContent;

    private List<PlaceholderDTO> placeholders;

    private Integer currentVersion;

    private String status;

    private Integer isDefault;

    private String description;

    private String tags;

    private Integer sortOrder;

    private Integer useCount;

    private String createdUserName;

    private String updatedUserName;

    private LocalDateTime createdTime;

    private LocalDateTime updatedTime;
}
