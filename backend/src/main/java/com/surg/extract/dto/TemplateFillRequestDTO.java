package com.surg.extract.dto;

import lombok.Data;
import java.util.Map;

@Data
public class TemplateFillRequestDTO {

    private Long recordId;

    private Map<String, String> placeholderValues;
}
