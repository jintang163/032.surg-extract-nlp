package com.surg.extract.schedule;

import com.surg.extract.dto.ModelTrainRequestDTO;
import com.surg.extract.service.ModelTrainService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class WeeklyTrainScheduler {

    private final ModelTrainService modelTrainService;

    @Scheduled(cron = "0 0 2 ? * MON")
    public void weeklyIncrementalTrain() {
        log.info("===== 周度自动增量训练定时任务开始 =====");
        try {
            ModelTrainRequestDTO req = new ModelTrainRequestDTO();
            req.setTrainType("WEEKLY");
            req.setEpochs(10);
            req.setBatchSize(16);
            req.setLearningRate(0.001);
            req.setMaxFeedbackCount(5000);
            req.setMinQualityScore(60);
            req.setRemark("周度自动调度训练");

            modelTrainService.triggerTraining(req, null, "SYSTEM_SCHEDULER");
            log.info("周度自动增量训练任务已触发");
        } catch (Exception e) {
            log.error("周度自动增量训练任务失败", e);
        }
        log.info("===== 周度自动增量训练定时任务结束 =====");
    }
}
