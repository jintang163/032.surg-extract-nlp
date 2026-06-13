package com.surg.extract.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.surg.extract.dto.HisMessageDTO.*;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class HisSyncService {

    private final HisApiClientService hisApiClient;
    private final HisSyncLogMapper syncLogMapper;
    private final MedicalRecordHomeMapper homePageMapper;
    private final SurgeryRecordMapper recordMapper;
    private final ObjectMapper objectMapper;

    @Value("${his.service.enabled:false}")
    private boolean hisEnabled;

    @Value("${his.service.max-retry:3}")
    private int maxRetry;

    @Value("${his.service.billing-enabled:true}")
    private boolean billingEnabled;

    public boolean syncToHis(MedicalRecordHome home) {
        return syncToHis(home, null, null);
    }

    public boolean syncToHis(MedicalRecordHome home, String operatorId, String operatorName) {
        if (!hisEnabled) {
            log.info("HIS同步未启用，跳过: recordId={}", home.getRecordId());
            saveSimpleLog(home.getRecordId(), "HOME_PAGE", "TO_HIS", "SKIPPED", null, null, "HIS同步未启用");
            updateSyncStatus(home.getRecordId(), 0, "HIS同步未启用");
            return true;
        }

        LocalDateTime startTime = LocalDateTime.now();
        HisSyncLog syncLog = createSyncLog(home.getRecordId(), "HOME_PAGE", "TO_HIS", 0, startTime);
        syncLog.setCreatedUserId(operatorId != null ? Long.parseLong(operatorId) : null);
        syncLog.setCreatedUserName(operatorName);

        try {
            log.info("开始同步病案首页到HIS: recordId={}", home.getRecordId());

            HomePageWriteBody body = buildHomePageWriteBody(home);
            String requestJson = objectMapper.writeValueAsString(body);
            syncLog.setSyncData(truncate(requestJson, 4000));

            HisResponse<HisHomePageData> response = hisApiClient.writeHomePage(body, operatorId, operatorName);
            String responseJson = objectMapper.writeValueAsString(response);
            syncLog.setResponseData(truncate(responseJson, 4000));

            if (!isSuccess(response)) {
                syncLog.setSyncStatus("FAILED");
                syncLog.setErrorMessage(response.getMessage());
                syncLog.setSyncEndTime(LocalDateTime.now());
                syncLog.setDuration(ChronoUnit.MILLIS.between(startTime, syncLog.getSyncEndTime()));
                syncLogMapper.insert(syncLog);

                updateSyncStatus(home.getRecordId(), 2, "同步失败: " + response.getMessage());
                return false;
            }

            syncLog.setSyncStatus("SUCCESS");
            syncLog.setSyncEndTime(LocalDateTime.now());
            syncLog.setDuration(ChronoUnit.MILLIS.between(startTime, syncLog.getSyncEndTime()));
            syncLogMapper.insert(syncLog);

            updateSyncStatus(home.getRecordId(), 1, "同步成功");

            if (billingEnabled) {
                triggerBillingWithLog(home, operatorId, operatorName);
            }

            log.info("同步到HIS成功: recordId={}, 耗时={}ms", home.getRecordId(), syncLog.getDuration());
            return true;

        } catch (Exception e) {
            log.error("同步到HIS失败: recordId={}", home.getRecordId(), e);
            syncLog.setSyncStatus("FAILED");
            syncLog.setErrorMessage(truncate(e.getMessage(), 2000));
            syncLog.setSyncEndTime(LocalDateTime.now());
            syncLog.setDuration(ChronoUnit.MILLIS.between(startTime, syncLog.getSyncEndTime()));
            syncLogMapper.insert(syncLog);

            updateSyncStatus(home.getRecordId(), 2, "同步失败: " + e.getMessage());
            return false;
        }
    }

    public boolean syncFromHis(Long recordId, String hospitalNo) {
        return syncFromHis(recordId, hospitalNo, null, null);
    }

    public boolean syncFromHis(Long recordId, String hospitalNo, String operatorId, String operatorName) {
        if (!hisEnabled) {
            log.info("HIS同步未启用，跳过从HIS拉取: recordId={}", recordId);
            return false;
        }

        LocalDateTime startTime = LocalDateTime.now();
        HisSyncLog syncLog = createSyncLog(recordId, "HOME_PAGE", "FROM_HIS", 0, startTime);
        syncLog.setCreatedUserId(operatorId != null ? Long.parseLong(operatorId) : null);
        syncLog.setCreatedUserName(operatorName);

        try {
            log.info("开始从HIS拉取数据: recordId={}, hospitalNo={}", recordId, hospitalNo);

            PullRequestBody body = new PullRequestBody();
            body.setHospitalNo(hospitalNo);
            String requestJson = objectMapper.writeValueAsString(body);
            syncLog.setSyncData(truncate(requestJson, 4000));

            HisResponse<HisHomePageData> response = hisApiClient.pullHomePage(body, operatorId, operatorName);
            String responseJson = objectMapper.writeValueAsString(response);
            syncLog.setResponseData(truncate(responseJson, 4000));

            if (!isSuccess(response)) {
                syncLog.setSyncStatus("FAILED");
                syncLog.setErrorMessage(response.getMessage());
                syncLog.setSyncEndTime(LocalDateTime.now());
                syncLog.setDuration(ChronoUnit.MILLIS.between(startTime, syncLog.getSyncEndTime()));
                syncLogMapper.insert(syncLog);
                return false;
            }

            HisHomePageData hisData = response.getData();
            if (hisData != null && StringUtils.hasText(hisData.getHospitalNo())) {
                applyHisDataToHomePage(recordId, hisData);
                syncLog.setSyncStatus("SUCCESS");
                log.info("从HIS拉取并应用数据成功: recordId={}", recordId);
            } else {
                syncLog.setSyncStatus("SUCCESS");
                log.info("HIS返回数据为空: recordId={}", recordId);
            }

            syncLog.setSyncEndTime(LocalDateTime.now());
            syncLog.setDuration(ChronoUnit.MILLIS.between(startTime, syncLog.getSyncEndTime()));
            syncLogMapper.insert(syncLog);

            updateSyncStatus(recordId, 1, "从HIS同步成功");
            return true;

        } catch (Exception e) {
            log.error("从HIS拉取数据失败: recordId={}", recordId, e);
            syncLog.setSyncStatus("FAILED");
            syncLog.setErrorMessage(truncate(e.getMessage(), 2000));
            syncLog.setSyncEndTime(LocalDateTime.now());
            syncLog.setDuration(ChronoUnit.MILLIS.between(startTime, syncLog.getSyncEndTime()));
            syncLogMapper.insert(syncLog);
            return false;
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean rollbackSync(Long recordId) {
        return rollbackSync(recordId, null, null);
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean rollbackSync(Long recordId, String operatorId, String operatorName) {
        if (!hisEnabled) {
            log.info("HIS同步未启用，跳过回滚: recordId={}", recordId);
            return true;
        }

        MedicalRecordHome home = homePageMapper.selectByRecordId(recordId);
        if (home == null) {
            log.warn("病案首页不存在，无法回滚: recordId={}", recordId);
            return false;
        }

        HisSyncLog lastSyncLog = syncLogMapper.selectLatestByRecordId(recordId);

        LocalDateTime startTime = LocalDateTime.now();
        HisSyncLog syncLog = createSyncLog(recordId, "ROLLBACK", "TO_HIS", 0, startTime);
        syncLog.setCreatedUserId(operatorId != null ? Long.parseLong(operatorId) : null);
        syncLog.setCreatedUserName(operatorName);

        try {
            log.info("开始回滚HIS数据: recordId={}", recordId);

            RollbackRequestBody body = new RollbackRequestBody();
            body.setHospitalNo(home.getHospitalNo());
            body.setCancelReason("操作员手动回滚");
            if (lastSyncLog != null) {
                body.setOriginalMessageId(String.valueOf(lastSyncLog.getId()));
            }

            String requestJson = objectMapper.writeValueAsString(body);
            syncLog.setSyncData(truncate(requestJson, 4000));

            HisResponse<Object> response = hisApiClient.rollback(body, operatorId, operatorName);
            String responseJson = objectMapper.writeValueAsString(response);
            syncLog.setResponseData(truncate(responseJson, 4000));

            if (!isSuccess(response)) {
                syncLog.setSyncStatus("FAILED");
                syncLog.setErrorMessage(response.getMessage());
                syncLog.setSyncEndTime(LocalDateTime.now());
                syncLog.setDuration(ChronoUnit.MILLIS.between(startTime, syncLog.getSyncEndTime()));
                syncLogMapper.insert(syncLog);

                updateSyncStatus(recordId, 3, "回滚失败: " + response.getMessage());
                return false;
            }

            syncLog.setSyncStatus("SUCCESS");
            syncLog.setSyncEndTime(LocalDateTime.now());
            syncLog.setDuration(ChronoUnit.MILLIS.between(startTime, syncLog.getSyncEndTime()));
            syncLogMapper.insert(syncLog);

            if (billingEnabled) {
                rollbackBillingWithLog(recordId, home, operatorId, operatorName);
            }

            updateSyncStatus(recordId, 0, "已回滚");
            log.info("HIS数据回滚成功: recordId={}", recordId);
            return true;

        } catch (Exception e) {
            log.error("HIS数据回滚失败: recordId={}", recordId, e);
            syncLog.setSyncStatus("FAILED");
            syncLog.setErrorMessage(truncate(e.getMessage(), 2000));
            syncLog.setSyncEndTime(LocalDateTime.now());
            syncLog.setDuration(ChronoUnit.MILLIS.between(startTime, syncLog.getSyncEndTime()));
            syncLogMapper.insert(syncLog);

            updateSyncStatus(recordId, 3, "回滚失败: " + e.getMessage());
            return false;
        }
    }

    public String triggerBilling(MedicalRecordHome home) {
        return triggerBilling(home, null, null);
    }

    public String triggerBilling(MedicalRecordHome home, String operatorId, String operatorName) {
        if (!billingEnabled) {
            return "Billing disabled";
        }
        try {
            triggerBillingWithLog(home, operatorId, operatorName);
            return "Billing triggered successfully";
        } catch (Exception e) {
            log.error("触发计费失败", e);
            return "Billing failed: " + e.getMessage();
        }
    }

    private void triggerBillingWithLog(MedicalRecordHome home, String operatorId, String operatorName) {
        LocalDateTime startTime = LocalDateTime.now();
        HisSyncLog billingLog = createSyncLog(home.getRecordId(), "BILLING", "TO_HIS", 0, startTime);
        billingLog.setCreatedUserId(operatorId != null ? Long.parseLong(operatorId) : null);
        billingLog.setCreatedUserName(operatorName);

        try {
            BillingRequestBody body = buildBillingRequestBody(home);
            String requestJson = objectMapper.writeValueAsString(body);
            billingLog.setSyncData(truncate(requestJson, 4000));

            HisResponse<Object> response = hisApiClient.generateBilling(body, operatorId, operatorName);
            String responseJson = objectMapper.writeValueAsString(response);
            billingLog.setResponseData(truncate(responseJson, 4000));

            if (!isSuccess(response)) {
                billingLog.setSyncStatus("FAILED");
                billingLog.setErrorMessage("计费失败: " + response.getMessage());
                log.warn("计费触发失败但首页同步已成功: recordId={}, error={}", home.getRecordId(), response.getMessage());
            } else {
                billingLog.setSyncStatus("SUCCESS");
                log.info("计费触发成功: recordId={}", home.getRecordId());
            }

            billingLog.setSyncEndTime(LocalDateTime.now());
            billingLog.setDuration(ChronoUnit.MILLIS.between(startTime, billingLog.getSyncEndTime()));
            syncLogMapper.insert(billingLog);

        } catch (Exception e) {
            log.warn("计费触发异常: recordId={}", home.getRecordId(), e);
            billingLog.setSyncStatus("FAILED");
            billingLog.setErrorMessage(truncate("计费异常: " + e.getMessage(), 2000));
            billingLog.setSyncEndTime(LocalDateTime.now());
            billingLog.setDuration(ChronoUnit.MILLIS.between(startTime, billingLog.getSyncEndTime()));
            syncLogMapper.insert(billingLog);
        }
    }

    private void rollbackBillingWithLog(Long recordId, MedicalRecordHome home, String operatorId, String operatorName) {
        LocalDateTime startTime = LocalDateTime.now();
        HisSyncLog billingRollbackLog = createSyncLog(recordId, "BILLING_ROLLBACK", "TO_HIS", 0, startTime);
        billingRollbackLog.setCreatedUserId(operatorId != null ? Long.parseLong(operatorId) : null);
        billingRollbackLog.setCreatedUserName(operatorName);

        try {
            RollbackRequestBody body = new RollbackRequestBody();
            body.setHospitalNo(home.getHospitalNo());
            body.setCancelType("BILLING_ONLY");
            body.setCancelReason("同步回滚-取消计费");

            String requestJson = objectMapper.writeValueAsString(body);
            billingRollbackLog.setSyncData(truncate(requestJson, 4000));

            HisResponse<Object> response = hisApiClient.rollback(body, operatorId, operatorName);
            String responseJson = objectMapper.writeValueAsString(response);
            billingRollbackLog.setResponseData(truncate(responseJson, 4000));

            if (!isSuccess(response)) {
                billingRollbackLog.setSyncStatus("FAILED");
                billingRollbackLog.setErrorMessage("计费回滚失败: " + response.getMessage());
            } else {
                billingRollbackLog.setSyncStatus("SUCCESS");
            }

            billingRollbackLog.setSyncEndTime(LocalDateTime.now());
            billingRollbackLog.setDuration(ChronoUnit.MILLIS.between(startTime, billingRollbackLog.getSyncEndTime()));
            syncLogMapper.insert(billingRollbackLog);

        } catch (Exception e) {
            log.warn("计费回滚异常: recordId={}", recordId, e);
            billingRollbackLog.setSyncStatus("FAILED");
            billingRollbackLog.setErrorMessage(truncate("计费回滚异常: " + e.getMessage(), 2000));
            billingRollbackLog.setSyncEndTime(LocalDateTime.now());
            billingRollbackLog.setDuration(ChronoUnit.MILLIS.between(startTime, billingRollbackLog.getSyncEndTime()));
            syncLogMapper.insert(billingRollbackLog);
        }
    }

    private HomePageWriteBody buildHomePageWriteBody(MedicalRecordHome home) {
        HomePageWriteBody body = new HomePageWriteBody();
        body.setHospitalNo(home.getHospitalNo());
        body.setPatientName(home.getPatientName());
        body.setGender(home.getGender());
        body.setAge(home.getAge());
        body.setDepartment(home.getDepartment());
        body.setBedNo(home.getBedNo());
        body.setAdmissionDate(formatDate(home.getAdmissionDate()));
        body.setDischargeDate(formatDate(home.getDischargeDate()));
        body.setAdmissionDays(home.getAdmissionDays());
        body.setAdmissionDiagnosis(home.getAdmissionDiagnosis());
        body.setDischargeDiagnosis(home.getDischargeDiagnosis());
        body.setSurgeryDate(formatDateTime(home.getSurgeryDate()));
        body.setSurgeryName(home.getSurgeryName());
        body.setSurgeryCode(home.getSurgeryCode());
        body.setSurgeryLevel(home.getSurgeryLevel());
        body.setIncisionLevel(home.getIncisionLevel());
        body.setIncisionHealing(home.getIncisionHealing());
        body.setAnesthesiaType(home.getAnesthesiaType());
        body.setAnesthesiaCode(home.getAnesthesiaCode());
        body.setBloodLoss(home.getBloodLoss());
        body.setBloodTransfusion(home.getBloodTransfusion());
        body.setFluidInfusion(home.getFluidInfusion());
        body.setSurgeon(home.getSurgeon());
        body.setChiefSurgeon(home.getChiefSurgeon());
        body.setAssistant1(home.getAssistant1());
        body.setAssistant2(home.getAssistant2());
        body.setAnesthesiologist(home.getAnesthesiologist());
        body.setScrubNurse(home.getScrubNurse());
        body.setCirculatingNurse(home.getCirculatingNurse());
        body.setCriticalPatient(home.getCriticalPatient());
        body.setComplications(home.getComplications());
        return body;
    }

    private BillingRequestBody buildBillingRequestBody(MedicalRecordHome home) {
        BillingRequestBody body = new BillingRequestBody();
        body.setHospitalNo(home.getHospitalNo());
        body.setPatientName(home.getPatientName());
        body.setDepartment(home.getDepartment());
        body.setSurgeryDate(formatDateTime(home.getSurgeryDate()));

        List<BillingItem> items = new ArrayList<>();

        if (StringUtils.hasText(home.getSurgeryName())) {
            BillingItem item = new BillingItem();
            item.setItemCode("SURGERY_FEE");
            item.setItemName("手术费-" + home.getSurgeryName());
            item.setQuantity(BigDecimal.ONE);
            item.setUnit("次");
            items.add(item);
        }

        if (StringUtils.hasText(home.getAnesthesiaType())) {
            BillingItem item = new BillingItem();
            item.setItemCode("ANESTHESIA_FEE");
            item.setItemName("麻醉费-" + home.getAnesthesiaType());
            item.setQuantity(BigDecimal.ONE);
            item.setUnit("次");
            items.add(item);
        }

        if (home.getBloodTransfusion() != null && home.getBloodTransfusion().compareTo(BigDecimal.ZERO) > 0) {
            BillingItem item = new BillingItem();
            item.setItemCode("BLOOD_TRANSFUSION_FEE");
            item.setItemName("输血费");
            item.setQuantity(home.getBloodTransfusion());
            item.setUnit("ml");
            items.add(item);
        }

        if (home.getCriticalPatient() != null && home.getCriticalPatient() == 1) {
            BillingItem item = new BillingItem();
            item.setItemCode("CRITICAL_CARE_FEE");
            item.setItemName("重症监护费");
            item.setQuantity(BigDecimal.ONE);
            item.setUnit("次");
            items.add(item);
        }

        body.setItems(items);
        return body;
    }

    private void applyHisDataToHomePage(Long recordId, HisHomePageData hisData) {
        MedicalRecordHome home = homePageMapper.selectByRecordId(recordId);
        if (home == null) {
            home = new MedicalRecordHome();
            home.setRecordId(recordId);
        }

        if (StringUtils.hasText(hisData.getHospitalNo())) home.setHospitalNo(hisData.getHospitalNo());
        if (StringUtils.hasText(hisData.getPatientName())) home.setPatientName(hisData.getPatientName());
        if (StringUtils.hasText(hisData.getGender())) home.setGender(hisData.getGender());
        if (hisData.getAge() != null) home.setAge(hisData.getAge());
        if (StringUtils.hasText(hisData.getDepartment())) home.setDepartment(hisData.getDepartment());
        if (StringUtils.hasText(hisData.getBedNo())) home.setBedNo(hisData.getBedNo());
        if (StringUtils.hasText(hisData.getAdmissionDate())) {
            home.setAdmissionDate(parseDate(hisData.getAdmissionDate()));
        }
        if (StringUtils.hasText(hisData.getDischargeDate())) {
            home.setDischargeDate(parseDate(hisData.getDischargeDate()));
        }
        if (hisData.getAdmissionDays() != null) home.setAdmissionDays(hisData.getAdmissionDays());
        if (StringUtils.hasText(hisData.getAdmissionDiagnosis())) home.setAdmissionDiagnosis(hisData.getAdmissionDiagnosis());
        if (StringUtils.hasText(hisData.getDischargeDiagnosis())) home.setDischargeDiagnosis(hisData.getDischargeDiagnosis());
        if (StringUtils.hasText(hisData.getSurgeryDate())) {
            home.setSurgeryDate(parseDateTime(hisData.getSurgeryDate()));
        }
        if (StringUtils.hasText(hisData.getSurgeryName())) home.setSurgeryName(hisData.getSurgeryName());
        if (StringUtils.hasText(hisData.getSurgeryCode())) home.setSurgeryCode(hisData.getSurgeryCode());
        if (StringUtils.hasText(hisData.getSurgeryLevel())) home.setSurgeryLevel(hisData.getSurgeryLevel());
        if (StringUtils.hasText(hisData.getIncisionLevel())) home.setIncisionLevel(hisData.getIncisionLevel());
        if (StringUtils.hasText(hisData.getIncisionHealing())) home.setIncisionHealing(hisData.getIncisionHealing());
        if (StringUtils.hasText(hisData.getAnesthesiaType())) home.setAnesthesiaType(hisData.getAnesthesiaType());
        if (StringUtils.hasText(hisData.getSurgeon())) home.setSurgeon(hisData.getSurgeon());
        if (StringUtils.hasText(hisData.getChiefSurgeon())) home.setChiefSurgeon(hisData.getChiefSurgeon());
        if (StringUtils.hasText(hisData.getAnesthesiologist())) home.setAnesthesiologist(hisData.getAnesthesiologist());
        if (hisData.getBloodLoss() != null) home.setBloodLoss(hisData.getBloodLoss());
        if (hisData.getBloodTransfusion() != null) home.setBloodTransfusion(hisData.getBloodTransfusion());
        if (hisData.getCriticalPatient() != null) home.setCriticalPatient(hisData.getCriticalPatient());
        if (StringUtils.hasText(hisData.getComplications())) home.setComplications(hisData.getComplications());

        if (home.getId() == null) {
            homePageMapper.insert(home);
        } else {
            homePageMapper.updateById(home);
        }
    }

    private void updateSyncStatus(Long recordId, int syncStatus, String message) {
        try {
            SurgeryRecord record = recordMapper.selectById(recordId);
            if (record != null) {
                record.setHisSynced(syncStatus);
                record.setHisSyncTime(LocalDateTime.now());
                record.setHisSyncMessage(truncate(message, 200));
                recordMapper.updateById(record);
            }
        } catch (Exception e) {
            log.error("更新同步状态失败: recordId={}", recordId, e);
        }
    }

    private HisSyncLog createSyncLog(Long recordId, String syncType, String syncDirection, int retryCount, LocalDateTime startTime) {
        HisSyncLog syncLog = new HisSyncLog();
        syncLog.setRecordId(recordId);
        syncLog.setSyncType(syncType);
        syncLog.setSyncDirection(syncDirection);
        syncLog.setRetryCount(retryCount);
        syncLog.setSyncStartTime(startTime);
        return syncLog;
    }

    private void saveSimpleLog(Long recordId, String syncType, String direction, String status,
                               String syncData, String responseData, String errorMsg) {
        try {
            HisSyncLog logEntry = new HisSyncLog();
            logEntry.setRecordId(recordId);
            logEntry.setSyncType(syncType);
            logEntry.setSyncDirection(direction);
            logEntry.setSyncStatus(status);
            logEntry.setSyncData(truncate(syncData, 4000));
            logEntry.setResponseData(truncate(responseData, 4000));
            logEntry.setErrorMessage(errorMsg);
            logEntry.setRetryCount(0);
            logEntry.setSyncStartTime(LocalDateTime.now());
            logEntry.setSyncEndTime(LocalDateTime.now());
            logEntry.setDuration(0L);
            syncLogMapper.insert(logEntry);
        } catch (Exception e) {
            log.error("保存同步日志失败", e);
        }
    }

    private boolean isSuccess(HisResponse<?> response) {
        return response != null && ("200".equals(response.getCode()) || "0".equals(response.getCode()));
    }

    private String truncate(String str, int maxLen) {
        if (str == null) return null;
        return str.length() > maxLen ? str.substring(0, maxLen) : str;
    }

    private String formatDate(LocalDate date) {
        return date != null ? date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) : null;
    }

    private String formatDateTime(LocalDateTime dateTime) {
        return dateTime != null ? dateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) : null;
    }

    private LocalDate parseDate(String dateStr) {
        if (!StringUtils.hasText(dateStr)) return null;
        try {
            return LocalDate.parse(dateStr.substring(0, 10), DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        } catch (Exception e) {
            try {
                return LocalDate.parse(dateStr.substring(0, 10));
            } catch (Exception ex) {
                log.warn("日期解析失败: {}", dateStr);
                return null;
            }
        }
    }

    private LocalDateTime parseDateTime(String dateTimeStr) {
        if (!StringUtils.hasText(dateTimeStr)) return null;
        try {
            return LocalDateTime.parse(dateTimeStr.substring(0, 19), DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        } catch (Exception e) {
            try {
                return LocalDateTime.parse(dateTimeStr.substring(0, 19));
            } catch (Exception ex) {
                log.warn("日期时间解析失败: {}", dateTimeStr);
                return null;
            }
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
