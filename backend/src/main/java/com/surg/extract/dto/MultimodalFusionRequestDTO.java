package com.surg.extract.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class MultimodalFusionRequestDTO {

    @JsonProperty("record_id")
    private Long recordId;

    @JsonProperty("ocr_text")
    private String ocrText;

    @JsonProperty("asr_result")
    private Map<String, Object> asrResult;

    @JsonProperty("instrument_result")
    private Map<String, Object> instrumentResult;

    @JsonProperty("ner_entities")
    private List<NerEntityDTO> nerEntities;

    @JsonProperty("regex_entities")
    private List<NerEntityDTO> regexEntities;

    @JsonProperty("rule_entities")
    private List<NerEntityDTO> ruleEntities;
}
