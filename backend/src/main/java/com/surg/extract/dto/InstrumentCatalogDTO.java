package com.surg.extract.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class InstrumentCatalogDTO {

    @JsonProperty("total_count")
    private Integer totalCount;

    @JsonProperty("categories")
    private Map<String, List<String>> categories;
}
