package com.surg.extract.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FhirProcedure extends FhirResource {

    private String resourceType = "Procedure";

    private List<FhirIdentifier> identifier;

    private String status = "completed";

    private FhirCodeableConcept code;

    private FhirReference subject;

    private FhirReference encounter;

    private FhirPeriod performedPeriod;

    private List<FhirProcedurePerformer> performer;
}
