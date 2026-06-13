package com.surg.extract.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class TermMergeRequestDTO {

    @NotNull(message = "目标术语ID不能为空")
    private Long targetTermId;

    @NotEmpty(message = "待合并术语ID列表不能为空")
    private List<Long> sourceTermIds;

    private String mergeReason;
}
