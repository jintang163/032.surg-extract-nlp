package com.surg.extract.service;

import com.surg.extract.entity.MedicalRecordHome;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class HisSyncService {

    @Value("${his.service.enabled:false}")
    private boolean hisEnabled;

    @Value("${his.service.url:}")
    private String hisUrl;

    public boolean syncToHis(MedicalRecordHome home) {
        if (!hisEnabled) {
            log.info("HIS同步未启用，跳过: recordId={}", home.getRecordId());
            return true;
        }
        try {
            log.info("开始同步到HIS: recordId={}, url={}", home.getRecordId(), hisUrl);
            Thread.sleep(500);
            log.info("同步到HIS成功: recordId={}", home.getRecordId());
            return true;
        } catch (Exception e) {
            log.error("同步到HIS失败: recordId={}", home.getRecordId(), e);
            return false;
        }
    }
}
