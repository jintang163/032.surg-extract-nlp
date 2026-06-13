package com.surg.extract.dto;

import lombok.Data;

import java.util.List;

@Data
public class TermImportItemDTO {

    private String termCode;

    private String standardName;

    private List<String> synonyms;

    private String icdCode;

    private String icdName;

    private String definition;

    private String termType;
}
