package com.surg.extract.es.document;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

@Data
@Document(indexName = "surgery_records")
@Setting(shards = 1, replicas = 0, refreshInterval = "1s")
@Mapping(mappingPath = "es/surgery-record-mapping.json")
public class SurgeryRecordDocument {

    @Id
    private Long id;

    @Field(type = FieldType.Keyword)
    private String recordNo;

    @Field(type = FieldType.Keyword)
    private String hospitalNo;

    @Field(type = FieldType.Keyword)
    private String department;

    @Field(type = FieldType.Text, analyzer = "ik_max_word", searchAnalyzer = "ik_smart")
    private String surgeryName;

    @Field(type = FieldType.Keyword)
    private String surgeryCode;

    @Field(type = FieldType.Text, analyzer = "ik_max_word", searchAnalyzer = "ik_smart")
    private String preopDiagnosis;

    @Field(type = FieldType.Text, analyzer = "ik_max_word", searchAnalyzer = "ik_smart")
    private String postopDiagnosis;

    @Field(type = FieldType.Keyword)
    private String surgeryLevel;

    @Field(type = FieldType.Keyword)
    private String incisionLevel;

    @Field(type = FieldType.Keyword)
    private String incisionHealing;

    @Field(type = FieldType.Keyword)
    private String anesthesiaType;

    @Field(type = FieldType.Double)
    private BigDecimal bloodLoss;

    @Field(type = FieldType.Double)
    private BigDecimal bloodTransfusion;

    @Field(type = FieldType.Double)
    private BigDecimal fluidInfusion;

    @Field(type = FieldType.Double)
    private BigDecimal urineOutput;

    @Field(type = FieldType.Keyword)
    private String surgeon;

    @Field(type = FieldType.Keyword)
    private String chiefSurgeon;

    @Field(type = FieldType.Keyword)
    private String anesthesiologist;

    @Field(type = FieldType.Date, format = DateFormat.date_hour_minute_second)
    private LocalDateTime surgeryDate;

    @Field(type = FieldType.Date, format = DateFormat.date_hour_minute_second)
    private LocalDateTime uploadTime;

    @Field(type = FieldType.Date, format = DateFormat.date_hour_minute_second)
    private LocalDateTime indexedTime;

    @Field(type = FieldType.Keyword)
    private String processStatus;

    @Field(type = FieldType.Integer)
    private Integer patientConfirmed;

    @Field(type = FieldType.Nested)
    private Map<String, EntityDocument> entities;

    @Data
    public static class EntityDocument {
        @Field(type = FieldType.Keyword)
        private String entityType;

        @Field(type = FieldType.Text)
        private String entityValue;

        @Field(type = FieldType.Keyword)
        private String entityUnit;

        @Field(type = FieldType.Double)
        private BigDecimal numericValue;
    }
}
