package com.surg.extract.controller;

import com.surg.extract.common.Result;
import com.surg.extract.dto.Icd10PcsConfirmRequestDTO;
import com.surg.extract.dto.Icd10PcsRecommendResultDTO;
import com.surg.extract.entity.Icd10PcsConfirmation;
import com.surg.extract.service.Icd10PcsCodingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/icd10-pcs")
@RequiredArgsConstructor
@Tag(name = "ICD-10-PCS手术编码", description = "ICD-10-PCS手术编码推荐、确认、历史查询")
public class Icd10PcsCodingController {

    private final Icd10PcsCodingService codingService;

    @GetMapping("/recommend/{recordId}")
    @Operation(summary = "推荐ICD-10-PCS编码", description = "根据手术记录实体自动推荐ICD-10-PCS编码")
    public Result<Icd10PcsRecommendResultDTO> recommend(@PathVariable Long recordId,
                                                        @RequestParam(required = false, defaultValue = "5") Integer topK) {
        return Result.success(codingService.recommendCodes(recordId, null, topK));
    }

    @PostMapping("/recommend-by-text")
    @Operation(summary = "按文本推荐ICD-10-PCS编码", description = "直接输入手术文本推荐ICD-10-PCS编码")
    public Result<Icd10PcsRecommendResultDTO> recommendByText(
            @RequestParam(required = false) Long recordId,
            @RequestParam String text,
            @RequestParam(required = false, defaultValue = "5") Integer topK) {
        return Result.success(codingService.recommendCodes(recordId, text, topK));
    }

    @PostMapping("/confirm/{recordId}")
    @Operation(summary = "确认ICD-10-PCS编码", description = "医生确认编码后自动写入病案首页surgeryCode字段")
    public Result<Map<String, Object>> confirm(@PathVariable Long recordId,
                                               @RequestBody Icd10PcsConfirmRequestDTO dto) {
        dto.setRecordId(recordId);
        return Result.success("确认成功", codingService.confirmCode(recordId, dto, null, null));
    }

    @GetMapping("/homepage/{recordId}")
    @Operation(summary = "获取病案首页含推荐编码", description = "返回病案首页数据 + ICD-10-PCS推荐编码 + 已确认编码")
    public Result<Map<String, Object>> getHomePageWithCodes(@PathVariable Long recordId) {
        return Result.success(codingService.getHomePageWithCodes(recordId));
    }

    @GetMapping("/history/{recordId}")
    @Operation(summary = "查询编码确认历史", description = "按手术记录ID查询编码确认历史")
    public Result<List<Icd10PcsConfirmation>> getHistory(@PathVariable Long recordId) {
        return Result.success(codingService.getConfirmationHistory(recordId));
    }

    @GetMapping("/history")
    @Operation(summary = "查询全部编码确认历史", description = "查询全部编码确认历史（最近500条）")
    public Result<List<Icd10PcsConfirmation>> getAllHistory() {
        return Result.success(codingService.getConfirmationHistory(null));
    }
}
