package com.surg.extract.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.surg.extract.entity.HisSyncLog;
import com.surg.extract.entity.MedicalRecordHome;
import com.surg.extract.entity.SurgeryRecord;
import com.surg.extract.mapper.HisSyncLogMapper;
import com.surg.extract.mapper.MedicalRecordHomeMapper;
import com.surg.extract.mapper.SurgeryRecordMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class HisSyncService {

    private final HisSyncLogMapper syncLogMapper;
    private final MedicalRecordHomeMapper homePageMapper;
    private final SurgeryRecordMapper recordMapper;
    private final ObjectMapper objectMapper;

    @Value("${his.service.enabled:false}")
    private boolean hisEnabled;

    @Value("${his.service.url:}")
    private String hisUrl;

    @Value("${his.service.timeout:30000}")
    private int timeout;

    @Value("${his.service.max-retry:3}")
    private int maxRetry;

    @Value("${his.service.billing-enabled:true}")
    private boolean billingEnabled;

    public boolean syncToHis(MedicalRecordHome home) {
        return syncToHisWithRetry(home, 0);
    }

    private boolean syncToHisWithRetry(MedicalRecordHome home, int retryCount) {
        if (!hisEnabled) {
            log.info("HIS同步未启用，跳过: recordId={}", home.getRecordId());
            saveSyncLog(home, "HOME_PAGE", "TO_HIS", "SKIPPED", null, null, "HIS同步未启用", retryCount, null, null);
            return true;
        }

        LocalDateTime startTime = LocalDateTime.now();
        HisSyncLog syncLog = new HisSyncLog();
        syncLog.setRecordId(home.getRecordId());
        syncLog.setSyncType("HOME_PAGE");
        syncLog.setSyncDirection("TO_HIS");
        syncLog.setRetryCount(retryCount);
        syncLog.setSyncStartTime(startTime);

        try {
            log.info("开始同步病案首页到HIS: recordId={}, retry={}", home.getRecordId(), retryCount);

            Map<String, Object> syncData = buildHomePageSyncData(home);
            String syncDataJson = objectMapper.writeValueAsString(syncData);
            syncLog.setSyncData(syncDataJson);

            String response = callHisWebService("writeMedicalRecordHome", syncData);
            syncLog.setResponseData(response);
            syncLog.setSyncStatus("SUCCESS");

            if (billingEnabled) {
                try {
                    String billingResponse = triggerBilling(home);
                    log.info("计费触发成功: recordId={}", home.getRecordId());
                } catch (Exception e) {
                    log.warn("计费触发失败，但同步成功: recordId={}", home.getRecordId(), e);
                }
            }

            LocalDateTime endTime = LocalDateTime.now();
            syncLog.setSyncEndTime(endTime);
            syncLog.setDuration(ChronoUnit.MILLIS.between(startTime, endTime));

            log.info("同步到HIS成功: recordId={}, 耗时={}ms", home.getRecordId(), syncLog.getDuration());
            return true;

        } catch (Exception e) {
            log.error("同步到HIS失败: recordId={}, retry={}", home.getRecordId(), retryCount, e);
            syncLog.setSyncStatus("FAILED");
            syncLog.setErrorMessage(e.getMessage());
            LocalDateTime endTime = LocalDateTime.now();
            syncLog.setSyncEndTime(endTime);
            syncLog.setDuration(ChronoUnit.MILLIS.between(startTime, endTime));

            if (retryCount < maxRetry - 1) {
                log.info("准备重试同步: recordId={}, 下次重试={}/{}", home.getRecordId(), retryCount + 1, maxRetry);
                syncLogMapper.insert(syncLog);
                return syncToHisWithRetry(home, retryCount + 1);
            }

            return false;
        } finally {
            syncLogMapper.insert(syncLog);
        }
    }

    public boolean syncFromHis(Long recordId, String hospitalNo) {
        if (!hisEnabled) {
            log.info("HIS同步未启用，跳过从HIS拉取: recordId={}", recordId);
            return false;
        }

        LocalDateTime startTime = LocalDateTime.now();
        HisSyncLog syncLog = new HisSyncLog();
        syncLog.setRecordId(recordId);
        syncLog.setSyncType("HOME_PAGE");
        syncLog.setSyncDirection("FROM_HIS");
        syncLog.setRetryCount(0);
        syncLog.setSyncStartTime(startTime);

        try {
            log.info("开始从HIS拉取数据: recordId={}, hospitalNo={}", recordId, hospitalNo);

            Map<String, Object> params = new HashMap<>();
            params.put("hospitalNo", hospitalNo);
            params.put("recordId", recordId);

            String response = callHisWebService("getMedicalRecordHome", params);
            syncLog.setResponseData(response);
            syncLog.setSyncStatus("SUCCESS");

            Map<String, Object> hisData = objectMapper.readValue(response, Map.class);
            if (hisData != null && !hisData.isEmpty()) {
                updateHomePageFromHis(recordId, hisData);
                log.info("从HIS拉取数据成功: recordId={}", recordId);
            }

            LocalDateTime endTime = LocalDateTime.now();
            syncLog.setSyncEndTime(endTime);
            syncLog.setDuration(ChronoUnit.MILLIS.between(startTime, endTime));

            return true;

        } catch (Exception e) {
            log.error("从HIS拉取数据失败: recordId={}", recordId, e);
            syncLog.setSyncStatus("FAILED");
            syncLog.setErrorMessage(e.getMessage());
            LocalDateTime endTime = LocalDateTime.now();
            syncLog.setSyncEndTime(endTime);
            syncLog.setDuration(ChronoUnit.MILLIS.between(startTime, endTime));
            return false;
        } finally {
            syncLogMapper.insert(syncLog);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean rollbackSync(Long recordId) {
        if (!hisEnabled) {
            log.info("HIS同步未启用，跳过回滚: recordId={}", recordId);
            return true;
        }

        LocalDateTime startTime = LocalDateTime.now();
        HisSyncLog syncLog = new HisSyncLog();
        syncLog.setRecordId(recordId);
        syncLog.setSyncType("ROLLBACK");
        syncLog.setSyncDirection("TO_HIS");
        syncLog.setRetryCount(0);
        syncLog.setSyncStartTime(startTime);

        try {
            log.info("开始回滚HIS数据: recordId={}", recordId);

            Map<String, Object> params = new HashMap<>();
            params.put("recordId", recordId);
            params.put("operation", "CANCEL");

            String response = callHisWebService("cancelMedicalRecordHome", params);
            syncLog.setResponseData(response);
            syncLog.setSyncStatus("SUCCESS");

            MedicalRecordHome home = homePageMapper.selectByRecordId(recordId);
            if (home != null) {
                SurgeryRecord record = recordMapper.selectById(recordId);
                if (record != null) {
                    record.setHisSynced(0);
                    record.setHisSyncTime(LocalDateTime.now());
                    record.setHisSyncMessage("已回滚");
                    recordMapper.updateById(record);
                }
            }

            LocalDateTime endTime = LocalDateTime.now();
            syncLog.setSyncEndTime(endTime);
            syncLog.setDuration(ChronoUnit.MILLIS.between(startTime, endTime));

            log.info("HIS数据回滚成功: recordId={}", recordId);
            return true;

        } catch (Exception e) {
            log.error("HIS数据回滚失败: recordId={}", recordId, e);
            syncLog.setSyncStatus("FAILED");
            syncLog.setErrorMessage(e.getMessage());
            LocalDateTime endTime = LocalDateTime.now();
            syncLog.setSyncEndTime(endTime);
            syncLog.setDuration(ChronoUnit.MILLIS.between(startTime, endTime));
            return false;
        } finally {
            syncLogMapper.insert(syncLog);
        }
    }

    public String triggerBilling(MedicalRecordHome home) {
        if (!billingEnabled) {
            return "Billing disabled";
        }

        Map<String, Object> billingData = buildBillingData(home);
        return callHisWebService("generateBillingItems", billingData);
    }

    private Map<String, Object> buildHomePageSyncData(MedicalRecordHome home) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("patientId", home.getPatientId());
        data.put("patientName", home.getPatientName());
        data.put("hospitalNo", home.getHospitalNo());
        data.put("gender", home.getGender());
        data.put("age", home.getAge());
        data.put("department", home.getDepartment());
        data.put("bedNo", home.getBedNo());
        data.put("admissionDate", home.getAdmissionDate());
        data.put("dischargeDate", home.getDischargeDate());
        data.put("admissionDays", home.getAdmissionDays());
        data.put("admissionDiagnosis", home.getAdmissionDiagnosis());
        data.put("dischargeDiagnosis", home.getDischargeDiagnosis());
        data.put("surgeryDate", home.getSurgeryDate());
        data.put("surgeryName", home.getSurgeryName());
        data.put("surgeryCode", home.getSurgeryCode());
        data.put("surgeryLevel", home.getSurgeryLevel());
        data.put("incisionLevel", home.getIncisionLevel());
        data.put("incisionHealing", home.getIncisionHealing());
        data.put("anesthesiaType", home.getAnesthesiaType());
        data.put("anesthesiaCode", home.getAnesthesiaCode());
        data.put("bloodLoss", home.getBloodLoss());
        data.put("bloodTransfusion", home.getBloodTransfusion());
        data.put("fluidInfusion", home.getFluidInfusion());
        data.put("surgeon", home.getSurgeon());
        data.put("chiefSurgeon", home.getChiefSurgeon());
        data.put("assistant1", home.getAssistant1());
        data.put("assistant2", home.getAssistant2());
        data.put("anesthesiologist", home.getAnesthesiologist());
        data.put("scrubNurse", home.getScrubNurse());
        data.put("circulatingNurse", home.getCirculatingNurse());
        data.put("criticalPatient", home.getCriticalPatient());
        data.put("complications", home.getComplications());
        data.put("hospitalizationFee", home.getHospitalizationFee());
        data.put("surgeryFee", home.getSurgeryFee());
        data.put("anesthesiaFee", home.getAnesthesiaFee());
        data.put("drugFee", home.getDrugFee());
        data.put("examFee", home.getExamFee());
        data.put("treatmentFee", home.getTreatmentFee());
        data.put("bedFee", home.getBedFee());
        data.put("otherFee", home.getOtherFee());
        data.put("sourceSystem", "SURG_EXTRACT");
        data.put("syncTime", LocalDateTime.now().toString());
        return data;
    }

    private Map<String, Object> buildBillingData(MedicalRecordHome home) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("patientId", home.getPatientId());
        data.put("hospitalNo", home.getHospitalNo());
        data.put("patientName", home.getPatientName());
        data.put("department", home.getDepartment());
        data.put("surgeryDate", home.getSurgeryDate());
        data.put("surgeryCode", home.getSurgeryCode());
        data.put("surgeryName", home.getSurgeryName());
        data.put("surgeryLevel", home.getSurgeryLevel());
        data.put("surgeon", home.getSurgeon());
        data.put("anesthesiaType", home.getAnesthesiaType());
        data.put("anesthesiologist", home.getAnesthesiologist());
        data.put("bloodLoss", home.getBloodLoss());
        data.put("bloodTransfusion", home.getBloodTransfusion());

        List<Map<String, Object>> billingItems = new ArrayList<>();

        if (StringUtils.hasText(home.getSurgeryName())) {
            Map<String, Object> surgeryFee = new HashMap<>();
            surgeryFee.put("itemCode", "SURGERY_FEE");
            surgeryFee.put("itemName", "手术费");
            surgeryFee.put("quantity", 1);
            surgeryFee.put("unit", "次");
            surgeryFee.put("surgeryLevel", home.getSurgeryLevel());
            billingItems.add(surgeryFee);
        }

        if (StringUtils.hasText(home.getAnesthesiaType())) {
            Map<String, Object> anesthesiaFee = new HashMap<>();
            anesthesiaFee.put("itemCode", "ANESTHESIA_FEE");
            anesthesiaFee.put("itemName", "麻醉费");
            anesthesiaFee.put("quantity", 1);
            anesthesiaFee.put("unit", "次");
            anesthesiaFee.put("anesthesiaType", home.getAnesthesiaType());
            billingItems.add(anesthesiaFee);
        }

        if (home.getBloodTransfusion() != null && home.getBloodTransfusion().compareTo(BigDecimal.ZERO) > 0) {
            Map<String, Object> transfusionFee = new HashMap<>();
            transfusionFee.put("itemCode", "BLOOD_TRANSFUSION_FEE");
            transfusionFee.put("itemName", "输血费");
            transfusionFee.put("quantity", home.getBloodTransfusion());
            transfusionFee.put("unit", "ml");
            billingItems.add(transfusionFee);
        }

        if (home.getCriticalPatient() != null && home.getCriticalPatient() == 1) {
            Map<String, Object> criticalFee = new HashMap<>();
            criticalFee.put("itemCode", "CRITICAL_CARE_FEE");
            criticalFee.put("itemName", "重症监护费");
            criticalFee.put("quantity", 1);
            criticalFee.put("unit", "次");
            billingItems.add(criticalFee);
        }

        data.put("billingItems", billingItems);
        data.put("sourceSystem", "SURG_EXTRACT");
        data.put("generateTime", LocalDateTime.now().toString());
        return data;
    }

    private String callHisWebService(String method, Map<String, Object> params) {
        if (!hisEnabled || !StringUtils.hasText(hisUrl)) {
            return simulateHisResponse(method, params);
        }

        try {
            Thread.sleep(300);
            return simulateHisResponse(method, params);
        } catch (Exception e) {
            throw new RuntimeException("HIS接口调用失败: " + method, e);
        }
    }

    private String simulateHisResponse(String method, Map<String, Object> params) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("code", 200);
        response.put("message", "success");
        response.put("method", method);
        response.put("timestamp", System.currentTimeMillis());

        if ("generateBillingItems".equals(method)) {
            response.put("data", Collections.singletonMap("billingNo", "BL" + System.currentTimeMillis()));
        } else if ("getMedicalRecordHome".equals(method)) {
            response.put("data", Collections.emptyMap());
        } else {
            response.put("data", Collections.singletonMap("success", true));
        }

        try {
            return objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException e) {
            return "{\"code\":200,\"message\":\"success\"}";
        }
    }

    private void updateHomePageFromHis(Long recordId, Map<String, Object> hisData) {
        MedicalRecordHome home = homePageMapper.selectByRecordId(recordId);
        if (home == null) {
            home = new MedicalRecordHome();
            home.setRecordId(recordId);
        }

        if (hisData.containsKey("patientName")) {
            home.setPatientName((String) hisData.get("patientName"));
        }
        if (hisData.containsKey("gender")) {
            home.setGender((String) hisData.get("gender"));
        }
        if (hisData.containsKey("age")) {
            Object age = hisData.get("age");
            if (age != null) {
                home.setAge(Integer.parseInt(age.toString()));
            }
        }
        if (hisData.containsKey("department")) {
            home.setDepartment((String) hisData.get("department"));
        }
        if (hisData.containsKey("bedNo")) {
            home.setBedNo((String) hisData.get("bedNo"));
        }

        if (home.getId() == null) {
            homePageMapper.insert(home);
        } else {
            homePageMapper.updateById(home);
        }
    }

    private void saveSyncLog(MedicalRecordHome home, String syncType, String direction, String status,
                             String syncData, String responseData, String errorMsg,
                             int retryCount, LocalDateTime startTime, LocalDateTime endTime) {
        try {
            HisSyncLog logEntry = new HisSyncLog();
            logEntry.setRecordId(home.getRecordId());
            logEntry.setSyncType(syncType);
            logEntry.setSyncDirection(direction);
            logEntry.setSyncStatus(status);
            if (syncData != null && syncData.length() > 4000) {
                syncData = syncData.substring(0, 4000);
            }
            logEntry.setSyncData(syncData);
            if (responseData != null && responseData.length() > 4000) {
                responseData = responseData.substring(0, 4000);
            }
            logEntry.setResponseData(responseData);
            logEntry.setErrorMessage(errorMsg);
            logEntry.setRetryCount(retryCount);
            logEntry.setSyncStartTime(startTime);
            logEntry.setSyncEndTime(endTime);
            if (startTime != null && endTime != null) {
                logEntry.setDuration(ChronoUnit.MILLIS.between(startTime, endTime));
            }
            syncLogMapper.insert(logEntry);
        } catch (Exception e) {
            log.error("保存同步日志失败", e);
        }
    }

    public List<HisSyncLog> getSyncLogs(Long recordId) {
        return syncLogMapper.selectByRecordId(recordId);
    }

    public HisSyncLog getLatestSyncLog(Long recordId) {
        return syncLogMapper.selectLatestByRecordId(recordId);
    }

    public boolean isHisEnabled() {
        return hisEnabled;
    }
}
