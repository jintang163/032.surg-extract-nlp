package com.surg.extract.dto;

import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;

@Data
public class CorrectionTypeStatsDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String correctionType;

    private String correctionTypeLabel;

    private Long count;

    private BigDecimal percentage;
}
