package com.surg.extract.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FhirProcedurePerformer {

    private FhirCodeableConcept function;

    private FhirReference actor;
}
