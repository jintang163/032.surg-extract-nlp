package com.surg.extract.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.surg.extract.common.Result;
import com.surg.extract.dto.*;
import com.surg.extract.service.QualityBenchmarkService;
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
@RequestMapping("/quality-benchmark")
@RequiredArgsConstructor
@Tag(name = "跨院区质控基准", description = "区域质控基准维护、偏离度计算、科室质控排名、雷达图分析")
public class QualityBenchmarkController {

    private final QualityBenchmarkService benchmarkService;

    @GetMapping("/dashboard")
    @Operation(summary = "质控基准仪表盘", description = "获取整体达标率、综合评分、偏离度TOP、科室排名")
    public Result<QualityBenchmarkDashboardDTO> getDashboard(
            @Parameter(description = "开始日期") @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @Parameter(description = "结束日期") @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate,
            @Parameter(description = "科室") @RequestParam(required = false) String department,
            @Parameter(description = "基准年份") @RequestParam(required = false) Integer benchmarkYear) {
        return Result.success(benchmarkService.getDashboard(startDate, endDate, department, benchmarkYear));
    }

    @GetMapping("/benchmarks")
    @Operation(summary = "质控基准列表（分页）", description = "分页查询质控基准配置")
    public Result<IPage<QualityBenchmarkDTO>> listBenchmarks(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int pageNum,
            @Parameter(description = "每页大小") @RequestParam(defaultValue = "20") int pageSize,
            @Parameter(description = "指标分类") @RequestParam(required = false) String indicatorCategory,
            @Parameter(description = "科室") @RequestParam(required = false) String department,
            @Parameter(description = "是否启用") @RequestParam(required = false) Integer enabled,
            @Parameter(description = "基准年份") @RequestParam(required = false) Integer benchmarkYear,
            @Parameter(description = "区域") @RequestParam(required = false) String region) {
        return Result.success(benchmarkService.listBenchmarks(
                pageNum, pageSize, indicatorCategory, department, enabled, benchmarkYear, region));
    }

    @GetMapping("/benchmarks/all")
    @Operation(summary = "全部质控基准", description = "获取全部启用的质控基准，不分页")
    public Result<List<QualityBenchmarkDTO>> listAllBenchmarks(
            @Parameter(description = "指标分类") @RequestParam(required = false) String indicatorCategory,
            @Parameter(description = "科室") @RequestParam(required = false) String department,
            @Parameter(description = "是否启用") @RequestParam(required = false) Integer enabled,
            @Parameter(description = "基准年份") @RequestParam(required = false) Integer benchmarkYear,
            @Parameter(description = "区域") @RequestParam(required = false) String region) {
        return Result.success(benchmarkService.listAllBenchmarks(
                indicatorCategory, department, enabled, benchmarkYear, region));
    }

    @GetMapping("/benchmarks/{id}")
    @Operation(summary = "质控基准详情", description = "获取单个质控基准详情")
    public Result<QualityBenchmarkDTO> getBenchmark(@PathVariable Long id) {
        return Result.success(benchmarkService.getBenchmark(id));
    }

    @PostMapping("/benchmarks")
    @Operation(summary = "创建质控基准", description = "新增质控基准配置")
    public Result<QualityBenchmarkDTO> createBenchmark(@RequestBody QualityBenchmarkCreateDTO dto) {
        return Result.success(benchmarkService.createBenchmark(dto));
    }

    @PutMapping("/benchmarks/{id}")
    @Operation(summary = "更新质控基准", description = "更新质控基准配置")
    public Result<QualityBenchmarkDTO> updateBenchmark(@PathVariable Long id,
                                                        @RequestBody QualityBenchmarkCreateDTO dto) {
        return Result.success(benchmarkService.updateBenchmark(id, dto));
    }

    @DeleteMapping("/benchmarks/{id}")
    @Operation(summary = "删除质控基准", description = "逻辑删除质控基准配置")
    public Result<Void> deleteBenchmark(@PathVariable Long id) {
        benchmarkService.deleteBenchmark(id);
        return Result.success();
    }

    @GetMapping("/deviations")
    @Operation(summary = "指标偏离度分析", description = "计算本院指标与区域基准的偏离度")
    public Result<List<IndicatorDeviationDTO>> calculateDeviations(
            @Parameter(description = "科室") @RequestParam(required = false) String department,
            @Parameter(description = "开始日期") @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @Parameter(description = "结束日期") @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate,
            @Parameter(description = "基准年份") @RequestParam(required = false) Integer benchmarkYear) {
        return Result.success(benchmarkService.calculateDeviations(department, startDate, endDate, benchmarkYear));
    }

    @GetMapping("/department-rankings")
    @Operation(summary = "科室质控排名", description = "各科室按质控综合评分排名")
    public Result<List<DepartmentRankingDTO>> calculateDepartmentRankings(
            @Parameter(description = "开始日期") @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @Parameter(description = "结束日期") @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate,
            @Parameter(description = "基准年份") @RequestParam(required = false) Integer benchmarkYear,
            @Parameter(description = "指标分类") @RequestParam(required = false) String indicatorCategory) {
        return Result.success(benchmarkService.calculateDepartmentRankings(
                startDate, endDate, benchmarkYear, indicatorCategory));
    }

    @GetMapping("/radar-chart")
    @Operation(summary = "质控雷达图数据", description = "生成科室质控对比雷达图数据")
    public Result<QualityRadarDTO> getRadarChartData(
            @Parameter(description = "科室列表(逗号分隔)") @RequestParam(required = false) List<String> departments,
            @Parameter(description = "开始日期") @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @Parameter(description = "结束日期") @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate,
            @Parameter(description = "基准年份") @RequestParam(required = false) Integer benchmarkYear,
            @Parameter(description = "指标分类") @RequestParam(required = false) String indicatorCategory) {
        return Result.success(benchmarkService.getRadarChartData(
                departments, startDate, endDate, benchmarkYear, indicatorCategory));
    }

    @PostMapping("/init-defaults")
    @Operation(summary = "初始化默认基准", description = "初始化区域质控中心默认基准数据")
    public Result<Void> initDefaultBenchmarks() {
        benchmarkService.initDefaultBenchmarks();
        return Result.success();
    }

    @GetMapping("/indicator-categories")
    @Operation(summary = "指标分类字典", description = "获取指标分类枚举")
    public Result<List<Map<String, String>>> getIndicatorCategories() {
        return Result.success(benchmarkService.getIndicatorCategories());
    }

    @GetMapping("/directions")
    @Operation(summary = "优劣方向字典", description = "获取指标优劣方向枚举")
    public Result<List<Map<String, String>>> getDirections() {
        return Result.success(benchmarkService.getDirections());
    }
}
