package com.surg.extract.controller;

import com.surg.extract.common.Result;
import com.surg.extract.dto.*;
import com.surg.extract.service.VoiceTranscriptionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;

@Slf4j
@RestController
@RequestMapping("/voice")
@RequiredArgsConstructor
@Tag(name = "实时语音转写", description = "语音录入会话创建、实时ASR、结构化抽取、病案首页填充")
public class VoiceTranscriptionController {

    private final VoiceTranscriptionService voiceService;

    @PostMapping("/session")
    @Operation(summary = "创建语音会话", description = "创建新的语音录入会话，返回sessionId，用于WebSocket连接")
    public Result<Map<String, Object>> createSession(
            @RequestParam(required = false) Long recordId,
            @RequestParam(defaultValue = "zh") String language,
            @RequestParam(defaultValue = "false") Boolean enableAutoPunctuation,
            @RequestParam(defaultValue = "true") Boolean enableRealTimeNer) {

        VoiceTranscriptionService.VoiceSession session = voiceService.createSession(recordId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("sessionId", session.getSessionId());
        result.put("recordId", session.getRecordId());
        result.put("wsUrl", "/ws/voice/" + session.getSessionId());
        result.put("language", language);
        result.put("enableAutoPunctuation", enableAutoPunctuation);
        result.put("enableRealTimeNer", enableRealTimeNer);
        result.put("startTime", session.getStartTime());
        return Result.success("会话创建成功", result);
    }

    @GetMapping("/session/{sessionId}")
    @Operation(summary = "获取会话状态", description = "查询语音会话当前状态、已转写文本、已抽取实体、病案首页填充字段")
    public Result<Map<String, Object>> getSessionStatus(@PathVariable String sessionId) {
        VoiceTranscriptionService.VoiceSession session = voiceService.getSession(sessionId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("sessionId", session.getSessionId());
        result.put("recordId", session.getRecordId());
        result.put("startTime", session.getStartTime());
        result.put("durationSeconds", session.getDurationSeconds());
        result.put("fullText", session.getFullText().toString());
        result.put("buffer", session.getBuffer().toString());
        result.put("entityCount", session.getEntities().size());
        result.put("entities", session.getEntities());
        result.put("homePageFields", session.getHomePageFields());
        return Result.success(result);
    }

    @PostMapping("/session/{sessionId}/stop")
    @Operation(summary = "结束会话", description = "手动结束语音会话，触发最终转写、实体入库、病案首页保存")
    public Result<Map<String, Object>> stopSession(@PathVariable String sessionId) {
        VoiceTranscriptionService.VoiceSession session = voiceService.finalizeSession(sessionId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("sessionId", session.getSessionId());
        result.put("recordId", session.getRecordId());
        result.put("fullText", session.getFullText().toString());
        result.put("totalChars", session.getFullText().length());
        result.put("entityCount", session.getEntities().size());
        result.put("durationSeconds", session.getDurationSeconds());
        result.put("homePageFields", session.getHomePageFields());
        result.put("entities", session.getEntities());
        voiceService.removeSession(sessionId);
        return Result.success("会话已结束", result);
    }

    @PostMapping(value = "/upload-chunk", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "上传音频分片", description = "HTTP方式上传音频分片（替代WebSocket）")
    public Result<VoiceStreamMessageDTO> uploadChunk(
            @RequestParam String sessionId,
            @RequestParam Integer seq,
            @RequestPart("chunk") MultipartFile chunk,
            @RequestParam(defaultValue = "false") Boolean lastChunk) {
        try {
            byte[] data = chunk.getBytes();
            VoiceStreamMessageDTO result = voiceService.processChunk(sessionId, data, seq,
                    Boolean.TRUE.equals(lastChunk));
            if (result == null) {
                result = VoiceStreamMessageDTO.partial(sessionId, "");
            }
            return Result.success(result);
        } catch (Exception e) {
            log.error("处理音频分片失败", e);
            return Result.success(VoiceStreamMessageDTO.error(sessionId, e.getMessage()));
        }
    }

    @PostMapping("/text-chunk")
    @Operation(summary = "手动录入文本段", description = "支持手动输入文本段，系统自动处理标点、抽取实体、填充首页")
    public Result<Map<String, Object>> submitTextChunk(
            @RequestParam String sessionId,
            @RequestBody Map<String, String> body) {

        String text = body.get("text");
        String sentence = voiceService.addPunctuation(text.trim());
        java.util.List<com.surg.extract.entity.SurgeryEntity> entities =
                voiceService.extractEntitiesFromText(sentence);
        Map<String, Object> fields = voiceService.updateHomePageFromEntities(sessionId, entities);

        VoiceTranscriptionService.VoiceSession session = voiceService.getSession(sessionId);
        session.getFullText().append(sentence).append(" ");

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sentence", sentence);
        payload.put("entities", entities);
        payload.put("homePageFields", fields);
        return Result.success(payload);
    }

    @PostMapping("/add-punctuation")
    @Operation(summary = "断句与标点添加", description = "对原始文本自动断句并添加标点符号")
    public Result<String> addPunctuation(@RequestBody Map<String, String> body) {
        return Result.success(voiceService.addPunctuation(body.get("text")));
    }
}
