package com.surg.extract.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FhirBundleEntry {

    private String fullUrl;

    private FhirResource resource;
}
