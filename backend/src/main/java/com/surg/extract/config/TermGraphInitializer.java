package com.surg.extract.config;

import com.surg.extract.service.MedicalTermGraphService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Order(100)
@RequiredArgsConstructor
public class TermGraphInitializer implements ApplicationRunner {

    private final MedicalTermGraphService graphService;

    @Override
    public void run(ApplicationArguments args) {
        try {
            log.info("开始同步术语图谱到Neo4j...");
            graphService.syncAllToGraph();
            log.info("术语图谱同步完成");
        } catch (Exception e) {
            log.error("术语图谱同步失败，将在首次调用时自动同步: {}", e.getMessage());
        }
    }
}
