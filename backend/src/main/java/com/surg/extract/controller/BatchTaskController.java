package com.surg.extract.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.surg.extract.common.Result;
import com.surg.extract.dto.BatchTaskDTO;
import com.surg.extract.dto.BatchTaskItemDTO;
import com.surg.extract.dto.PageResult;
import com.surg.extract.service.BatchTaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;

@Slf4j
@RestController
@RequestMapping("/batch-tasks")
@RequiredArgsConstructor
@Tag(name = "批量任务管理", description = "批量上传手术记录、任务进度查询、批量填充病案首页")
public class BatchTaskController {

    private final BatchTaskService batchTaskService;

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "创建批量处理任务", description = "上传ZIP压缩包，系统异步处理所有手术记录文件")
    public Result<BatchTaskDTO> createBatchTask(
            @RequestPart("file") MultipartFile file,
            @Parameter(description = "任务名称") @RequestParam(required = false) String taskName,
            @Parameter(description = "科室") @RequestParam(required = false) String department,
            @Parameter(description = "通知邮箱，任务完成后发送邮件通知") @RequestParam(required = false) String notifyTarget,
            @Parameter(description = "最大重试次数") @RequestParam(defaultValue = "3") Integer maxRetryCount) {

        BatchTaskDTO result = batchTaskService.createBatchTask(
                file, taskName, department, "EMAIL", notifyTarget, maxRetryCount);
        return Result.success("批量任务已创建，正在后台处理", result);
    }

    @GetMapping("/list")
    @Operation(summary = "批量任务列表", description = "分页查询批量任务列表")
    public Result<PageResult<BatchTaskDTO>> getTaskList(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") Integer pageNum,
            @Parameter(description = "每页条数") @RequestParam(defaultValue = "10") Integer pageSize,
            @Parameter(description = "任务状态：PENDING-待处理 PROCESSING-处理中 COMPLETED-已完成 PARTIAL-部分完成 FAILED-全部失败") @RequestParam(required = false) String status,
            @Parameter(description = "科室") @RequestParam(required = false) String department,
            @Parameter(description = "开始日期") @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @Parameter(description = "结束日期") @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate) {

        IPage<BatchTaskDTO> page = batchTaskService.getTaskPage(pageNum, pageSize, status, department, startDate, endDate);
        PageResult<BatchTaskDTO> result = PageResult.of(
                page.getRecords(), page.getTotal(), pageNum, pageSize);
        return Result.success(result);
    }

    @GetMapping("/{id}")
    @Operation(summary = "任务详情", description = "获取批量任务详情，包含进度信息")
    public Result<BatchTaskDTO> getTaskDetail(@PathVariable Long id) {
        return Result.success(batchTaskService.getTaskDetail(id));
    }

    @GetMapping("/{id}/items")
    @Operation(summary = "任务文件列表", description = "分页查询批量任务的文件处理列表")
    public Result<PageResult<BatchTaskItemDTO>> getTaskItems(
            @PathVariable Long id,
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") Integer pageNum,
            @Parameter(description = "每页条数") @RequestParam(defaultValue = "20") Integer pageSize,
            @Parameter(description = "处理状态：PENDING/PROCESSING/SUCCESS/FAILED") @RequestParam(required = false) String status) {

        IPage<BatchTaskItemDTO> page = batchTaskService.getTaskItems(id, pageNum, pageSize, status);
        PageResult<BatchTaskItemDTO> result = PageResult.of(
                page.getRecords(), page.getTotal(), pageNum, pageSize);
        return Result.success(result);
    }

    @PostMapping("/{id}/retry")
    @Operation(summary = "重试失败项", description = "重试任务中处理失败的文件")
    public Result<BatchTaskDTO> retryFailedItems(@PathVariable Long id) {
        return Result.success("重试任务已启动", batchTaskService.retryFailedItems(id));
    }

    @PostMapping("/{id}/fill-home")
    @Operation(summary = "批量填充病案首页", description = "将任务中成功处理的记录批量填充病案首页")
    public Result<String> batchFillHomePages(@PathVariable Long id) {
        String result = batchTaskService.batchFillHomePages(id);
        return Result.success(result);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除任务", description = "删除批量任务（处理中的任务不可删除）")
    public Result<Void> deleteTask(@PathVariable Long id) {
        batchTaskService.deleteTask(id);
        return Result.success("删除成功");
    }
}
