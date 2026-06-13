package com.surg.extract.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.surg.extract.common.Result;
import com.surg.extract.dto.*;
import com.surg.extract.entity.MedicalTerm;
import com.surg.extract.entity.MedicalTermAlias;
import com.surg.extract.entity.MedicalTermIcd;
import com.surg.extract.service.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/medical-term")
@RequiredArgsConstructor
@Tag(name = "医学术语管理", description = "医学术语、同义词、ICD编码、术语映射接口")
public class MedicalTermController {

    private final MedicalTermService termService;
    private final MedicalTermAliasService aliasService;
    private final MedicalTermIcdService icdService;
    private final TermMappingService mappingService;
    private final MedicalTermGraphService graphService;

    @Operation(summary = "获取术语详情", description = "根据ID获取术语详情")
    @GetMapping("/{id}")
    public Result<MedicalTerm> getTermById(@PathVariable Long id) {
        return Result.success(termService.getTermById(id));
    }

    @Operation(summary = "获取术语详情（含同义词）", description = "根据ID获取术语详情及同义词列表")
    @GetMapping("/{id}/detail")
    public Result<Map<String, Object>> getTermDetail(@PathVariable Long id) {
        return Result.success(termService.getTermDetail(id));
    }

    @Operation(summary = "分页查询术语", description = "分页查询标准术语列表")
    @GetMapping("/page")
    public Result<Page<MedicalTerm>> getTermPage(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") Integer pageNum,
            @Parameter(description = "每页大小") @RequestParam(defaultValue = "10") Integer pageSize,
            @Parameter(description = "关键词") @RequestParam(required = false) String keyword,
            @Parameter(description = "术语类型") @RequestParam(required = false) String termType,
            @Parameter(description = "分类ID") @RequestParam(required = false) Long categoryId,
            @Parameter(description = "审核状态") @RequestParam(required = false) String reviewStatus) {
        return Result.success(termService.getTermPage(pageNum, pageSize, keyword, termType, categoryId, reviewStatus));
    }

    @Operation(summary = "搜索术语", description = "根据关键词搜索术语")
    @GetMapping("/search")
    public Result<List<MedicalTerm>> searchTerms(
            @Parameter(description = "关键词") @RequestParam String keyword,
            @Parameter(description = "术语类型") @RequestParam(required = false) String termType) {
        return Result.success(termService.searchTerms(keyword, termType));
    }

    @Operation(summary = "创建术语", description = "创建新的标准术语")
    @PostMapping
    public Result<MedicalTerm> createTerm(@RequestBody @Validated MedicalTermCreateDTO dto) {
        return Result.success("创建成功", termService.createTerm(dto));
    }

    @Operation(summary = "更新术语", description = "更新标准术语信息")
    @PutMapping
    public Result<MedicalTerm> updateTerm(@RequestBody @Validated MedicalTermUpdateDTO dto) {
        return Result.success("更新成功", termService.updateTerm(dto));
    }

    @Operation(summary = "删除术语", description = "逻辑删除术语")
    @DeleteMapping("/{id}")
    public Result<Void> deleteTerm(@PathVariable Long id) {
        termService.deleteTerm(id);
        return Result.success("删除成功");
    }

    @Operation(summary = "审核术语", description = "审核术语，通过或拒绝")
    @PostMapping("/{id}/review")
    public Result<Void> reviewTerm(
            @PathVariable Long id,
            @Parameter(description = "是否通过") @RequestParam Boolean approved,
            @Parameter(description = "审核备注") @RequestParam(required = false) String remark) {
        termService.reviewTerm(id, approved, remark);
        return Result.success(approved ? "审核通过" : "已拒绝");
    }

    @Operation(summary = "合并术语", description = "将多个术语合并到目标术语")
    @PostMapping("/merge")
    public Result<Void> mergeTerms(@RequestBody @Validated TermMergeRequestDTO dto) {
        termService.mergeTerms(dto);
        return Result.success("合并成功");
    }

    @Operation(summary = "批量导入术语", description = "批量导入术语和同义词")
    @PostMapping("/batch-import")
    public Result<Map<String, Object>> batchImport(@RequestBody @Validated TermBatchImportDTO dto) {
        return Result.success("导入成功", termService.batchImport(dto));
    }

    @Operation(summary = "获取术语同义词列表", description = "获取指定术语的所有同义词")
    @GetMapping("/{termId}/aliases")
    public Result<List<MedicalTermAlias>> getAliasesByTermId(@PathVariable Long termId) {
        return Result.success(aliasService.getAliasesByTermId(termId));
    }

    @Operation(summary = "添加同义词", description = "为指定术语添加同义词")
    @PostMapping("/alias")
    public Result<MedicalTermAlias> addAlias(@RequestBody @Validated MedicalTermAliasCreateDTO dto) {
        return Result.success("添加成功", aliasService.addAlias(dto));
    }

    @Operation(summary = "更新同义词", description = "更新同义词信息")
    @PutMapping("/alias")
    public Result<MedicalTermAlias> updateAlias(@RequestBody @Validated MedicalTermAliasUpdateDTO dto) {
        return Result.success("更新成功", aliasService.updateAlias(dto));
    }

    @Operation(summary = "删除同义词", description = "删除同义词")
    @DeleteMapping("/alias/{id}")
    public Result<Void> deleteAlias(@PathVariable Long id) {
        aliasService.deleteAlias(id);
        return Result.success("删除成功");
    }

