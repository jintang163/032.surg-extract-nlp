package com.surg.extract.controller;

import com.surg.extract.common.Result;
import com.surg.extract.dto.NextStepRecommendDTO;
import com.surg.extract.service.NextStepRecommendService;
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
@RequestMapping("/next-step")
@RequiredArgsConstructor
@Tag(name = "智能推荐下一步操作", description = "基于医生历史行为序列的协同过滤 + 内容推荐，推荐后续医疗文书并一键生成草稿")
public class NextStepRecommendController {

    private final NextStepRecommendService recommendService;

    @GetMapping("/{recordId}/recommend")
    @Operation(summary = "获取下一步操作推荐列表",
            description = "协同过滤(医生行为 45%) + 内容匹配(手术/诊断 35%) + 流行度(历史使用 20%)，按综合得分排序")
    public Result<List<NextStepRecommendDTO>> getRecommendations(
            @PathVariable @Parameter(description = "手术记录ID") Long recordId,
            @RequestParam(required = false) @Parameter(description = "用户ID，默认取记录的上传用户") Long userId) {
        return Result.success(recommendService.getRecommendations(recordId, userId));
    }

    @PostMapping("/{recordId}/generate/{templateId}")
    @Operation(summary = "一键生成草稿",
            description = "基于选定模板，从当前病例抽取实体自动填充生成草稿，写入 surgery_record.template_draft 并保存 template_id")
    public Result<Map<String, Object>> generateDraft(
            @PathVariable @Parameter(description = "手术记录ID") Long recordId,
            @PathVariable @Parameter(description = "模板ID") Long templateId) {
        Map<String, Object> res = recommendService.generateDraft(recordId, templateId);
        return Result.success(String.valueOf(res.get("message")), res);
    }
}
