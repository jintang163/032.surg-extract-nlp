package com.surg.extract.dto;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class ExportTemplateDTO {

    private Long id;

    private String templateName;

    private String templateCode;

    private String description;

    private String exportFormat;

    private String exportFormatLabel;

    private String targetSystem;

    private String department;

    private List<ExportFieldConfigDTO> fieldConfigs;

    private List<UnitConversionDTO> unitConversions;

    private Integer sortOrder;

    private Integer isDefault;

    private Integer enabled;

    private Long createUserId;

    private String createUserName;

    private LocalDateTime createdTime;

    private LocalDateTime updatedTime;
}
