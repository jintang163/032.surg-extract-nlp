package com.surg.extract.dto;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class SurgeryTemplateVersionDTO {

    private Long id;

    private Long templateId;

    private Integer versionNo;

    private String templateContent;

    private List<PlaceholderDTO> placeholders;

    private String changeLog;

    private Integer isCurrent;

    private String createdUserName;

    private LocalDateTime createdTime;
}
