package com.surg.extract.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.Data;

@Data
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "resourceType")
@JsonSubTypes({
        @JsonSubTypes.Type(value = FhirPatient.class, name = "Patient"),
        @JsonSubTypes.Type(value = FhirEncounter.class, name = "Encounter"),
        @JsonSubTypes.Type(value = FhirProcedure.class, name = "Procedure"),
})
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FhirResource {

    private String resourceType;

    private String id;
}
