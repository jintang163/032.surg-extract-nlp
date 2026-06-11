package com.surg.extract.service;

import com.surg.extract.config.StorageProperties;
import com.surg.extract.common.BusinessException;
import com.surg.extract.common.ErrorCode;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileStorageService {

    private final StorageProperties storageProperties;

    private Path rootLocation;

    @PostConstruct
    public void init() {
        this.rootLocation = Paths.get(storageProperties.getPath()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.rootLocation);
            log.info("文件存储目录初始化: {}", this.rootLocation);
        } catch (IOException e) {
            throw new RuntimeException("无法创建文件存储目录: " + this.rootLocation, e);
        }
    }

    public String store(MultipartFile file) {
        String originalFilename = file.getOriginalFilename();
        if (!StringUtils.hasText(originalFilename)) {
            throw new BusinessException(ErrorCode.FILE_NOT_FOUND);
        }

        String extension = getFileExtension(originalFilename);
        if (!storageProperties.isAllowedType(extension)) {
            throw new BusinessException(ErrorCode.FILE_TYPE_NOT_SUPPORTED,
                    "不支持的文件类型: ." + extension + "，支持的类型: " + storageProperties.getAllowedTypes());
        }

        String dateDir = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        Path datePath = this.rootLocation.resolve(dateDir);
        try {
            Files.createDirectories(datePath);
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.FILE_UPLOAD_ERROR, "创建日期目录失败");
        }

        String newFilename = UUID.randomUUID().toString().replace("-", "") + "." + extension;
        Path targetLocation = datePath.resolve(newFilename);

        try {
            Files.copy(file.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);
            log.info("文件存储成功: {} -> {}", originalFilename, targetLocation);
            return targetLocation.toString();
        } catch (IOException e) {
            log.error("文件存储失败", e);
            throw new BusinessException(ErrorCode.FILE_UPLOAD_ERROR, "文件存储失败: " + e.getMessage());
        }
    }

    public String getFileExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf(".") + 1).toLowerCase();
    }

    public String detectFileType(String filename) {
        String ext = getFileExtension(filename);
        return switch (ext) {
            case "txt" -> "TEXT";
            case "doc", "docx" -> "WORD";
            case "pdf" -> "PDF";
            case "png", "jpg", "jpeg", "gif", "bmp", "tiff" -> "IMAGE";
            default -> "UNKNOWN";
        };
    }

    public boolean needsOcr(String filename) {
        String ext = getFileExtension(filename);
        return storageProperties.isOcrType(ext);
    }

    public byte[] loadFile(String filePath) {
        Path path = Paths.get(filePath);
        try {
            return Files.readAllBytes(path);
        } catch (IOException e) {
            log.error("读取文件失败: {}", filePath, e);
            throw new BusinessException(ErrorCode.FILE_READ_ERROR);
        }
    }

    public void deleteFile(String filePath) {
        if (!StringUtils.hasText(filePath)) return;
        try {
            Path path = Paths.get(filePath);
            Files.deleteIfExists(path);
            log.info("文件已删除: {}", filePath);
        } catch (IOException e) {
            log.warn("删除文件失败: {}", filePath, e);
        }
    }
}
