package com.surg.extract.controller;

import com.surg.extract.common.Result;
import com.surg.extract.dto.AdoptTypicalValueRequestDTO;
import com.surg.extract.dto.CaseStatsAnalysisDTO;
import com.surg.extract.dto.SimilarCaseResultDTO;
import com.surg.extract.dto.SimilarCaseSearchRequestDTO;
import com.surg.extract.service.CaseCompareService;
import com.surg.extract.service.SurgeryRecordEsIndexService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/case-compare")
@RequiredArgsConstructor
@Tag(name = "历史病例对比", description = "相似病例检索、历史统计分析、ES索引管理")
public class CaseCompareController {

    private final CaseCompareService caseCompareService;
    private final SurgeryRecordEsIndexService esIndexService;

    @PostMapping("/similar/search")
    @Operation(summary = "相似病例检索", description = "根据手术名称、诊断、科室等条件，检索过去N个月内的相似病例")
    public Result<List<SimilarCaseResultDTO>> searchSimilarCases(
            @RequestBody SimilarCaseSearchRequestDTO request) {
        List<SimilarCaseResultDTO> results = caseCompareService.searchSimilarCases(request);
        return Result.success(results);
    }

    @PostMapping("/stats/analysis")
    @Operation(summary = "历史数据统计分析", description = "获取匹配病例的统计指标（均值/中位数/分布）+ 与当前值的差异对比")
    public Result<CaseStatsAnalysisDTO> getStatsAnalysis(
            @RequestBody SimilarCaseSearchRequestDTO request) {
        CaseStatsAnalysisDTO analysis = caseCompareService.getStatsAnalysis(request);
        return Result.success(analysis);
    }

    @PostMapping("/{recordId}/full-analysis")
    @Operation(summary = "完整对比分析（同时返回相似病例 + 统计对比）",
            description = "一站式接口，返回相似病例列表、统计分析结果、差异对比")
    public Result<Map<String, Object>> getFullAnalysis(
            @PathVariable Long recordId,
            @RequestBody SimilarCaseSearchRequestDTO request) {
        request.setExcludeRecordId(recordId);
        List<SimilarCaseResultDTO> similarCases = caseCompareService.searchSimilarCases(request);
        CaseStatsAnalysisDTO stats = caseCompareService.getStatsAnalysis(request);

        Map<String, Object> result = new HashMap<>();
        result.put("similarCases", similarCases);
        result.put("stats", stats);
        return Result.success(result);
    }

    @PostMapping("/{recordId}/adopt")
    @Operation(summary = "采纳历史典型值并回写到抽取结果",
            description = "将医生选择的典型值更新为该病例的抽取实体值，并自动触发ES索引刷新")
    public Result<Map<String, Object>> adoptTypicalValue(
            @PathVariable Long recordId,
            @RequestBody AdoptTypicalValueRequestDTO request) {
        Map<String, Object> res = caseCompareService.adoptTypicalValue(recordId, request);
        return Result.success(res);
    }

    @PostMapping("/index/rebuild")
    @Operation(summary = "重建ES病例索引", description = "删除旧索引，重新同步所有已处理的病例到Elasticsearch")
    public Result<Map<String, Object>> rebuildIndex() {
        esIndexService.rebuildIndex();
        long count = esIndexService.getIndexCount();
        Map<String, Object> result = new HashMap<>();
        result.put("indexedCount", count);
        result.put("message", "索引重建完成");
        return Result.success(result);
    }

    @PostMapping("/index/sync/{recordId}")
    @Operation(summary = "同步单条病例到ES", description = "将指定病例数据同步/更新到Elasticsearch")
    public Result<Void> syncRecordToIndex(@PathVariable Long recordId) {
        esIndexService.indexRecord(recordId);
        return Result.success("同步成功");
    }

    @PostMapping("/index/sync-all")
    @Operation(summary = "全量同步病例到ES", description = "批量同步所有已处理病例（不删除现有索引）")
    public Result<Map<String, Object>> syncAllToIndex() {
        long count = esIndexService.bulkIndexAll();
        Map<String, Object> result = new HashMap<>();
        result.put("indexedCount", count);
        return Result.success(result);
    }

    @GetMapping("/index/status")
    @Operation(summary = "获取索引状态", description = "检查ES索引是否存在以及索引数据量")
    public Result<Map<String, Object>> getIndexStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("exists", esIndexService.indexExists());
        status.put("count", esIndexService.getIndexCount());
        return Result.success(status);
    }
}
