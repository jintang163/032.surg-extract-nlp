package com.surg.extract.controller;

import com.surg.extract.common.Result;
import com.surg.extract.common.UserContext;
import com.surg.extract.dto.*;
import com.surg.extract.entity.CustomFieldSample;
import com.surg.extract.entity.MedicalRecordHomeExt;
import com.surg.extract.service.DepartmentCustomFieldService;
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
@RequestMapping("/custom-fields")
@RequiredArgsConstructor
@Tag(name = "科室自定义字段管理", description = "科室自定义抽取字段的CRUD、样本管理、模型训练接口")
public class DepartmentCustomFieldController {

    private final DepartmentCustomFieldService customFieldService;

    @GetMapping("/department/{department}")
    @Operation(summary = "获取科室自定义字段列表", description = "获取指定科室启用的自定义字段列表")
    public Result<List<CustomFieldDTO>> getFieldsByDepartment(
            @Parameter(description = "科室名称") @PathVariable String department) {
        return Result.success(customFieldService.getFieldsByDepartment(department));
    }

    @GetMapping("/department/{department}/all")
    @Operation(summary = "获取科室所有自定义字段", description = "获取指定科室所有自定义字段（包括禁用的），用于管理后台")
    public Result<List<CustomFieldDTO>> getAllFieldsByDepartment(
            @Parameter(description = "科室名称") @PathVariable String department) {
        return Result.success(customFieldService.getAllFieldsByDepartment(department));
    }

    @GetMapping("/{id}")
    @Operation(summary = "获取字段详情", description = "获取单个自定义字段的详细信息")
    public Result<CustomFieldDTO> getFieldDetail(@PathVariable Long id) {
        return Result.success(customFieldService.getField(id));
    }

    @PostMapping
    @Operation(summary = "创建自定义字段", description = "创建新的科室自定义字段（仅管理员）")
    public Result<CustomFieldDTO> createField(@RequestBody CustomFieldCreateDTO dto) {
        UserContext.checkAdmin();
        return Result.success("创建成功", customFieldService.createField(dto));
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新自定义字段", description = "更新自定义字段配置（仅管理员）")
    public Result<CustomFieldDTO> updateField(
            @PathVariable Long id,
            @RequestBody CustomFieldUpdateDTO dto) {
        UserContext.checkAdmin();
        return Result.success("更新成功", customFieldService.updateField(id, dto));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除自定义字段", description = "删除自定义字段（仅管理员）")
    public Result<Void> deleteField(@PathVariable Long id) {
        UserContext.checkAdmin();
        customFieldService.deleteField(id);
        return Result.success("删除成功");
    }

    @GetMapping("/{fieldId}/samples")
    @Operation(summary = "获取训练样本列表", description = "获取指定字段的所有训练样本")
    public Result<List<CustomFieldSample>> getSamples(@PathVariable Long fieldId) {
        return Result.success(customFieldService.getSamplesByFieldId(fieldId));
    }

    @PostMapping("/samples")
    @Operation(summary = "添加训练样本", description = "添加新的训练样本（管理员或医生）")
    public Result<CustomFieldSample> addSample(@RequestBody CustomFieldSampleCreateDTO dto) {
        return Result.success("添加成功", customFieldService.addSample(dto));
    }

    @DeleteMapping("/samples/{sampleId}")
    @Operation(summary = "删除训练样本", description = "删除训练样本（仅管理员）")
    public Result<Void> deleteSample(@PathVariable Long sampleId) {
        UserContext.checkAdmin();
        customFieldService.deleteSample(sampleId);
        return Result.success("删除成功");
    }

    @PostMapping("/{fieldId}/train")
    @Operation(summary = "训练NER模型", description = "使用样本训练自定义字段的NER模型（仅管理员）")
    public Result<String> trainModel(@PathVariable Long fieldId) {
        UserContext.checkAdmin();
        String message = customFieldService.trainModel(fieldId);
        return Result.success(message);
    }

    @GetMapping("/{fieldId}/model-status")
    @Operation(summary = "获取模型训练状态", description = "获取自定义字段NER模型的训练状态")
    public Result<Map<String, Object>> getModelStatus(@PathVariable Long fieldId) {
        return Result.success(customFieldService.getModelStatus(fieldId));
    }

    @GetMapping("/home-ext/{recordId}")
    @Operation(summary = "获取病案首页扩展字段", description = "获取指定手术记录的自定义字段抽取结果")
    public Result<List<MedicalRecordHomeExt>> getHomeExtFields(@PathVariable Long recordId) {
        return Result.success(customFieldService.getHomeExtFields(recordId));
    }

    @PostMapping("/home-ext/verify/{extId}")
    @Operation(summary = "确认扩展字段", description = "人工确认自定义字段的抽取结果")
    public Result<Void> verifyHomeExtField(@PathVariable Long extId) {
        Long userId = UserContext.getUserId();
        String userName = UserContext.getUserName();
        customFieldService.verifyHomeExtField(extId, userId, userName);
        return Result.success("确认成功");
    }
}
