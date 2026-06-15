package com.surg.extract.controller;

import com.surg.extract.common.Result;
import com.surg.extract.dto.HomePageUpdateDTO;
import com.surg.extract.entity.FieldMapping;
import com.surg.extract.service.Icd10PcsCodingService;
import com.surg.extract.service.MedicalRecordHomeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/homepage")
@RequiredArgsConstructor
@Tag(name = "病案首页管理", description = "病案首页自动填充、编辑、审核接口")
public class MedicalRecordHomeController {

    private final MedicalRecordHomeService homeService;
    private final Icd10PcsCodingService icd10PcsCodingService;

    @GetMapping("/{recordId}")
    @Operation(summary = "获取病案首页", description = "根据手术记录ID获取病案首页数据")
    public Result<Map<String, Object>> getHomePage(@PathVariable Long recordId) {
        return Result.success(homeService.getHomePage(recordId));
    }

    @GetMapping("/{recordId}/with-codes")
    @Operation(summary = "获取病案首页(含推荐编码)", description = "返回病案首页数据 + ICD-10-PCS推荐编码 + 医生已确认编码")
    public Result<Map<String, Object>> getHomePageWithCodes(@PathVariable Long recordId) {
        return Result.success(icd10PcsCodingService.getHomePageWithCodes(recordId));
    }

    @GetMapping("/field-mappings")
    @Operation(summary = "获取字段映射配置", description = "获取病案首页字段映射配置，用于前端动态渲染表单")
    public Result<List<FieldMapping>> getFieldMappings() {
        return Result.success(homeService.getFieldMappings());
    }

    @PutMapping("/{recordId}")
    @Operation(summary = "保存病案首页", description = "保存病案首页编辑内容")
    public Result<Map<String, Object>> updateHomePage(
            @PathVariable Long recordId,
            @RequestBody HomePageUpdateDTO dto) {
        return Result.success("保存成功", homeService.updateHomePage(recordId, dto));
    }

    @PostMapping("/{recordId}/draft")
    @Operation(summary = "保存草稿", description = "保存病案首页为草稿状态")
    public Result<Map<String, Object>> saveAsDraft(
            @PathVariable Long recordId,
            @RequestBody HomePageUpdateDTO dto) {
        return Result.success("草稿保存成功", homeService.saveAsDraft(recordId, dto));
    }

    @PostMapping("/{recordId}/submit")
    @Operation(summary = "提交审核", description = "提交病案首页进入审核流程")
    public Result<Void> submit(@PathVariable Long recordId) {
        homeService.submitHomePage(recordId);
        return Result.success("提交成功");
    }

    @PostMapping("/{recordId}/audit")
    @Operation(summary = "审核病案首页", description = "审核病案首页，通过后自动同步HIS")
    public Result<Void> audit(
            @PathVariable Long recordId,
            @RequestParam boolean approved,
            @RequestParam(required = false) String remark) {
        homeService.auditHomePage(recordId, approved, remark);
        return Result.success(approved ? "审核通过" : "已驳回");
    }

    @GetMapping("/stats/efficiency")
    @Operation(summary = "提效统计", description = "获取录入效率提升统计数据")
    public Result<Map<String, Object>> getEfficiencyStats() {
        return Result.success(homeService.getEfficiencyStats());
    }
}
