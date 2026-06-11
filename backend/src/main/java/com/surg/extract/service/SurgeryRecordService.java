package com.surg.extract.service;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.IdUtil;
import com.surg.extract.common.BusinessException;
import com.surg.extract.common.ErrorCode;
import com.surg.extract.dto.*;
import com.surg.extract.entity.FieldMapping;
import com.surg.extract.entity.MedicalRecordHome;
import com.surg.extract.entity.SurgeryEntity;
import com.surg.extract.entity.SurgeryRecord;
import com.surg.extract.feign.NlpServiceClient;
import com.surg.extract.mapper.FieldMappingMapper;
import com.surg.extract.mapper.MedicalRecordHomeMapper;
import com.surg.extract.mapper.SurgeryEntityMapper;
import com.surg.extract.mapper.SurgeryRecordMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SurgeryRecordService {

    private final SurgeryRecordMapper surgeryRecordMapper;
    private final SurgeryEntityMapper surgeryEntityMapper;
    private final MedicalRecordHomeMapper homePageMapper;
    private final FieldMappingMapper fieldMappingMapper;
    private final FileStorageService fileStorageService;
    private final NlpServiceClient nlpServiceClient;
    private final ObjectMapper objectMapper;

    @Transactional(rollbackFor = Exception.class)
    public RecordQueryDTO uploadRecord(MultipartFile file, RecordUploadDTO uploadDTO) {
        log.info("开始上传手术记录: fileName={}, patientName={}", file.getOriginalFilename(), uploadDTO.getPatientName());

        String filePath = fileStorageService.store(file);
        String fileType = fileStorageService.detectFileType(file.getOriginalFilename());

        SurgeryRecord record = new SurgeryRecord();
        record.setRecordNo("SR" + DateUtil.format(new Date(), "yyyyMMddHHmmss") + IdUtil.getSnowflakeNextIdStr().substring(12));
        record.setPatientId(uploadDTO.getPatientId());
        record.setPatientName(uploadDTO.getPatientName());
        record.setHospitalNo(uploadDTO.getHospitalNo());
        record.setDepartment(uploadDTO.getDepartment());
        record.setOriginalFileName(file.getOriginalFilename());
        record.setFileType(fileType);
        record.setFilePath(filePath);
        record.setFileSize(file.getSize());
        record.setUploadUserId(1L);
        record.setUploadUserName("当前用户");
        record.setUploadTime(LocalDateTime.now());
        record.setProcessStatus("PENDING");
        record.setManualDurationEst(600);
        record.setDeleted(0);

        surgeryRecordMapper.insert(record);
        log.info("手术记录已保存: id={}, recordNo={}", record.getId(), record.getRecordNo());

        startProcessingAsync(record.getId());

        return convertToQueryDTO(record);
    }

    @Async
    public void startProcessingAsync(Long recordId) {
        log.info("异步处理手术记录: recordId={}", recordId);
        try {
            SurgeryRecord record = surgeryRecordMapper.selectById(recordId);
            if (record == null) {
                log.warn("手术记录不存在: {}", recordId);
                return;
            }

            String filePath = record.getFilePath();
            if (filePath == null || filePath.isBlank()) {
                log.error("手术记录文件路径为空: recordId={}", recordId);
                surgeryRecordMapper.updateStatus(recordId, "FAILED", "文件路径为空，无法进行OCR处理");
                return;
            }

            surgeryRecordMapper.updateStatus(recordId, "OCR_PROCESSING", null);
            record.setOcrStartTime(LocalDateTime.now());

            NlpOcrResponse ocrResponse;
            if ("TEXT".equalsIgnoreCase(record.getFileType())) {
                ocrResponse = new NlpOcrResponse();
                ocrResponse.setSuccess(true);
                try {
                    String text = new String(fileStorageService.loadFile(filePath));
                    ocrResponse.setOcrText(text);
                    ocrResponse.setProcessedText(text);
                } catch (Exception e) {
                    log.error("读取TEXT文件失败: recordId={}, path={}", recordId, filePath, e);
                    ocrResponse.setSuccess(false);
                    ocrResponse.setErrorMessage("读取TEXT文件失败: " + e.getMessage());
                }
            } else {
                NlpOcrRequest ocrRequest = new NlpOcrRequest();
                ocrRequest.setRecordId(recordId);
                ocrRequest.setFilePath(filePath);
                ocrRequest.setFileType(record.getFileType());
                try {
                    ocrResponse = nlpServiceClient.processOcrByFilePath(ocrRequest);
                } catch (Exception e) {
                    log.error("调用NLP OCR服务失败: recordId={}", recordId, e);
                    ocrResponse = new NlpOcrResponse();
                    ocrResponse.setSuccess(false);
                    ocrResponse.setErrorMessage("NLP OCR服务调用失败: " + e.getMessage());
                }
            }

            record.setOcrEndTime(LocalDateTime.now());

            if (!Boolean.TRUE.equals(ocrResponse.getSuccess())) {
                String errorMsg = ocrResponse.getErrorMessage() != null
                        ? ocrResponse.getErrorMessage()
                        : "OCR处理失败，未知错误";
                log.error("OCR处理失败: recordId={}, message={}", recordId, errorMsg);
                surgeryRecordMapper.updateStatus(recordId, "FAILED", "OCR处理失败: " + errorMsg);
                return;
            }

            if (ocrResponse.getOcrText() == null || ocrResponse.getOcrText().isBlank()) {
                log.warn("OCR识别结果为空: recordId={}", recordId);
                surgeryRecordMapper.updateStatus(recordId, "FAILED",
                        "OCR识别结果为空，请上传清晰的手术记录文件");
                return;
            }

            record.setOcrText(ocrResponse.getOcrText());
            record.setProcessedText(ocrResponse.getProcessedText());
            surgeryRecordMapper.updateOcrTime(recordId, record.getOcrStartTime(), record.getOcrEndTime());
            surgeryRecordMapper.updateById(record);

            surgeryRecordMapper.updateStatus(recordId, "NER_PROCESSING", null);
            record.setNerStartTime(LocalDateTime.now());

            NlpNerRequest nerRequest = new NlpNerRequest();
            nerRequest.setRecordId(recordId);
            nerRequest.setText(record.getProcessedText());
            NlpNerResponse nerResponse;
            try {
                nerResponse = nlpServiceClient.extractEntities(nerRequest);
            } catch (Exception e) {
                log.error("调用NLP实体抽取服务失败: recordId={}", recordId, e);
                surgeryRecordMapper.updateStatus(recordId, "FAILED",
                        "NLP实体抽取服务调用失败: " + e.getMessage());
                return;
            }

            record.setNerEndTime(LocalDateTime.now());

            if (!Boolean.TRUE.equals(nerResponse.getSuccess())) {
                String errorMsg = nerResponse.getErrorMessage() != null
                        ? nerResponse.getErrorMessage()
                        : "实体抽取失败，未知错误";
                log.error("实体抽取失败: recordId={}, message={}", recordId, errorMsg);
                surgeryRecordMapper.updateStatus(recordId, "FAILED", "实体抽取失败: " + errorMsg);
                return;
            }

            saveEntities(recordId, nerResponse.getEntities());
            autoFillHomePage(recordId, nerResponse.getEntities());

            record.setNerStartTime(record.getNerStartTime());
            record.setNerEndTime(record.getNerEndTime());
            surgeryRecordMapper.updateNerTime(recordId, record.getNerStartTime(), record.getNerEndTime());
            surgeryRecordMapper.updateStatus(recordId, "NER_DONE", "处理完成");

            log.info("手术记录处理完成: recordId={}", recordId);
        } catch (Exception e) {
            log.error("处理手术记录异常: recordId={}", recordId, e);
            surgeryRecordMapper.updateStatus(recordId, "FAILED", "处理异常: " + e.getMessage());
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void saveEntities(Long recordId, List<NerEntityDTO> entities) {
        if (entities == null || entities.isEmpty()) {
            log.warn("无实体数据保存: recordId={}", recordId);
            return;
        }

        surgeryEntityMapper.deleteByRecordId(recordId);

        List<SurgeryEntity> entityList = new ArrayList<>();
        for (NerEntityDTO dto : entities) {
            SurgeryEntity entity = new SurgeryEntity();
            entity.setRecordId(recordId);
            entity.setEntityType(dto.getEntityType());
            entity.setEntityValue(dto.getEntityValue());
            entity.setEntityUnit(dto.getEntityUnit());
            entity.setConfidence(dto.getConfidence() != null ? BigDecimal.valueOf(dto.getConfidence()) : null);
            entity.setSource(dto.getSource() != null ? dto.getSource() : "MODEL");
            entity.setStartPos(dto.getStartPos());
            entity.setEndPos(dto.getEndPos());
            entity.setOriginalText(dto.getOriginalText());
            entity.setVerified(0);
            entity.setDeleted(0);
            entityList.add(entity);
        }
        surgeryEntityMapper.batchInsert(entityList);
        log.info("保存实体抽取结果: recordId={}, count={}", recordId, entityList.size());
    }

    @Transactional(rollbackFor = Exception.class)
    public void autoFillHomePage(Long recordId, List<NerEntityDTO> entities) {
        MedicalRecordHome existing = homePageMapper.selectByRecordId(recordId);
        MedicalRecordHome home = existing != null ? existing : new MedicalRecordHome();

        if (existing == null) {
            home.setRecordId(recordId);
            home.setStatus("DRAFT");
            home.setManualDurationEst(600);
        }

        Map<String, String> entityMap = entities.stream()
                .collect(Collectors.toMap(
                        NerEntityDTO::getEntityType,
                        NerEntityDTO::getEntityValue,
                        (a, b) -> a,
                        LinkedHashMap::new
                ));

        List<FieldMapping> mappings = fieldMappingMapper.selectEnabledMappings("medical_record_home");
        for (FieldMapping mapping : mappings) {
            String value = entityMap.get(mapping.getEntityType());
            if (!StringUtils.hasText(value)) continue;

            try {
                applyFieldMapping(home, mapping, value);
            } catch (Exception e) {
                log.warn("字段映射失败: field={}, value={}", mapping.getTargetField(), value, e);
            }
        }

        SurgeryRecord record = surgeryRecordMapper.selectById(recordId);
        if (record != null) {
            if (!StringUtils.hasText(home.getPatientName())) {
                home.setPatientName(record.getPatientName());
            }
            if (!StringUtils.hasText(home.getHospitalNo())) {
                home.setHospitalNo(record.getHospitalNo());
            }
            if (!StringUtils.hasText(home.getDepartment())) {
                home.setDepartment(record.getDepartment());
            }
        }

        if (existing == null) {
            homePageMapper.insert(home);
        } else {
            homePageMapper.updateById(home);
        }
        log.info("自动填充病案首页: recordId={}", recordId);
    }

    private void applyFieldMapping(MedicalRecordHome home, FieldMapping mapping, String value) {
        String field = mapping.getTargetField();
        String dataType = mapping.getDataType();

        switch (field) {
            case "patient_name" -> home.setPatientName(value);
            case "hospital_no" -> home.setHospitalNo(value);
            case "gender" -> home.setGender(value);
            case "age" -> home.setAge(parseInteger(value));
            case "surgery_date" -> home.setSurgeryDate(parseDateTime(value));
            case "surgery_name" -> home.setSurgeryName(value);
            case "surgery_level" -> home.setSurgeryLevel(value);
            case "incision_level" -> home.setIncisionLevel(value);
            case "incision_healing" -> home.setIncisionHealing(value);
            case "anesthesia_type" -> home.setAnesthesiaType(value);
            case "blood_loss" -> home.setBloodLoss(parseDecimal(value));
            case "blood_transfusion" -> home.setBloodTransfusion(parseDecimal(value));
            case "fluid_infusion" -> home.setFluidInfusion(parseDecimal(value));
            case "complications" -> home.setComplications(toJsonArray(value));
            case "surgeon" -> home.setSurgeon(value);
            case "assistant_1" -> home.setAssistant1(value);
            case "anesthesiologist" -> home.setAnesthesiologist(value);
            case "scrub_nurse" -> home.setScrubNurse(value);
            case "circulating_nurse" -> home.setCirculatingNurse(value);
            case "department" -> home.setDepartment(value);
            default -> log.debug("未处理的字段映射: {}", field);
        }
    }

    private Integer parseInteger(String value) {
        try {
            String num = value.replaceAll("[^0-9]", "");
            return StringUtils.hasText(num) ? Integer.parseInt(num) : null;
        } catch (Exception e) {
            return null;
        }
    }

    private BigDecimal parseDecimal(String value) {
        try {
            String num = value.replaceAll("[^0-9.]", "");
            return StringUtils.hasText(num) ? new BigDecimal(num) : null;
        } catch (Exception e) {
            return null;
        }
    }

    private LocalDateTime parseDateTime(String value) {
        try {
            value = value.replace("年", "-").replace("月", "-").replace("日", " ")
                    .replace("/", "-").replace(".", "-").trim();
            if (value.matches("\\d{4}-\\d{1,2}-\\d{1,2}")) {
                return LocalDate.parse(value).atStartOfDay();
            }
            if (value.matches("\\d{4}-\\d{1,2}-\\d{1,2}\\s+\\d{1,2}:\\d{1,2}")) {
                value += ":00";
            }
            return LocalDateTime.parse(value.replace(" ", "T"));
        } catch (Exception e) {
            return null;
        }
    }

    private String toJsonArray(String value) {
        try {
            List<String> list = Arrays.asList(value.split("[,，、；;\\s]+"));
            return objectMapper.writeValueAsString(list);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    public PageResult<RecordQueryDTO> queryRecords(String patientName, String hospitalNo,
                                                   String status, LocalDate startDate,
                                                   LocalDate endDate, Integer pageNum, Integer pageSize) {
        int offset = (pageNum - 1) * pageSize;
        List<SurgeryRecord> records = surgeryRecordMapper.selectByConditions(
                patientName, hospitalNo, status, startDate, endDate, offset, pageSize);
        Long total = surgeryRecordMapper.countByConditions(patientName, hospitalNo, status, startDate, endDate);

        List<RecordQueryDTO> dtoList = records.stream()
                .map(this::convertToQueryDTO)
                .collect(Collectors.toList());

        return PageResult.of(dtoList, total, pageNum, pageSize);
    }

    public RecordQueryDTO getRecordDetail(Long id) {
        SurgeryRecord record = surgeryRecordMapper.selectById(id);
        if (record == null) {
            throw new BusinessException(ErrorCode.RECORD_NOT_FOUND);
        }
        return convertToQueryDTO(record);
    }

    public String getRecordOcrText(Long id) {
        SurgeryRecord record = surgeryRecordMapper.selectById(id);
        if (record == null) {
            throw new BusinessException(ErrorCode.RECORD_NOT_FOUND);
        }
        return record.getProcessedText() != null ? record.getProcessedText() : record.getOcrText();
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateOcrText(Long id, String ocrText) {
        SurgeryRecord record = surgeryRecordMapper.selectById(id);
        if (record == null) {
            throw new BusinessException(ErrorCode.RECORD_NOT_FOUND);
        }
        record.setOcrText(ocrText);
        record.setProcessedText(ocrText);
        surgeryRecordMapper.updateById(record);
        log.info("更新OCR文本: recordId={}", id);
    }

    public List<SurgeryEntity> getRecordEntities(Long id) {
        return surgeryEntityMapper.selectByRecordId(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateEntity(Long recordId, List<EntityUpdateDTO> entities) {
        if (entities == null || entities.isEmpty()) return;

        for (EntityUpdateDTO dto : entities) {
            SurgeryEntity entity = surgeryEntityMapper.selectById(dto.getId());
            if (entity != null && entity.getRecordId().equals(recordId)) {
                if (dto.getEntityValue() != null) {
                    entity.setEntityValue(dto.getEntityValue());
                }
                if (dto.getEntityUnit() != null) {
                    entity.setEntityUnit(dto.getEntityUnit());
                }
                if (dto.getVerified() != null) {
                    entity.setVerified(dto.getVerified());
                    if (dto.getVerified() == 1) {
                        entity.setVerifiedBy(1L);
                        entity.setVerifiedTime(LocalDateTime.now());
                    }
                }
                entity.setSource("MANUAL");
                surgeryEntityMapper.updateById(entity);
            }
        }
        log.info("批量更新实体: recordId={}, count={}", recordId, entities.size());
    }

    private RecordQueryDTO convertToQueryDTO(SurgeryRecord record) {
        RecordQueryDTO dto = new RecordQueryDTO();
        BeanUtils.copyProperties(record, dto);
        return dto;
    }

    public Long getRecordProcessingTime(Long id) {
        SurgeryRecord record = surgeryRecordMapper.selectById(id);
        if (record == null) return null;
        return record.getFillDuration() != null ? record.getFillDuration().longValue() : null;
    }
}
