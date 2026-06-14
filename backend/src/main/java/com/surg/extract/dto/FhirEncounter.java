package com.surg.extract.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FhirEncounter extends FhirResource {

    private String resourceType = "Encounter";

    private List<FhirIdentifier> identifier;

    private String status = "finished";

    private FhirCodeableConcept classCode;

    private FhirReference subject;

    private FhirPeriod period;

    private List<FhirReference> diagnosis;
}
