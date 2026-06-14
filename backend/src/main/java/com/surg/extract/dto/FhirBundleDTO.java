package com.surg.extract.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FhirBundleDTO {

    private String resourceType = "Bundle";

    private String type = "collection";

    private String id;

    private String timestamp;

    private List<FhirBundleEntry> entry;
}
