package com.surg.extract.dto;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;

@Data
public class RecordUploadDTO {

    @NotBlank(message = "文件名不能为空")
    private String fileName;

    private String patientId;

    private String patientName;

    private String hospitalNo;

    private String department;
}
