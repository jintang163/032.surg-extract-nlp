package com.surg.extract.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VoiceStreamMessageDTO {

    private String type;

    private String sessionId;

    private String text;

    private Boolean isFinal;

    private String errorMsg;

    private Object data;

    private String timestamp;

    public static VoiceStreamMessageDTO partial(String sessionId, String text) {
        return VoiceStreamMessageDTO.builder()
                .type("PARTIAL")
                .sessionId(sessionId)
                .text(text)
                .isFinal(false)
                .timestamp(LocalDateTime.now().toString())
                .build();
    }

    public static VoiceStreamMessageDTO finalSegment(String sessionId, String text, Object data) {
        return VoiceStreamMessageDTO.builder()
                .type("FINAL_SEGMENT")
                .sessionId(sessionId)
                .text(text)
                .isFinal(true)
                .data(data)
                .timestamp(LocalDateTime.now().toString())
                .build();
    }

    public static VoiceStreamMessageDTO homePageUpdate(String sessionId, Object data) {
        return VoiceStreamMessageDTO.builder()
                .type("HOME_PAGE_UPDATE")
                .sessionId(sessionId)
                .data(data)
                .timestamp(LocalDateTime.now().toString())
                .build();
    }

    public static VoiceStreamMessageDTO entityUpdate(String sessionId, Object data) {
        return VoiceStreamMessageDTO.builder()
                .type("ENTITY_UPDATE")
                .sessionId(sessionId)
                .data(data)
                .timestamp(LocalDateTime.now().toString())
                .build();
    }

    public static VoiceStreamMessageDTO started(String sessionId) {
        return VoiceStreamMessageDTO.builder()
                .type("SESSION_STARTED")
                .sessionId(sessionId)
                .timestamp(LocalDateTime.now().toString())
                .build();
    }

    public static VoiceStreamMessageDTO stopped(String sessionId, String fullText, Object data) {
        return VoiceStreamMessageDTO.builder()
                .type("SESSION_STOPPED")
                .sessionId(sessionId)
                .text(fullText)
                .isFinal(true)
                .data(data)
                .timestamp(LocalDateTime.now().toString())
                .build();
    }

    public static VoiceStreamMessageDTO error(String sessionId, String errorMsg) {
        return VoiceStreamMessageDTO.builder()
                .type("ERROR")
                .sessionId(sessionId)
                .errorMsg(errorMsg)
                .timestamp(LocalDateTime.now().toString())
                .build();
    }
}
