package com.surg.extract.dto;

import lombok.Data;
import java.util.List;

@Data
public class ExportTemplateCreateDTO {

    private String templateName;

    private String templateCode;

    private String description;

    private String exportFormat;

    private String targetSystem;

    private String department;

    private List<ExportFieldConfigDTO> fieldConfigs;

    private List<UnitConversionDTO> unitConversions;

    private Integer sortOrder;

    private Integer isDefault;

    private Integer enabled;
}
