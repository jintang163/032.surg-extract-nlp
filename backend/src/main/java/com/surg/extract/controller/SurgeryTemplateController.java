package com.surg.extract.controller;

import com.surg.extract.common.Result;
import com.surg.extract.dto.*;
import com.surg.extract.service.SurgeryTemplateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/templates")
@RequiredArgsConstructor
@Tag(name = "手术模板管理", description = "手术模板CRUD、版本控制、占位符填充、导入导出接口")
public class SurgeryTemplateController {

    private final SurgeryTemplateService templateService;

    @GetMapping("/list")
    @Operation(summary = "模板列表", description = "分页查询手术模板列表")
    public Result<PageResult<SurgeryTemplateDTO>> list(
            @Parameter(description = "模板名称（模糊搜索）") @RequestParam(required = false) String templateName,
            @Parameter(description = "手术类型（模糊搜索）") @RequestParam(required = false) String surgeryType,
            @Parameter(description = "科室") @RequestParam(required = false) String department,
            @Parameter(description = "状态: DRAFT/ACTIVE/INACTIVE") @RequestParam(required = false) String status,
            @Parameter(description = "页码，从1开始") @RequestParam(defaultValue = "1") Integer pageNum,
            @Parameter(description = "每页数量") @RequestParam(defaultValue = "20") Integer pageSize) {

        PageResult<SurgeryTemplateDTO> result = templateService.queryTemplates(
                templateName, surgeryType, department, status, pageNum, pageSize);
        return Result.success(result);
    }

    @GetMapping("/available")
    @Operation(summary = "获取可用模板", description = "获取启用状态的模板列表，用于医生选择")
    public Result<List<SurgeryTemplateDTO>> getAvailableTemplates(
            @Parameter(description = "手术类型（模糊匹配）") @RequestParam(required = false) String surgeryType,
            @Parameter(description = "科室") @RequestParam(required = false) String department) {
        return Result.success(templateService.getAvailableTemplates(surgeryType, department));
    }

    @GetMapping("/{id}")
    @Operation(summary = "模板详情", description = "获取单条手术模板详情")
    public Result<SurgeryTemplateDTO> getDetail(@PathVariable Long id) {
        return Result.success(templateService.getTemplate(id));
    }

    @PostMapping
    @Operation(summary = "创建模板", description = "创建新的手术模板")
    public Result<SurgeryTemplateDTO> create(@RequestBody SurgeryTemplateCreateDTO dto) {
        return Result.success("创建成功", templateService.createTemplate(dto));
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新模板", description = "更新手术模板，内容变更时自动创建新版本")
    public Result<SurgeryTemplateDTO> update(@PathVariable Long id, @RequestBody SurgeryTemplateUpdateDTO dto) {
        return Result.success("更新成功", templateService.updateTemplate(id, dto));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除模板", description = "逻辑删除手术模板")
    public Result<Void> delete(@PathVariable Long id) {
        templateService.deleteTemplate(id);
        return Result.success("删除成功");
    }

    @GetMapping("/{id}/versions")
    @Operation(summary = "获取版本历史", description = "获取模板的所有历史版本")
    public Result<List<SurgeryTemplateVersionDTO>> getVersions(@PathVariable Long id) {
        return Result.success(templateService.getTemplateVersions(id));
    }

    @GetMapping("/{id}/versions/{versionNo}")
    @Operation(summary = "获取指定版本", description = "获取模板的指定版本详情")
    public Result<SurgeryTemplateVersionDTO> getVersion(
            @PathVariable Long id,
            @PathVariable Integer versionNo) {
        return Result.success(templateService.getTemplateVersion(id, versionNo));
    }

    @PostMapping("/{id}/versions/revert/{versionNo}")
    @Operation(summary = "回退版本", description = "将模板回退到指定历史版本，自动创建新版本")
    public Result<SurgeryTemplateDTO> revertVersion(
            @PathVariable Long id,
            @PathVariable Integer versionNo,
            @RequestParam(required = false) String changeLog) {
        return Result.success("回退成功", templateService.revertToVersion(id, versionNo, changeLog));
    }

    @PostMapping("/{id}/fill")
    @Operation(summary = "填充模板（自定义值）", description = "使用传入的占位符值填充模板")
    public Result<String> fillTemplate(
            @PathVariable Long id,
            @RequestBody Map<String, String> values) {
        return Result.success(templateService.fillTemplate(id, values));
    }

    @PostMapping("/{id}/fill/record/{recordId}")
    @Operation(summary = "填充模板（从记录）", description = "从手术记录抽取的实体数据自动填充模板")
    public Result<String> fillTemplateFromRecord(
            @PathVariable Long id,
            @PathVariable Long recordId) {
        return Result.success(templateService.fillTemplateFromRecord(id, recordId));
    }

    @PostMapping("/extract-placeholders")
    @Operation(summary = "提取占位符", description = "从模板内容中提取所有 ${xxx} 格式的占位符")
    public Result<List<String>> extractPlaceholders(@RequestBody Map<String, String> body) {
        String content = body.get("content");
        return Result.success(templateService.extractPlaceholders(content));
    }

    @GetMapping("/{id}/export")
    @Operation(summary = "导出模板", description = "导出模板为JSON格式，包含模板内容和占位符定义")
    public Result<Map<String, Object>> exportTemplate(@PathVariable Long id) {
        return Result.success(templateService.exportTemplate(id));
    }

    @PostMapping("/import")
    @Operation(summary = "导入模板", description = "导入JSON格式的模板，编码已存在则更新，否则创建")
    public Result<SurgeryTemplateDTO> importTemplate(@RequestBody TemplateImportDTO dto) {
        return Result.success("导入成功", templateService.importTemplate(dto));
    }
}