    @Operation(summary = "审核同义词", description = "审核同义词，通过或拒绝")
    @PostMapping("/alias/{id}/review")
    public Result<Void> reviewAlias(
            @PathVariable Long id,
            @Parameter(description = "是否通过") @RequestParam Boolean approved,
            @Parameter(description = "审核备注") @RequestParam(required = false) String remark) {
        aliasService.reviewAlias(id, approved, remark);
        return Result.success(approved ? "审核通过" : "已拒绝");
    }

    @Operation(summary = "术语映射", description = "将非标准术语映射到标准术语")
    @PostMapping("/map")
    public Result<TermMappingResultDTO> mapTerm(@RequestBody @Validated TermMappingRequestDTO dto) {
        return Result.success(mappingService.mapTerm(dto));
    }

    @Operation(summary = "批量术语映射", description = "批量映射多个术语")
    @PostMapping("/map/batch")
    public Result<List<TermMappingResultDTO>> batchMapTerms(@RequestBody List<TermMappingRequestDTO> dtos) {
        return Result.success(mappingService.batchMapTerms(dtos));
    }

    @Operation(summary = "获取映射日志", description = "分页获取术语映射日志")
    @GetMapping("/mapping-log")
    public Result<Page<?>> getMappingLog(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") Integer pageNum,
            @Parameter(description = "每页大小") @RequestParam(defaultValue = "20") Integer pageSize,
            @Parameter(description = "手术记录ID") @RequestParam(required = false) Long recordId) {
        return Result.success(mappingService.getMappingLog(pageNum, pageSize, recordId));
    }

    @Operation(summary = "获取映射统计", description = "获取术语映射统计数据")
    @GetMapping("/mapping-stats")
    public Result<Map<String, Object>> getMappingStats(
            @Parameter(description = "统计天数") @RequestParam(defaultValue = "30") Integer days) {
        return Result.success(mappingService.getMappingStats(days));
    }

    @Operation(summary = "分页查询ICD编码", description = "分页查询ICD编码列表")
    @GetMapping("/icd/page")
    public Result<Page<MedicalTermIcd>> getIcdPage(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") Integer pageNum,
            @Parameter(description = "每页大小") @RequestParam(defaultValue = "20") Integer pageSize,
            @Parameter(description = "关键词") @RequestParam(required = false) String keyword,
            @Parameter(description = "ICD版本") @RequestParam(defaultValue = "ICD-10") String icdVersion) {
        return Result.success(icdService.getIcdPage(pageNum, pageSize, keyword, icdVersion));
    }

    @Operation(summary = "搜索ICD编码", description = "根据关键词搜索ICD编码")
    @GetMapping("/icd/search")
    public Result<List<MedicalTermIcd>> searchIcd(
            @Parameter(description = "关键词") @RequestParam String keyword,
            @Parameter(description = "ICD版本") @RequestParam(defaultValue = "ICD-10") String icdVersion) {
        return Result.success(icdService.searchIcd(keyword, icdVersion));
    }

    @Operation(summary = "获取ICD详情", description = "根据编码获取ICD详情")
    @GetMapping("/icd/{code}")
    public Result<MedicalTermIcd> getIcdByCode(
            @PathVariable String code,
            @Parameter(description = "ICD版本") @RequestParam(defaultValue = "ICD-10") String icdVersion) {
        return Result.success(icdService.getIcdByCode(code, icdVersion));
    }

    @Operation(summary = "图谱搜索", description = "在Neo4j图谱中搜索术语")
    @GetMapping("/graph/search")
    public Result<List<Map<String, Object>>> searchInGraph(
            @Parameter(description = "关键词") @RequestParam String keyword) {
        return Result.success(graphService.searchInGraph(keyword));
    }

    @Operation(summary = "图谱同义词查询", description = "在图谱中查询术语的同义词（支持多跳）")
    @GetMapping("/graph/synonyms")
    public Result<List<Map<String, Object>>> findSynonymsInGraph(
            @Parameter(description = "术语名称") @RequestParam String name,
            @Parameter(description = "最大跳数") @RequestParam(defaultValue = "3") Integer maxHops) {
        return Result.success(graphService.findSynonymsInGraph(name, maxHops));
    }

    @Operation(summary = "图谱路径查询", description = "查询两个术语之间的关联路径")
    @GetMapping("/graph/path")
    public Result<List<Map<String, Object>>> findPathInGraph(
            @Parameter(description = "起始术语") @RequestParam String startName,
            @Parameter(description = "目标术语") @RequestParam String endName) {
        return Result.success(graphService.findPathInGraph(startName, endName));
    }

    @Operation(summary = "获取图谱统计", description = "获取Neo4j图谱统计信息")
    @GetMapping("/graph/stats")
    public Result<TermGraphStatsDTO> getGraphStats() {
        return Result.success(graphService.getGraphStats());
    }

    @Operation(summary = "同步到图谱", description = "将MySQL中的术语全量同步到Neo4j图谱")
    @PostMapping("/graph/sync")
    public Result<Void> syncAllToGraph() {
        graphService.syncAllToGraph();
        return Result.success("同步成功");
    }

    @Operation(summary = "清空图谱", description = "清空Neo4j图谱中的所有数据")
    @DeleteMapping("/graph/clear")
    public Result<Void> clearGraph() {
        graphService.clearGraph();
        return Result.success("清空成功");
    }

    @Operation(summary = "获取所有术语类型", description = "获取术语类型枚举列表")
    @GetMapping("/types")
    public Result<List<Map<String, String>>> getTermTypes() {
        return Result.success(termService.getTermTypes());
    }

    @Operation(summary = "获取所有别名类型", description = "获取别名类型枚举列表")
    @GetMapping("/alias-types")
    public Result<List<Map<String, String>>> getAliasTypes() {
        return Result.success(aliasService.getAliasTypes());
    }
}
