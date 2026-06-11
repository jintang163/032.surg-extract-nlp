package com.surg.extract.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.List;

@Data
@Configuration
@ConfigurationProperties(prefix = "storage.file")
public class StorageProperties {

    private String path = "./data/uploads";

    private String allowedTypes = "txt,doc,docx,pdf,png,jpg,jpeg,gif,bmp,tiff";

    private String ocrTypes = "pdf,png,jpg,jpeg,gif,bmp,tiff,doc,docx";

    public List<String> getAllowedTypeList() {
        return Arrays.asList(allowedTypes.toLowerCase().split(","));
    }

    public List<String> getOcrTypeList() {
        return Arrays.asList(ocrTypes.toLowerCase().split(","));
    }

    public boolean isAllowedType(String fileExtension) {
        if (fileExtension == null) return false;
        return getAllowedTypeList().contains(fileExtension.toLowerCase());
    }

    public boolean isOcrType(String fileExtension) {
        if (fileExtension == null) return false;
        return getOcrTypeList().contains(fileExtension.toLowerCase());
    }
}
