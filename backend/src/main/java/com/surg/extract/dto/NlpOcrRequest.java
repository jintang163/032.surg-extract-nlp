package com.surg.extract.dto;

import lombok.Data;
import java.util.List;

@Data
public class NlpOcrRequest {

    private Long recordId;

    private String filePath;

    private String fileType;
}
