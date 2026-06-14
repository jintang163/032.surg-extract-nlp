package com.surg.extract.controller;

import com.surg.extract.common.Result;
import com.surg.extract.dto.*;
import com.surg.extract.service.AnalyticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/analytics")
@RequiredArgsConstructor
@Tag(name = "统计分析仪表盘", description = "质控科监控系统效果：覆盖率、效率、准确率趋势，词云和异常分布")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    @GetMapping("/dashboard")
    @Operation(summary = "获取仪表盘全量数据", description = "一次性返回仪表盘所需的全部统计数据")
    public Result<Map<String, Object>> getDashboard(
            @Parameter(description = "开始日期") @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @Parameter(description = "结束日期") @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate,
            @Parameter(description = "科室过滤") @RequestParam(required = false) String department) {
        return Result.success(analyticsService.getFullDashboard(startDate, endDate, department));
    }

    @GetMapping("/overview")
    @Operation(summary = "总览数据", description = "获取总体覆盖率、节省率、准确率等指标")
    public Result<AnalyticsOverviewDTO> getOverview(
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate,
            @RequestParam(required = false) String department) {
        return Result.success(analyticsService.getOverview(startDate, endDate, department));
    }

    @GetMapping("/trend/coverage")
    @Operation(summary = "覆盖率趋势", description = "按日期/科室维度的结构化提取覆盖率趋势")
    public Result<List<CoverageTrendDTO>> getCoverageTrend(
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate,
            @RequestParam(required = false) String department,
            @RequestParam(required = false) String groupBy) {
        return Result.success(analyticsService.getCoverageTrend(startDate, endDate, department, groupBy));
    }

    @GetMapping("/trend/efficiency")
    @Operation(summary = "时间节省率趋势", description = "按日期/科室/医生维度的平均填充时间节省率趋势")
    public Result<List<EfficiencyTrendDTO>> getEfficiencyTrend(
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate,
            @RequestParam(required = false) String department,
            @RequestParam(required = false) String surgeon,
            @RequestParam(required = false) String groupBy) {
        return Result.success(analyticsService.getEfficiencyTrend(startDate, endDate, department, surgeon, groupBy));
    }

    @GetMapping("/trend/accuracy")
    @Operation(summary = "字段识别准确率趋势", description = "按日期/科室/字段类型维度的识别准确率趋势")
    public Result<List<AccuracyTrendDTO>> getAccuracyTrend(
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate,
            @RequestParam(required = false) String department,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) String groupBy) {
        return Result.success(analyticsService.getAccuracyTrend(startDate, endDate, department, entityType, groupBy));
    }

    @GetMapping("/department/stats")
    @Operation(summary = "各科室统计", description = "各科室的覆盖率、平均节省率、准确率")
    public Result<List<DepartmentStatsDTO>> getDepartmentStats(
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate) {
        return Result.success(analyticsService.getDepartmentStats(startDate, endDate));
    }

    @GetMapping("/surgeon/stats")
    @Operation(summary = "医生维度统计", description = "按手术医生维度下钻查看覆盖率、节省率")
    public Result<List<SurgeonStatsDTO>> getSurgeonStats(
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate,
            @RequestParam(required = false) String department,
            @RequestParam(required = false) Integer limit) {
        return Result.success(analyticsService.getSurgeonStats(startDate, endDate, department, limit));
    }

    @GetMapping("/surgery-type/stats")
    @Operation(summary = "手术类型维度统计", description = "按手术名称维度下钻查看覆盖率、准确率")
    public Result<List<SurgeryTypeStatsDTO>> getSurgeryTypeStats(
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate,
            @RequestParam(required = false) String department,
            @RequestParam(required = false) Integer limit) {
        return Result.success(analyticsService.getSurgeryTypeStats(startDate, endDate, department, limit));
    }

    @GetMapping("/surgery/word-cloud")
    @Operation(summary = "热门手术词云", description = "获取手术名称词云数据")
    public Result<List<SurgeryWordCloudDTO>> getSurgeryWordCloud(
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate,
            @RequestParam(required = false) String department,
            @RequestParam(required = false) Integer limit) {
        return Result.success(analyticsService.getSurgeryWordCloud(startDate, endDate, department, limit));
    }

    @GetMapping("/low-confidence/distribution")
    @Operation(summary = "低置信度分布图", description = "异常识别：按字段类型的低置信度实体分布")
    public Result<List<LowConfidenceDistributionDTO>> getLowConfidenceDistribution(
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate,
            @RequestParam(required = false) String department,
            @RequestParam(required = false) Double threshold) {
        return Result.success(analyticsService.getLowConfidenceDistribution(startDate, endDate, department, threshold));
    }
}
