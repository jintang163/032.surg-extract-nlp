package com.surg.extract.service;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.surg.extract.common.BusinessException;
import com.surg.extract.common.ErrorCode;
import com.surg.extract.dto.*;
import com.surg.extract.entity.BatchTask;
import com.surg.extract.entity.BatchTaskItem;
import com.surg.extract.mapper.BatchTaskItemMapper;
import com.surg.extract.mapper.BatchTaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Slf4j
@Service
@RequiredArgsConstructor
public class BatchTaskService {

    private final BatchTaskMapper batchTaskMapper;
    private final BatchTaskItemMapper batchTaskItemMapper;
    private final SurgeryRecordService surgeryRecordService;
    private final MedicalRecordHomeService homeService;
    private final FileStorageService fileStorageService;
    private final NotificationService notificationService;

    @Autowired
    private ApplicationContext applicationContext;

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "txt", "doc", "docx", "pdf", "png", "jpg", "jpeg", "gif", "bmp", "tiff", "tif"
    );

    private static final int CONCURRENT_PROCESSING_LIMIT = 4;
    private static final Map<Long, Object> TASK_LOCKS = new ConcurrentHashMap<>();

    @Transactional(rollbackFor = Exception.class)
    public BatchTaskDTO createBatchTask(MultipartFile zipFile, String taskName, String department,
                                        String notifyType, String notifyTarget, Integer maxRetryCount) {
        String originalFileName = zipFile.getOriginalFilename();
        if (originalFileName == null || !originalFileName.toLowerCase().endsWith(".zip")) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "请上传 ZIP 压缩包文件");
        }

        String filePath = fileStorageService.store(zipFile);
        String baseTaskName = StringUtils.hasText(taskName) ? taskName :
                "批量处理_" + DateUtil.format(new Date(), "yyyyMMddHHmmss");

        List<FileEntry> extractedFiles = extractZip(filePath);
        if (extractedFiles.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "ZIP 包中未找到有效的手术记录文件");
        }

        BatchTask task = new BatchTask();
        task.setTaskName(baseTaskName);
        task.setDepartment(department);
        task.setTaskType("SURGERY_RECORD");
        task.setOriginalFileName(originalFileName);
        task.setFilePath(filePath);
        task.setFileSize(zipFile.getSize());
        task.setTotalCount(extractedFiles.size());
        task.setSuccessCount(0);
        task.setFailedCount(0);
        task.setPendingCount(extractedFiles.size());
        task.setStatus("PENDING");
        task.setNotifyType("EMAIL");
        task.setNotifyTarget(notifyTarget);
        task.setNotified(false);
        task.setRetryCount(0);
        task.setMaxRetryCount(maxRetryCount != null ? maxRetryCount : 3);
        task.setCreatedBy(1L);
        task.setCreatedByName("当前用户");
        task.setDeleted(0);

        batchTaskMapper.insert(task);

        for (FileEntry entry : extractedFiles) {
            BatchTaskItem item = new BatchTaskItem();
            item.setTaskId(task.getId());
            item.setFileName(entry.fileName);
            item.setFilePath(entry.storedPath);
            item.setFileType(entry.fileType);
            item.setPatientName(extractPatientName(entry.fileName));
            item.setStatus("PENDING");
            item.setRetryCount(0);
            item.setDeleted(0);
            batchTaskItemMapper.insert(item);
        }

        log.info("批量任务已创建: taskId={}, totalFiles={}", task.getId(), extractedFiles.size());

        BatchTaskService proxy = applicationContext.getBean(BatchTaskService.class);
        proxy.startBatchProcessing(task.getId());

        return convertToDTO(task);
    }

    @Async("batchTaskExecutor")
    public void startBatchProcessing(Long taskId) {
        Object lock = TASK_LOCKS.computeIfAbsent(taskId, k -> new Object());
        synchronized (lock) {
            try {
                BatchTask task = batchTaskMapper.selectById(taskId);
                if (task == null) return;

                if ("PROCESSING".equals(task.getStatus())) {
                    log.warn("任务 {} 已在处理中，跳过", taskId);
                    return;
                }

                task.setStatus("PROCESSING");
                task.setStartTime(LocalDateTime.now());
                batchTaskMapper.updateById(task);

                boolean hasMore = true;
                while (hasMore) {
                    List<BatchTaskItem> pendingItems = batchTaskItemMapper.selectPendingItems(taskId, CONCURRENT_PROCESSING_LIMIT);
                    if (pendingItems.isEmpty()) {
                        hasMore = false;
                        break;
                    }

                    for (BatchTaskItem item : pendingItems) {
                        processSingleItem(task, item);
                    }

                    batchTaskMapper.updateTaskProgress(taskId);
                    task = batchTaskMapper.selectById(taskId);
                }

                int successCount = batchTaskMapper.countSuccessItems(taskId);
                int failedCount = batchTaskMapper.countFailedItems(taskId);
                int totalCount = task.getTotalCount() == null ? 0 : task.getTotalCount();

                String finalStatus;
                if (successCount > 0 && failedCount == 0) {
                    finalStatus = "COMPLETED";
                } else if (successCount > 0 && failedCount > 0) {
                    finalStatus = "PARTIAL";
                } else if (successCount == 0 && failedCount > 0) {
                    finalStatus = "FAILED";
                } else {
                    finalStatus = "COMPLETED";
                }

                task.setStatus(finalStatus);
                task.setEndTime(LocalDateTime.now());
                batchTaskMapper.updateById(task);

                if (!Boolean.TRUE.equals(task.getNotified()) && StringUtils.hasText(task.getNotifyTarget())) {
                    notificationService.sendBatchCompleteNotification(
                            task.getNotifyType(),
                            task.getNotifyTarget(),
                            task.getTaskName(),
                            totalCount,
                            successCount,
                            failedCount
                    );
                    task.setNotified(true);
                    batchTaskMapper.updateById(task);
                }

                log.info("批量任务完成: taskId={}, status={}, total={}, success={}, failed={}",
                        taskId, finalStatus, totalCount, successCount, failedCount);

            } catch (Exception e) {
                log.error("批量任务处理异常: taskId={}", taskId, e);
                BatchTask task = batchTaskMapper.selectById(taskId);
                if (task != null) {
                    task.setStatus("FAILED");
                    task.setErrorMessage(e.getMessage());
                    task.setEndTime(LocalDateTime.now());
                    batchTaskMapper.updateById(task);
                }
            } finally {
                TASK_LOCKS.remove(taskId);
            }
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void processSingleItem(BatchTask task, BatchTaskItem item) {
        try {
            item.setStartTime(LocalDateTime.now());
            item.setStatus("PROCESSING");
            batchTaskItemMapper.updateById(item);

            Path filePath = Paths.get(fileStorageService.getBasePath(), item.getFilePath());
            if (!Files.exists(filePath)) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "文件不存在: " + item.getFilePath());
            }

            byte[] fileContent = Files.readAllBytes(filePath);
            File file = filePath.toFile();

            MultipartFile multipartFile = new InMemoryMultipartFile(
                    item.getFileName(),
                    item.getFileName(),
                    Files.probeContentType(filePath),
                    fileContent
            );

            RecordUploadDTO uploadDTO = new RecordUploadDTO();
            uploadDTO.setFileName(item.getFileName());
            uploadDTO.setPatientName(item.getPatientName());
            uploadDTO.setDepartment(task.getDepartment());

            RecordQueryDTO result = surgeryRecordService.uploadRecord(multipartFile, uploadDTO);

            item.setRecordId(result.getId());
            item.setPatientName(result.getPatientName());
            item.setHospitalNo(result.getHospitalNo());
            item.setStatus("SUCCESS");
            item.setEndTime(LocalDateTime.now());
            batchTaskItemMapper.updateById(item);

            log.info("文件处理成功: taskId={}, itemId={}, fileName={}, recordId={}",
                    task.getId(), item.getId(), item.getFileName(), result.getId());

        } catch (Exception e) {
            log.error("文件处理失败: taskId={}, itemId={}, fileName={}",
                    task.getId(), item.getId(), item.getFileName(), e);

            int newRetryCount = (item.getRetryCount() == null ? 0 : item.getRetryCount()) + 1;
            item.setRetryCount(newRetryCount);
            item.setErrorMessage(e.getMessage());

            int maxRetry = task.getMaxRetryCount() != null ? task.getMaxRetryCount() : 3;
            if (newRetryCount < maxRetry) {
                item.setStatus("PENDING");
                log.info("文件将重试: itemId={}, retryCount={}", item.getId(), newRetryCount);
            } else {
                item.setStatus("FAILED");
                item.setEndTime(LocalDateTime.now());
            }
            batchTaskItemMapper.updateById(item);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public BatchTaskDTO retryFailedItems(Long taskId) {
        BatchTask task = batchTaskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "任务不存在");
        }
        if ("PROCESSING".equals(task.getStatus())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "任务正在处理中，请稍后再试");
        }

        List<BatchTaskItem> failedItems = batchTaskItemMapper.selectFailedItems(taskId);
        if (failedItems.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "没有需要重试的失败项");
        }

        for (BatchTaskItem item : failedItems) {
            item.setStatus("PENDING");
            item.setErrorMessage(null);
            batchTaskItemMapper.updateById(item);
        }

        task.setRetryCount((task.getRetryCount() == null ? 0 : task.getRetryCount()) + 1);
        task.setPendingCount(task.getPendingCount() + failedItems.size());
        task.setFailedCount(task.getFailedCount() - failedItems.size());
        task.setNotified(false);
        batchTaskMapper.updateById(task);

        BatchTaskService proxy = applicationContext.getBean(BatchTaskService.class);
        proxy.startBatchProcessing(taskId);

        return getTaskDetail(taskId);
    }

    @Transactional(rollbackFor = Exception.class)
    public String batchFillHomePages(Long taskId) {
        List<Long> successRecordIds = batchTaskItemMapper.selectSuccessRecordIds(taskId);
        if (successRecordIds.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "没有可批量填充的成功记录");
        }

        int filledCount = 0;
        int failedCount = 0;

        for (Long recordId : successRecordIds) {
            try {
                homeService.autoFillHomePage(recordId);
                filledCount++;
            } catch (Exception e) {
                log.error("批量填充病案首页失败: recordId={}", recordId, e);
                failedCount++;
            }
        }

        return String.format("批量填充完成：成功 %d 条，失败 %d 条", filledCount, failedCount);
    }

    public IPage<BatchTaskDTO> getTaskPage(int pageNum, int pageSize, String status,
                                           String department, LocalDate startDate, LocalDate endDate) {
        Page<BatchTaskDTO> page = new Page<>(pageNum, pageSize);
        IPage<BatchTaskDTO> result = batchTaskMapper.selectTaskPage(page, status, department, 1L, startDate, endDate);
        for (BatchTaskDTO dto : result.getRecords()) {
            dto.setProgress(calculateProgress(dto));
        }
        return result;
    }

    public BatchTaskDTO getTaskDetail(Long taskId) {
        BatchTask task = batchTaskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "任务不存在");
        }
        BatchTaskDTO dto = convertToDTO(task);
        dto.setSuccessCount(batchTaskMapper.countSuccessItems(taskId));
        dto.setFailedCount(batchTaskMapper.countFailedItems(taskId));
        dto.setPendingCount(batchTaskMapper.countPendingItems(taskId));
        dto.setProgress(calculateProgress(dto));
        return dto;
    }

    public IPage<BatchTaskItemDTO> getTaskItems(Long taskId, int pageNum, int pageSize, String status) {
        Page<BatchTaskItemDTO> page = new Page<>(pageNum, pageSize);
        return batchTaskItemMapper.selectItemPage(page, taskId, status);
    }

    public void deleteTask(Long taskId) {
        BatchTask task = batchTaskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "任务不存在");
        }
        if ("PROCESSING".equals(task.getStatus())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "任务正在处理中，无法删除");
        }
        batchTaskMapper.deleteById(taskId);
    }

    private List<FileEntry> extractZip(String zipFilePath) {
        List<FileEntry> result = new ArrayList<>();
        Path basePath = Paths.get(fileStorageService.getBasePath());
        String extractDir = "batch_" + IdUtil.getSnowflakeNextIdStr();
        Path extractPath = basePath.resolve(extractDir);

        try {
            Files.createDirectories(extractPath);

            try (ZipInputStream zis = new ZipInputStream(new FileInputStream(basePath.resolve(zipFilePath).toFile()))) {
                ZipEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    if (entry.isDirectory()) continue;

                    String fileName = new File(entry.getName()).getName();
                    String ext = getFileExtension(fileName).toLowerCase();
                    if (!ALLOWED_EXTENSIONS.contains(ext)) {
                        log.info("跳过不支持的文件: {}", fileName);
                        continue;
                    }

                    Path targetFile = extractPath.resolve(fileName);
                    int duplicateCount = 1;
                    while (Files.exists(targetFile)) {
                        String baseName = fileName.substring(0, fileName.lastIndexOf('.'));
                        targetFile = extractPath.resolve(baseName + "_" + duplicateCount + "." + ext);
                        duplicateCount++;
                    }

                    try (FileOutputStream fos = new FileOutputStream(targetFile.toFile())) {
                        byte[] buffer = new byte[8192];
                        int len;
                        while ((len = zis.read(buffer)) > 0) {
                            fos.write(buffer, 0, len);
                        }
                    }

                    result.add(new FileEntry(
                            fileName,
                            basePath.relativize(targetFile).toString().replace("\\", "/"),
                            detectFileType(fileName)
                    ));

                    zis.closeEntry();
                }
            }
        } catch (Exception e) {
            log.error("ZIP 解压失败", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "ZIP 解压失败: " + e.getMessage());
        }

        return result;
    }

    private String getFileExtension(String fileName) {
        int dotIdx = fileName.lastIndexOf('.');
        return dotIdx > 0 ? fileName.substring(dotIdx + 1) : "";
    }

    private String detectFileType(String fileName) {
        String ext = getFileExtension(fileName).toLowerCase();
        return switch (ext) {
            case "txt" -> "TEXT";
            case "doc", "docx" -> "WORD";
            case "pdf" -> "PDF";
            case "png", "jpg", "jpeg", "gif", "bmp", "tiff", "tif" -> "IMAGE";
            default -> "UNKNOWN";
        };
    }

    private String extractPatientName(String fileName) {
        String name = fileName.replaceAll("\\.[^.]+$", "");
        name = name.replaceAll("[\\d_\\-\\s]+", " ").trim();
        return name.length() > 50 ? name.substring(0, 50) : name;
    }

    private Integer calculateProgress(BatchTaskDTO dto) {
        if (dto.getTotalCount() == null || dto.getTotalCount() == 0) return 0;
        int done = (dto.getSuccessCount() == null ? 0 : dto.getSuccessCount()) +
                (dto.getFailedCount() == null ? 0 : dto.getFailedCount());
        return (int) (done * 100.0 / dto.getTotalCount());
    }

    private BatchTaskDTO convertToDTO(BatchTask task) {
        BatchTaskDTO dto = new BatchTaskDTO();
        dto.setId(task.getId());
        dto.setTaskName(task.getTaskName());
        dto.setDepartment(task.getDepartment());
        dto.setTaskType(task.getTaskType());
        dto.setOriginalFileName(task.getOriginalFileName());
        dto.setFileSize(task.getFileSize());
        dto.setTotalCount(task.getTotalCount());
        dto.setSuccessCount(task.getSuccessCount());
        dto.setFailedCount(task.getFailedCount());
        dto.setPendingCount(task.getPendingCount());
        dto.setStatus(task.getStatus());
        dto.setErrorMessage(task.getErrorMessage());
        dto.setNotifyType(task.getNotifyType());
        dto.setNotifyTarget(task.getNotifyTarget());
        dto.setRetryCount(task.getRetryCount());
        dto.setStartTime(task.getStartTime());
        dto.setEndTime(task.getEndTime());
        dto.setCreatedByName(task.getCreatedByName());
        dto.setCreatedTime(task.getCreatedTime());
        return dto;
    }

    private record FileEntry(String fileName, String storedPath, String fileType) {}

    private static class InMemoryMultipartFile implements MultipartFile {
        private final String name;
        private final String originalFilename;
        private final String contentType;
        private final byte[] content;

        public InMemoryMultipartFile(String name, String originalFilename, String contentType, byte[] content) {
            this.name = name;
            this.originalFilename = originalFilename;
            this.contentType = contentType;
            this.content = content;
        }

        @Override
        public String getName() { return name; }
        @Override
        public String getOriginalFilename() { return originalFilename; }
        @Override
        public String getContentType() { return contentType; }
        @Override
        public boolean isEmpty() { return content.length == 0; }
        @Override
        public long getSize() { return content.length; }
        @Override
        public byte[] getBytes() { return content; }
        @Override
        public InputStream getInputStream() { return new ByteArrayInputStream(content); }
        @Override
        public void transferTo(File dest) throws IOException {
            Files.write(dest.toPath(), content);
        }
    }
}
