package com.surg.extract.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.surg.extract.entity.MedicalRecordHome;
import com.surg.extract.entity.SurgeryEntity;
import com.surg.extract.entity.SurgeryRecord;
import com.surg.extract.es.document.SurgeryRecordDocument;
import com.surg.extract.es.repository.SurgeryRecordEsRepository;
import com.surg.extract.mapper.MedicalRecordHomeMapper;
import com.surg.extract.mapper.SurgeryEntityMapper;
import com.surg.extract.mapper.SurgeryRecordMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.data.elasticsearch.core.document.Document;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SurgeryRecordEsIndexService {

    private final SurgeryRecordEsRepository esRepository;
    private final ElasticsearchOperations elasticsearchOperations;
    private final SurgeryRecordMapper recordMapper;
    private final SurgeryEntityMapper entityMapper;
    private final MedicalRecordHomeMapper homeMapper;
    private final ObjectMapper objectMapper;

    public boolean indexExists() {
        IndexOperations indexOps = elasticsearchOperations.indexOps(SurgeryRecordDocument.class);
        return indexOps.exists();
    }

    public void createIndex() {
        IndexOperations indexOps = elasticsearchOperations.indexOps(SurgeryRecordDocument.class);
        if (indexOps.exists()) {
            log.info("索引已存在，无需创建");
            return;
        }
        Document mapping = indexOps.createMapping(SurgeryRecordDocument.class);
        indexOps.create();
        indexOps.putMapping(mapping);
        log.info("ES索引创建成功: surgery_records");
    }

    public void deleteIndex() {
        IndexOperations indexOps = elasticsearchOperations.indexOps(SurgeryRecordDocument.class);
        if (indexOps.exists()) {
            indexOps.delete();
            log.info("ES索引删除成功");
        }
    }

    public void rebuildIndex() {
        deleteIndex();
        createIndex();
        bulkIndexAll();
    }

    public long bulkIndexAll() {
        LambdaQueryWrapper<SurgeryRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(SurgeryRecord::getProcessStatus, "NER_DONE", "COMPLETED")
                .orderByAsc(SurgeryRecord::getId);

        List<SurgeryRecord> records = recordMapper.selectList(wrapper);
        log.info("开始批量索引，共{}条记录", records.size());

        int count = 0;
        List<SurgeryRecordDocument> batch = new ArrayList<>();
        int batchSize = 100;

        for (SurgeryRecord record : records) {
            try {
                SurgeryRecordDocument doc = buildDocument(record);
                if (doc != null) {
                    batch.add(doc);
                    count++;
                }
                if (batch.size() >= batchSize) {
                    esRepository.saveAll(batch);
                    batch.clear();
                    log.info("已索引 {} 条", count);
                }
            } catch (Exception e) {
                log.warn("索引记录失败: recordId={}, error={}", record.getId(), e.getMessage());
            }
        }
        if (!batch.isEmpty()) {
            esRepository.saveAll(batch);
        }
        log.info("批量索引完成，共 {} 条", count);
        return count;
    }

    @Async
    public void asyncIndexRecord(Long recordId) {
        try {
            indexRecord(recordId);
        } catch (Exception e) {
            log.warn("异步索引失败: recordId={}, error={}", recordId, e.getMessage());
        }
    }

    public SurgeryRecordDocument indexRecord(Long recordId) {
        SurgeryRecord record = recordMapper.selectById(recordId);
        if (record == null) {
            log.warn("记录不存在: recordId={}", recordId);
            return null;
        }
        SurgeryRecordDocument doc = buildDocument(record);
        if (doc == null) {
            log.warn("无法构建索引文档: recordId={}", recordId);
            return null;
        }
        esRepository.save(doc);
        log.debug("索引成功: recordId={}", recordId);
        return doc;
    }

    public void deleteFromIndex(Long recordId) {
        esRepository.deleteById(recordId);
        log.info("从ES删除: recordId={}", recordId);
    }

    public long getIndexCount() {
        try {
            return esRepository.count();
        } catch (Exception e) {
            log.warn("获取索引数量失败: {}", e.getMessage());
            return 0;
        }
    }

    private SurgeryRecordDocument buildDocument(SurgeryRecord record) {
        if (record == null) return null;

        SurgeryRecordDocument doc = new SurgeryRecordDocument();
        doc.setId(record.getId());
        doc.setRecordNo(record.getRecordNo());
        doc.setHospitalNo(record.getHospitalNo());
        doc.setDepartment(record.getDepartment());
        doc.setUploadTime(record.getUploadTime());
        doc.setProcessStatus(record.getProcessStatus());
        doc.setPatientConfirmed(record.getPatientConfirmed());
        doc.setIndexedTime(LocalDateTime.now());

        List<SurgeryEntity> entities = entityMapper.selectByRecordId(record.getId());
        Map<String, SurgeryEntity> entityMap = new HashMap<>();
        if (!CollectionUtils.isEmpty(entities)) {
            for (SurgeryEntity entity : entities) {
                if (!StringUtils.hasText(entity.getEntityType())) continue;
                entityMap.put(entity.getEntityType(), entity);
            }
        }

        setEntityText(doc, entityMap, "SURGERY_NAME", v -> doc.setSurgeryName(v));
        setEntityText(doc, entityMap, "PREOP_DIAGNOSIS", v -> doc.setPreopDiagnosis(v));
        setEntityText(doc, entityMap, "POSTOP_DIAGNOSIS", v -> doc.setPostopDiagnosis(v));
        setEntityText(doc, entityMap, "SURGERY_LEVEL", v -> doc.setSurgeryLevel(v));
        setEntityText(doc, entityMap, "INCISION_LEVEL", v -> doc.setIncisionLevel(v));
        setEntityText(doc, entityMap, "INCISION_HEALING", v -> doc.setIncisionHealing(v));
        setEntityText(doc, entityMap, "ANESTHESIA_TYPE", v -> doc.setAnesthesiaType(v));
        setEntityText(doc, entityMap, "SURGEON", v -> doc.setSurgeon(v));
        setEntityText(doc, entityMap, "CHIEF_SURGEON", v -> doc.setChiefSurgeon(v));
        setEntityText(doc, entityMap, "ANESTHESIOLOGIST", v -> doc.setAnesthesiologist(v));

        setEntityNumeric(doc, entityMap, "BLOOD_LOSS", v -> doc.setBloodLoss(v));
        setEntityNumeric(doc, entityMap, "BLOOD_TRANSFUSION", v -> doc.setBloodTransfusion(v));
        setEntityNumeric(doc, entityMap, "FLUID_INFUSION", v -> doc.setFluidInfusion(v));
        setEntityNumeric(doc, entityMap, "URINE_OUTPUT", v -> doc.setUrineOutput(v));

        SurgeryEntity dateEntity = entityMap.get("SURGERY_DATE");
        if (dateEntity != null && StringUtils.hasText(dateEntity.getEntityValue())) {
            try {
                String value = dateEntity.getEntityValue()
                        .replace("年", "-").replace("月", "-").replace("日", " ")
                        .replace("/", "-").replace(".", "-").trim();
                if (value.matches("\\d{4}-\\d{1,2}-\\d{1,2}")) {
                    doc.setSurgeryDate(java.time.LocalDate.parse(value).atStartOfDay());
                } else if (value.matches("\\d{4}-\\d{1,2}-\\d{1,2}\\s+\\d{1,2}:\\d{1,2}")) {
                    value += ":00";
                    doc.setSurgeryDate(LocalDateTime.parse(value.replace(" ", "T")));
                }
            } catch (Exception ignored) {
            }
        }

        MedicalRecordHome home = homeMapper.selectByRecordId(record.getId());
        if (home != null) {
            if (!StringUtils.hasText(doc.getSurgeryName()) && StringUtils.hasText(home.getSurgeryName())) {
                doc.setSurgeryName(home.getSurgeryName());
            }
            if (!StringUtils.hasText(doc.getSurgeryCode()) && StringUtils.hasText(home.getSurgeryCode())) {
                doc.setSurgeryCode(home.getSurgeryCode());
            }
            if (!StringUtils.hasText(doc.getSurgeryLevel()) && StringUtils.hasText(home.getSurgeryLevel())) {
                doc.setSurgeryLevel(home.getSurgeryLevel());
            }
            if (!StringUtils.hasText(doc.getIncisionLevel()) && StringUtils.hasText(home.getIncisionLevel())) {
                doc.setIncisionLevel(home.getIncisionLevel());
            }
            if (!StringUtils.hasText(doc.getIncisionHealing()) && StringUtils.hasText(home.getIncisionHealing())) {
                doc.setIncisionHealing(home.getIncisionHealing());
            }
            if (!StringUtils.hasText(doc.getAnesthesiaType()) && StringUtils.hasText(home.getAnesthesiaType())) {
                doc.setAnesthesiaType(home.getAnesthesiaType());
            }
            if (doc.getBloodLoss() == null && home.getBloodLoss() != null) {
                doc.setBloodLoss(BigDecimal.valueOf(home.getBloodLoss()));
            }
            if (doc.getBloodTransfusion() == null && home.getBloodTransfusion() != null) {
                doc.setBloodTransfusion(BigDecimal.valueOf(home.getBloodTransfusion()));
            }
            if (doc.getFluidInfusion() == null && home.getFluidInfusion() != null) {
                doc.setFluidInfusion(BigDecimal.valueOf(home.getFluidInfusion()));
            }
            if (!StringUtils.hasText(doc.getSurgeon()) && StringUtils.hasText(home.getSurgeon())) {
                doc.setSurgeon(home.getSurgeon());
            }
            if (!StringUtils.hasText(doc.getChiefSurgeon()) && StringUtils.hasText(home.getChiefSurgeon())) {
                doc.setChiefSurgeon(home.getChiefSurgeon());
            }
            if (!StringUtils.hasText(doc.getAnesthesiologist()) && StringUtils.hasText(home.getAnesthesiologist())) {
                doc.setAnesthesiologist(home.getAnesthesiologist());
            }
            if (doc.getSurgeryDate() == null && home.getSurgeryDate() != null) {
                doc.setSurgeryDate(home.getSurgeryDate().toLocalDate().atStartOfDay());
            }
        }

        Map<String, SurgeryRecordDocument.EntityDocument> entityDocs = new HashMap<>();
        for (Map.Entry<String, SurgeryEntity> entry : entityMap.entrySet()) {
            SurgeryRecordDocument.EntityDocument ed = new SurgeryRecordDocument.EntityDocument();
            ed.setEntityType(entry.getKey());
            ed.setEntityValue(entry.getValue().getEntityValue());
            ed.setEntityUnit(entry.getValue().getEntityUnit());
            ed.setNumericValue(parseNumeric(entry.getValue().getEntityValue()));
            entityDocs.put(entry.getKey(), ed);
        }
        doc.setEntities(entityDocs);

        return doc;
    }

    private interface TextSetter {
        void set(String value);
    }

    private interface NumericSetter {
        void set(BigDecimal value);
    }

    private void setEntityText(SurgeryRecordDocument doc, Map<String, SurgeryEntity> entityMap,
                               String type, TextSetter setter) {
        SurgeryEntity entity = entityMap.get(type);
        if (entity != null && StringUtils.hasText(entity.getEntityValue())) {
            setter.set(entity.getEntityValue());
        }
    }

    private void setEntityNumeric(SurgeryRecordDocument doc, Map<String, SurgeryEntity> entityMap,
                                  String type, NumericSetter setter) {
        SurgeryEntity entity = entityMap.get(type);
        if (entity != null && StringUtils.hasText(entity.getEntityValue())) {
            BigDecimal val = parseNumeric(entity.getEntityValue());
            if (val != null) {
                setter.set(val);
            }
        }
    }

    private BigDecimal parseNumeric(String value) {
        if (!StringUtils.hasText(value)) return null;
        try {
            String num = value.replaceAll("[^0-9.]", "");
            return StringUtils.hasText(num) ? new BigDecimal(num) : null;
        } catch (Exception e) {
            return null;
        }
    }
}
