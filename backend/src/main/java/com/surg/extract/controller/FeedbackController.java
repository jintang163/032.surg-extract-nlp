package com.surg.extract.controller;

import com.surg.extract.common.Result;
import com.surg.extract.dto.*;
import com.surg.extract.service.DoctorFeedbackService;
import com.surg.extract.service.ModelTrainService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/feedback")
@RequiredArgsConstructor
@Tag(name = "医生反馈管理", description = "记录医生对实体抽取结果的修正反馈，用于模型主动学习")
public class FeedbackController {

    private final DoctorFeedbackService feedbackService;
    private final ModelTrainService modelTrainService;

    @PostMapping
    @Operation(summary = "提交单条反馈", description = "记录医生对某条实体抽取结果的修正反馈")
    public Result<DoctorFeedbackDTO> createFeedback(
            @RequestBody DoctorFeedbackCreateDTO dto,
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestHeader(value = "X-User-Name", required = false) String userName) {
        DoctorFeedbackDTO result = feedbackService.createFeedback(dto, userId, userName);
        return Result.success("反馈已记录", result);
    }

    @PostMapping("/batch")
    @Operation(summary = "批量提交反馈", description = "批量记录医生对多条实体抽取结果的修正反馈")
    public Result<List<DoctorFeedbackDTO>> batchCreateFeedback(
            @RequestBody List<DoctorFeedbackCreateDTO> dtoList,
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestHeader(value = "X-User-Name", required = false) String userName) {
        List<DoctorFeedbackDTO> result = feedbackService.batchCreateFeedback(dtoList, userId, userName);
        return Result.success("批量反馈已记录，共" + result.size() + "条", result);
    }

    @GetMapping("/{id}")
    @Operation(summary = "获取反馈详情", description = "根据ID获取单条反馈详情")
    public Result<DoctorFeedbackDTO> getFeedbackById(@PathVariable Long id) {
        return Result.success(feedbackService.getFeedbackById(id));
    }

    @GetMapping("/record/{recordId}")
    @Operation(summary = "获取记录的反馈列表", description = "获取某条手术记录的所有反馈")
    public Result<List<DoctorFeedbackDTO>> getFeedbackByRecordId(@PathVariable Long recordId) {
        return Result.success(feedbackService.getFeedbackByRecordId(recordId));
    }

    @GetMapping("/list")
    @Operation(summary = "反馈列表", description = "分页查询医生反馈列表")
    public Result<PageResult<DoctorFeedbackDTO>> getFeedbackList(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") Integer pageNum,
            @Parameter(description = "每页条数") @RequestParam(defaultValue = "20") Integer pageSize,
            @Parameter(description = "手术记录ID") @RequestParam(required = false) Long recordId,
            @Parameter(description = "实体类型") @RequestParam(required = false) String entityType,
            @Parameter(description = "修正类型: CORRECTION-修改, ADDITION-新增, DELETION-删除") @RequestParam(required = false) String correctionType,
            @Parameter(description = "是否已用于训练: 0-未使用, 1-已使用") @RequestParam(required = false) Integer usedForTraining,
            @Parameter(description = "科室") @RequestParam(required = false) String department,
            @Parameter(description = "开始日期") @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @Parameter(description = "结束日期") @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate) {
        return Result.success(feedbackService.getFeedbackPage(
                pageNum, pageSize, recordId, entityType, correctionType,
                usedForTraining, department, startDate, endDate));
    }

    @GetMapping("/dashboard")
    @Operation(summary = "反馈统计仪表盘", description = "获取反馈统计总览、趋势图、实体类型分布、修正类型分布、Top医生、训练日志等")
    public Result<FeedbackDashboardDTO> getDashboard(
            @Parameter(description = "开始日期") @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @Parameter(description = "结束日期") @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate,
            @Parameter(description = "科室") @RequestParam(required = false) String department) {
        return Result.success(feedbackService.getDashboard(startDate, endDate, department));
    }

    @GetMapping("/trend")
    @Operation(summary = "反馈趋势数据", description = "按日期/科室维度获取反馈数量趋势")
    public Result<List<FeedbackTrendDTO>> getFeedbackTrend(
            @Parameter(description = "开始日期") @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @Parameter(description = "结束日期") @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate,
            @Parameter(description = "科室") @RequestParam(required = false) String department,
            @Parameter(description = "分组维度: day-按天, department-按科室") @RequestParam(defaultValue = "day") String groupBy) {
        return Result.success(feedbackService.getFeedbackTrend(startDate, endDate, department, groupBy));
    }

