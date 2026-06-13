package com.surg.extract.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class TermMappingRequestDTO {

    @NotBlank(message = "待映射文本不能为空")
    private String text;

    private String termType;

    private Long recordId;

    private Double minSimilarity = 0.7;

    private Integer maxResults = 5;

    private Boolean useGraph = true;

    private Boolean useFuzzy = true;

    private Boolean usePinyin = true;
}
