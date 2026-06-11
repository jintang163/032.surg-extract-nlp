package com.surg.extract.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class RecordQueryDTO {

    private Long id;

    private String recordNo;

    private String patientName;

    private String hospitalNo;

    private String gender;

    private Integer age;

    private String department;

    private String originalFileName;

    private String fileType;

    private String processStatus;

    private String processMessage;

    private Integer patientConfirmed;

    private Integer hisSynced;

    private Integer fillDuration;

    private Integer manualDurationEst;

    private LocalDateTime uploadTime;

    private LocalDateTime confirmTime;
}
