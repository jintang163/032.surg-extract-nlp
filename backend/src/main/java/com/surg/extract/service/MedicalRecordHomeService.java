package com.surg.extract.service;

import com.surg.extract.common.BusinessException;
import com.surg.extract.common.ErrorCode;
import com.surg.extract.dto.HomePageUpdateDTO;
import com.surg.extract.entity.FieldMapping;
import com.surg.extract.entity.MedicalRecordHome;
import com.surg.extract.entity.SurgeryRecord;
import com.surg.extract.mapper.FieldMappingMapper;
import com.surg.extract.mapper.MedicalRecordHomeMapper;
import com.surg.extract.mapper.SurgeryRecordMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class MedicalRecordHomeService {

    private final MedicalRecordHomeMapper homePageMapper;
    private final SurgeryRecordMapper recordMapper;
    private final FieldMappingMapper fieldMappingMapper;
    private final ObjectMapper objectMapper;
    private final HisSyncService hisSyncService;

    public Map<String, Object> getHomePage(Long recordId) {
        MedicalRecordHome home = homePageMapper.selectByRecordId(recordId);
        if (home == null) {
            throw new BusinessException(ErrorCode.HOME_PAGE_NOT_FOUND);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            result = objectMapper.convertValue(home, new TypeReference<>() {});
            if (StringUtils.hasText(home.getComplications())) {
                List<String> complications = objectMapper.readValue(home.getComplications(),
                        new TypeReference<List<String>>() {});
                result.put("complications", complications);
            } else {
                result.put("complications", new ArrayList<>());
            }
        } catch (JsonProcessingException e) {
            log.warn("解析并发症失败", e);
        }
        return result;
    }

    public List<FieldMapping> getFieldMappings() {
        return fieldMappingMapper.selectEnabledMappings("medical_record_home");
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> updateHomePage(Long recordId, HomePageUpdateDTO dto) {
        validateRequiredFields(dto);

        MedicalRecordHome home = homePageMapper.selectByRecordId(recordId);
        if (home == null) {
            home = new MedicalRecordHome();
            home.setRecordId(recordId);
            home.setStatus("DRAFT");
        }

        home.setPatientName(dto.getPatientName());
        home.setGender(dto.getGender());
        home.setAge(dto.getAge());
        home.setHospitalNo(dto.getHospitalNo());
        home.setAdmissionDate(dto.getAdmissionDate());
        home.setDischargeDate(dto.getDischargeDate());
        home.setDepartment(dto.getDepartment());
        home.setSurgeryDate(dto.getSurgeryDate());
        home.setSurgeryName(dto.getSurgeryName());
        home.setSurgeryLevel(dto.getSurgeryLevel());
        home.setIncisionLevel(dto.getIncisionLevel());
        home.setIncisionHealing(dto.getIncisionHealing());
        home.setAnesthesiaType(dto.getAnesthesiaType());
        home.setBloodLoss(dto.getBloodLoss());
        home.setBloodTransfusion(dto.getBloodTransfusion());
        home.setFluidInfusion(dto.getFluidInfusion());
        home.setSurgeon(dto.getSurgeon());
        home.setChiefSurgeon(dto.getChiefSurgeon());
        home.setAssistant1(dto.getAssistant1());
        home.setAssistant2(dto.getAssistant2());
        home.setAnesthesiologist(dto.getAnesthesiologist());
        home.setScrubNurse(dto.getScrubNurse());
        home.setCirculatingNurse(dto.getCirculatingNurse());
        home.setCriticalPatient(dto.getCriticalPatient());
        home.setAdmissionDiagnosis(dto.getAdmissionDiagnosis());
        home.setDischargeDiagnosis(dto.getDischargeDiagnosis());
        home.setBedNo(dto.getBedNo());
        home.setAdmissionDays(dto.getAdmissionDays());
        home.setFillStartTime(dto.getFillStartTime());
        home.setFillEndTime(dto.getFillEndTime());

        if (dto.getFillDuration() != null) {
            home.setFillDuration(dto.getFillDuration());
        } else if (dto.getFillStartTime() != null && dto.getFillEndTime() != null) {
            home.setFillDuration((int) java.time.Duration.between(dto.getFillStartTime(), dto.getFillEndTime()).getSeconds());
        }

        if (dto.getComplications() != null) {
            try {
                home.setComplications(objectMapper.writeValueAsString(dto.getComplications()));
            } catch (JsonProcessingException e) {
                log.warn("序列化并发症失败", e);
            }
        }

        if (StringUtils.hasText(dto.getStatus())) {
            home.setStatus(dto.getStatus());
        }
        home.setFillUserId(1L);
        home.setFillUserName("当前用户");

        if (home.getId() == null) {
            homePageMapper.insert(home);
        } else {
            homePageMapper.updateById(home);
        }

        SurgeryRecord record = recordMapper.selectById(recordId);
        if (record != null) {
            record.setPatientName(home.getPatientName());
            record.setHospitalNo(home.getHospitalNo());
            record.setDepartment(home.getDepartment());
            if (home.getFillDuration() != null) {
                record.setFillDuration(home.getFillDuration());
            }
            recordMapper.updateById(record);
        }

        log.info("更新病案首页: recordId={}", recordId);
        return getHomePage(recordId);
    }

    @Transactional(rollbackFor = Exception.class)
    public void submitHomePage(Long recordId) {
        MedicalRecordHome home = homePageMapper.selectByRecordId(recordId);
        if (home == null) {
            throw new BusinessException(ErrorCode.HOME_PAGE_NOT_FOUND);
        }

        HomePageUpdateDTO validateDTO = new HomePageUpdateDTO();
        BeanUtils.copyProperties(home, validateDTO);
        validateRequiredFields(validateDTO);

        home.setStatus("PENDING");
        homePageMapper.updateById(home);

        SurgeryRecord record = recordMapper.selectById(recordId);
        if (record != null) {
            record.setProcessStatus("COMPLETED");
            record.setPatientConfirmed(1);
            record.setConfirmUserId(1L);
            record.setConfirmUserName("当前用户");
            record.setConfirmTime(LocalDateTime.now());
            recordMapper.updateById(record);
        }

        log.info("提交病案首页审核: recordId={}", recordId);
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> saveAsDraft(Long recordId, HomePageUpdateDTO dto) {
        dto.setStatus("DRAFT");
        return updateHomePage(recordId, dto);
    }

    @Transactional(rollbackFor = Exception.class)
    public void auditHomePage(Long recordId, boolean approved, String remark) {
        MedicalRecordHome home = homePageMapper.selectByRecordId(recordId);
        if (home == null) {
            throw new BusinessException(ErrorCode.HOME_PAGE_NOT_FOUND);
        }
        if (!"PENDING".equals(home.getStatus())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), "当前状态不可审核");
        }

        String newStatus = approved ? "APPROVED" : "REJECTED";
        homePageMapper.updateStatus(home.getId(), newStatus, 1L, "审核用户", LocalDateTime.now(), remark);

        if (approved) {
            try {
                boolean syncResult = hisSyncService.syncToHis(home);
                SurgeryRecord record = recordMapper.selectById(recordId);
                if (record != null) {
                    record.setHisSynced(syncResult ? 1 : 0);
                    record.setHisSyncTime(LocalDateTime.now());
                    record.setHisSyncMessage(syncResult ? "HIS同步成功" : "HIS同步失败");
                    recordMapper.updateById(record);
                }
            } catch (Exception e) {
                log.error("HIS同步失败", e);
            }
        }

        log.info("审核病案首页: recordId={}, approved={}", recordId, approved);
    }

    private void validateRequiredFields(HomePageUpdateDTO dto) {
        List<String> errors = new ArrayList<>();
        List<FieldMapping> mappings = fieldMappingMapper.selectEnabledMappings("medical_record_home");
        for (FieldMapping mapping : mappings) {
            if (mapping.getRequired() != null && mapping.getRequired() == 1) {
                String value = getFieldValue(dto, mapping.getTargetField());
                if (!StringUtils.hasText(value)) {
                    errors.add(mapping.getFieldLabel() + "不能为空");
                }
            }
        }
        if (!errors.isEmpty()) {
            throw new BusinessException(ErrorCode.REQUIRED_FIELD_MISSING.getCode(),
                    String.join("; ", errors));
        }
    }

    private String getFieldValue(HomePageUpdateDTO dto, String field) {
        return switch (field) {
            case "patient_name" -> dto.getPatientName();
            case "hospital_no" -> dto.getHospitalNo();
            case "gender" -> dto.getGender();
            case "age" -> dto.getAge() != null ? String.valueOf(dto.getAge()) : null;
            case "surgery_date" -> dto.getSurgeryDate() != null ? dto.getSurgeryDate().toString() : null;
            case "surgery_name" -> dto.getSurgeryName();
            case "incision_level" -> dto.getIncisionLevel();
            case "anesthesia_type" -> dto.getAnesthesiaType();
            case "surgeon" -> dto.getSurgeon();
            case "anesthesiologist" -> dto.getAnesthesiologist();
            default -> null;
        };
    }

    public Map<String, Object> getEfficiencyStats() {
        Map<String, Object> stats = new LinkedHashMap<>();

        List<SurgeryRecord> records = recordMapper.selectByConditions(
                null, null, null, null, null, 0, 10000);

        long totalCount = records.size();
        long autoFilledCount = records.stream()
                .filter(r -> "COMPLETED".equals(r.getProcessStatus()) || "NER_DONE".equals(r.getProcessStatus()))
                .count();
        long confirmedCount = records.stream()
                .filter(r -> r.getPatientConfirmed() != null && r.getPatientConfirmed() == 1)
                .count();

        Integer totalManualEst = records.stream()
                .filter(r -> r.getFillDuration() != null)
                .map(SurgeryRecord::getManualDurationEst)
                .filter(Objects::nonNull)
                .reduce(0, Integer::sum);

        Integer totalActual = records.stream()
                .map(SurgeryRecord::getFillDuration)
                .filter(Objects::nonNull)
                .reduce(0, Integer::sum);

        int savedSeconds = totalManualEst - totalActual;
        double efficiencyRate = totalManualEst > 0 ? (savedSeconds * 100.0 / totalManualEst) : 0;

        stats.put("totalRecords", totalCount);
        stats.put("autoFilledCount", autoFilledCount);
        stats.put("confirmedCount", confirmedCount);
        stats.put("totalManualEstSeconds", totalManualEst);
        stats.put("totalActualSeconds", totalActual);
        stats.put("savedSeconds", savedSeconds);
        stats.put("savedMinutes", savedSeconds / 60);
        stats.put("savedHours", savedSeconds / 3600);
        stats.put("efficiencyRate", String.format("%.1f%%", efficiencyRate));
        stats.put("avgFillSeconds", confirmedCount > 0 ? (totalActual / confirmedCount) : 0);

        return stats;
    }
}
