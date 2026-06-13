package com.surg.extract.controller;

import com.surg.extract.common.Result;
import com.surg.extract.dto.HomePageUpdateDTO;
import com.surg.extract.dto.QcCheckResult;
import com.surg.extract.dto.QcScorecardDTO;
import com.surg.extract.service.QualityControlService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/qc")
@RequiredArgsConstructor
@Tag(name = "数据质控", description = "病案首页数据质控校验、评分卡、报告导出接口")
public class QualityControlController {

    private final QualityControlService qcService;

    @PostMapping("/validate/{recordId}")
    @Operation(summary = "质控校验", description = "对指定记录的病案首页数据进行质控规则校验")
    public Result<QcCheckResult> validate(@PathVariable Long recordId) {
        return Result.success(qcService.validate(recordId));
    }

    @PostMapping("/validate-form")
    @Operation(summary = "表单数据质控校验", description = "对前端提交的表单数据进行实时质控校验（无需保存）")
    public Result<QcCheckResult> validateForm(@RequestBody HomePageUpdateDTO dto) {
        return Result.success(qcService.validateByFormData(dto));
    }

    @GetMapping("/scorecard/{recordId}")
    @Operation(summary = "质控评分卡", description = "生成病案首页质控评分卡（完整性、逻辑一致性评分）")
    public Result<QcScorecardDTO> getScorecard(@PathVariable Long recordId) {
        return Result.success(qcService.generateScorecard(recordId));
    }

    @GetMapping("/export/{recordId}")
    @Operation(summary = "导出质控报告", description = "导出指定记录的质控报告（Excel格式）")
    public ResponseEntity<byte[]> exportReport(@PathVariable Long recordId) throws Exception {
        byte[] data = qcService.exportReport(recordId);
        String fileName = URLEncoder.encode("质控报告_" + recordId + ".xlsx", StandardCharsets.UTF_8);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + fileName)
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .contentLength(data.length)
                .body(data);
    }
}
