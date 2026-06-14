package com.surg.extract.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FhirPatient extends FhirResource {

    private String resourceType = "Patient";

    private List<FhirIdentifier> identifier;

    private List<FhirHumanName> name;

    private String gender;

    private String birthDate;

    private Boolean active = true;
}
