package com.surg.extract.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class EntityUpdateDTO {

    private Long id;

    private String entityType;

    private String entityValue;

    private String entityUnit;

    private Integer verified;
}
