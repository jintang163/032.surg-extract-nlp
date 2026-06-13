package com.surg.extract.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class TermBatchImportDTO {

    @NotEmpty(message = "导入数据不能为空")
    private List<TermImportItemDTO> items;

    private String termType;

    private Long categoryId;

    private Boolean skipDuplicate = true;
}