    @GetMapping("/stats/entity-type")
    @Operation(summary = "实体类型反馈统计", description = "按实体类型统计反馈数量、修正类型、平均置信度等")
    public Result<List<EntityFeedbackStatsDTO>> getEntityTypeStats(
            @Parameter(description = "开始日期") @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @Parameter(description = "结束日期") @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate,
            @Parameter(description = "科室") @RequestParam(required = false) String department) {
        return Result.success(feedbackService.getEntityTypeStats(startDate, endDate, department));
    }

    @GetMapping("/stats/correction-type")
    @Operation(summary = "修正类型分布统计", description = "统计各修正类型的数量和占比")
    public Result<List<CorrectionTypeStatsDTO>> getCorrectionTypeStats(
            @Parameter(description = "开始日期") @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @Parameter(description = "结束日期") @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate,
            @Parameter(description = "科室") @RequestParam(required = false) String department) {
        return Result.success(feedbackService.getCorrectionTypeStats(startDate, endDate, department));
    }

    @GetMapping("/stats/top-doctors")
    @Operation(summary = "贡献医生排行", description = "按反馈数量和质量评分排行")
    public Result<List<DoctorFeedbackStatsDTO>> getTopDoctors(
            @Parameter(description = "开始日期") @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @Parameter(description = "结束日期") @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate,
            @Parameter(description = "科室") @RequestParam(required = false) String department,
            @Parameter(description = "返回数量") @RequestParam(defaultValue = "10") Integer limit) {
        return Result.success(feedbackService.getTopDoctors(startDate, endDate, department, limit));
    }

    @GetMapping("/export/training-data")
    @Operation(summary = "导出训练数据", description = "导出待训练的反馈数据为TSV格式")
    public ResponseEntity<byte[]> exportTrainingData(
            @Parameter(description = "开始日期") @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @Parameter(description = "结束日期") @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate,
            @Parameter(description = "科室") @RequestParam(required = false) String department,
            @Parameter(description = "最低质量评分") @RequestParam(required = false) Integer minQualityScore,
            @Parameter(description = "最大导出数量") @RequestParam(required = false) Integer limit) {
        byte[] data = feedbackService.exportTrainingData(startDate, endDate, department, minQualityScore, limit);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/tab-separated-values; charset=UTF-8"));
        String filename = "feedback_training_data_" + LocalDate.now() + ".tsv";
        headers.setContentDispositionFormData("attachment", new String(filename.getBytes(StandardCharsets.UTF_8), StandardCharsets.ISO_8859_1));
        return ResponseEntity.ok().headers(headers).body(data);
    }

    @PostMapping("/train")
    @Operation(summary = "触发模型增量微调", description = "使用反馈数据触发NLP模型增量微调训练")
    public Result<ModelTrainLogDTO> triggerTraining(
            @RequestBody ModelTrainRequestDTO dto,
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestHeader(value = "X-User-Name", required = false) String userName) {
        ModelTrainLogDTO result = modelTrainService.triggerTraining(dto, userId, userName);
        return Result.success("训练任务已启动，正在后台执行", result);
    }

    @GetMapping("/train/{id}")
    @Operation(summary = "获取训练日志详情", description = "根据ID获取模型训练日志详情")
    public Result<ModelTrainLogDTO> getTrainLogById(@PathVariable Long id) {
        return Result.success(modelTrainService.getTrainLogById(id));
    }

    @GetMapping("/train/list")
    @Operation(summary = "训练日志列表", description = "分页查询模型训练日志")
    public Result<PageResult<ModelTrainLogDTO>> getTrainLogList(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") Integer pageNum,
            @Parameter(description = "每页条数") @RequestParam(defaultValue = "10") Integer pageSize,
            @Parameter(description = "训练状态: PENDING-待开始, RUNNING-训练中, SUCCESS-成功, FAILED-失败") @RequestParam(required = false) String trainStatus,
            @Parameter(description = "训练类型: FULL-全量, INCREMENTAL-增量, WEEKLY-周度") @RequestParam(required = false) String trainType,
            @Parameter(description = "开始日期") @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @Parameter(description = "结束日期") @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate) {
        return Result.success(modelTrainService.getTrainLogPage(
                pageNum, pageSize, trainStatus, trainType, startDate, endDate));
    }

    @GetMapping("/train/latest")
    @Operation(summary = "获取最新成功训练", description = "获取最近一次成功训练的模型信息")
    public Result<ModelTrainLogDTO> getLatestSuccess() {
        return Result.success(modelTrainService.getLatestSuccess());
    }

    @GetMapping("/train/recent")
    @Operation(summary = "获取最近训练日志", description = "获取最近N条训练日志")
    public Result<List<ModelTrainLogDTO>> getRecentTrainLogs(
            @Parameter(description = "返回数量") @RequestParam(defaultValue = "10") Integer limit) {
        return Result.success(modelTrainService.getRecentTrainLogs(limit));
    }
}
