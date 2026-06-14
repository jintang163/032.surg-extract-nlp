package com.surg.extract.controller;

import com.surg.extract.common.Result;
import com.surg.extract.dto.*;
import com.surg.extract.service.DataExportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/export")
@RequiredArgsConstructor
@Tag(name = "数据导出管理", description = "结构化数据导出、模板配置、多格式导出接口")
public class DataExportController {

    private final DataExportService exportService;

    @GetMapping("/templates")
    @Operation(summary = "查询导出模板列表", description = "分页查询导出模板，支持按格式、科室、启用状态筛选")
    public Result<Map<String, Object>> listTemplates(
            @Parameter(description = "导出格式: EXCEL/JSON/FHIR") @RequestParam(required = false) String format,
            @RequestParam(required = false) String department,
            @RequestParam(required = false) Integer enabled,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {
        List<ExportTemplateDTO> list = exportService.listTemplates(format, department, enabled, pageNum, pageSize);
        long total = exportService.countTemplates(format, department, enabled);
        Map<String, Object> result = new HashMap<>();
        result.put("list", list);
        result.put("total", total);
        result.put("pageNum", pageNum);
        result.put("pageSize", pageSize);
        return Result.success(result);
    }

    @GetMapping("/templates/{id}")
    @Operation(summary = "获取导出模板详情", description = "根据ID获取导出模板详情")
    public Result<ExportTemplateDTO> getTemplate(@PathVariable Long id) {
        return Result.success(exportService.getTemplate(id));
    }

    @GetMapping("/templates/available-fields")
    @Operation(summary = "获取可配置字段列表", description = "获取病案首页所有可导出的标准化字段配置")
    public Result<List<ExportFieldConfigDTO>> getAvailableFields() {
        return Result.success(exportService.getAvailableFields());
    }

    @PostMapping("/templates")
    @Operation(summary = "创建导出模板", description = "创建新的导出模板，配置字段选择、排序、单位转换等")
    public Result<ExportTemplateDTO> createTemplate(@RequestBody ExportTemplateCreateDTO dto) {
        return Result.success("创建成功", exportService.createTemplate(dto, 1L, "当前用户"));
    }

    @PutMapping("/templates/{id}")
    @Operation(summary = "更新导出模板", description = "更新指定导出模板的配置")
    public Result<ExportTemplateDTO> updateTemplate(
            @PathVariable Long id,
            @RequestBody ExportTemplateCreateDTO dto) {
        return Result.success("更新成功", exportService.updateTemplate(id, dto));
    }

    @DeleteMapping("/templates/{id}")
    @Operation(summary = "删除导出模板", description = "删除指定导出模板（逻辑删除）")
    public Result<Void> deleteTemplate(@PathVariable Long id) {
        exportService.deleteTemplate(id);
        return Result.success("删除成功");
    }

    @PostMapping("/excel")
    @Operation(summary = "导出Excel", description = "按指定模板导出病案首页数据为Excel格式")
    public ResponseEntity<byte[]> exportExcel(
            @RequestBody Map<String, Object> request) {
        @SuppressWarnings("unchecked")
        List<Long> recordIds = (List<Long>) request.get("recordIds");
        Long templateId = request.get("templateId") != null ? ((Number) request.get("templateId")).longValue() : null;

        byte[] data = exportService.exportToExcel(recordIds, templateId);
        String filename = "病案首页数据_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".xlsx";
        String encodedFilename = URLEncoder.encode(filename, StandardCharsets.UTF_8).replaceAll("\\+", "%20");

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encodedFilename)
                .header(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, HttpHeaders.CONTENT_DISPOSITION)
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(data);
    }

    @PostMapping("/json")
    @Operation(summary = "导出JSON", description = "按指定模板导出病案首页数据为JSON格式")
    public Result<List<StandardHomePageDTO>> exportJson(@RequestBody Map<String, Object> request) {
        @SuppressWarnings("unchecked")
        List<Long> recordIds = (List<Long>) request.get("recordIds");
        Long templateId = request.get("templateId") != null ? ((Number) request.get("templateId")).longValue() : null;
        return Result.success(exportService.exportToJson(recordIds, templateId));
    }

    @PostMapping("/fhir")
    @Operation(summary = "导出HL7 FHIR R4", description = "按指定模板导出病案首页数据为HL7 FHIR R4格式，包含Patient/Encounter/Procedure资源")
    public Result<FhirBundleDTO> exportFhir(@RequestBody Map<String, Object> request) {
        @SuppressWarnings("unchecked")
        List<Long> recordIds = (List<Long>) request.get("recordIds");
        Long templateId = request.get("templateId") != null ? ((Number) request.get("templateId")).longValue() : null;
        return Result.success(exportService.exportToFhir(recordIds, templateId));
    }

    @GetMapping("/standard/homepage/{recordId}")
    @Operation(summary = "第三方API-获取标准化病案首页", description = "第三方系统调用，获取标准化的病案首页JSON数据，无需登录鉴权")
    public Result<StandardHomePageDTO> getStandardHomePage(@PathVariable Long recordId) {
        return Result.success(exportService.getStandardHomePage(recordId));
    }

    @PostMapping("/standard/homepage/batch")
    @Operation(summary = "第三方API-批量获取标准化病案首页", description = "第三方系统调用，批量获取标准化病案首页数据")
    public Result<List<StandardHomePageDTO>> batchGetStandardHomePage(@RequestBody Map<String, Object> request) {
        @SuppressWarnings("unchecked")
        List<Long> recordIds = (List<Long>) request.get("recordIds");
        return Result.success(exportService.exportToJson(recordIds, null));
    }

    @GetMapping("/standard/fhir/{recordId}")
    @Operation(summary = "第三方API-获取FHIR格式病案首页", description = "第三方系统调用，获取HL7 FHIR R4格式的病案首页数据")
    public Result<FhirBundleDTO> getFhirHomePage(@PathVariable Long recordId) {
        return Result.success(exportService.exportToFhir(List.of(recordId), null));
    }
}
