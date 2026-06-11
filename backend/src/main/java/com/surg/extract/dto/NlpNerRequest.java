package com.surg.extract.dto;

import lombok.Data;
import java.util.List;

@Data
public class NlpNerRequest {

    private Long recordId;

    private String text;
}
