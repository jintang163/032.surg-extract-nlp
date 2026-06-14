package com.surg.extract;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@SpringBootApplication
@MapperScan("com.surg.extract.mapper")
@EnableFeignClients(basePackages = "com.surg.extract.feign")
@EnableAsync
@EnableScheduling
@EnableTransactionManagement
public class SurgExtractApplication {

    public static void main(String[] args) {
        SpringApplication.run(SurgExtractApplication.class, args);
    }
}
