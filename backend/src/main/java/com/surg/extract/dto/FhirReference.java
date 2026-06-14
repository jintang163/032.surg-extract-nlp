package com.surg.extract.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FhirReference {

    private String reference;

    private String display;

    private FhirIdentifier identifier;
}
