package com.surg.extract.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.surg.extract.dto.VoiceStreamMessageDTO;
import com.surg.extract.service.VoiceTranscriptionService;
import jakarta.websocket.*;
import jakarta.websocket.server.PathParam;
import jakarta.websocket.server.ServerEndpoint;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Component
@ServerEndpoint("/ws/voice/{sessionId}")
public class VoiceTranscriptionWebSocket {

    private static final ConcurrentHashMap<String, Session> SESSIONS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, AtomicInteger> SEQ_COUNTERS = new ConcurrentHashMap<>();

    private static VoiceTranscriptionService transcriptionService;
    private static ObjectMapper objectMapper;

    @Autowired
    public void setTranscriptionService(VoiceTranscriptionService service) {
        VoiceTranscriptionWebSocket.transcriptionService = service;
    }

    @Autowired
    public void setObjectMapper(ObjectMapper mapper) {
        VoiceTranscriptionWebSocket.objectMapper = mapper;
    }

    @OnOpen
    public void onOpen(@PathParam("sessionId") String sessionId, Session session) {
        SESSIONS.put(sessionId, session);
        SEQ_COUNTERS.put(sessionId, new AtomicInteger(0));
        log.info("WebSocket连接建立: sessionId={}", sessionId);
        sendMessage(sessionId, VoiceStreamMessageDTO.started(sessionId));
    }

    @OnMessage
    public void onBinary(@PathParam("sessionId") String sessionId, byte[] data, Session session) {
        try {
            AtomicInteger counter = SEQ_COUNTERS.get(sessionId);
            int seq = counter != null ? counter.incrementAndGet() : -1;
            VoiceStreamMessageDTO result = transcriptionService.processChunk(sessionId, data, seq, false);
            if (result != null) {
                sendMessage(sessionId, result);
                if ("FINAL_SEGMENT".equals(result.getType()) && result.getData() instanceof java.util.Map) {
                    java.util.Map<?, ?> payload = (java.util.Map<?, ?>) result.getData();
                    if (payload.get("homePageFields") != null) {
                        sendMessage(sessionId, VoiceStreamMessageDTO.homePageUpdate(sessionId, payload.get("homePageFields")));
                    }
                    if (payload.get("entities") != null) {
                        sendMessage(sessionId, VoiceStreamMessageDTO.entityUpdate(sessionId, payload.get("entities")));
                    }
                }
            }
        } catch (Exception e) {
            log.error("处理语音chunk失败: sessionId={}", sessionId, e);
            sendMessage(sessionId, VoiceStreamMessageDTO.error(sessionId, e.getMessage()));
        }
    }

    @OnMessage
    public void onText(@PathParam("sessionId") String sessionId, String text, Session session) {
        try {
            if ("PING".equalsIgnoreCase(text) || "HEARTBEAT".equalsIgnoreCase(text)) {
                sendMessage(sessionId, VoiceStreamMessageDTO.builder()
                        .type("PONG")
                        .sessionId(sessionId)
                        .build());
                return;
            }
            if ("STOP".equalsIgnoreCase(text) || "END".equalsIgnoreCase(text)) {
                try {
                    VoiceStreamMessageDTO flushed = transcriptionService.flushBuffer(sessionId);
                    if (flushed != null) {
                        sendMessage(sessionId, flushed);
                        if ("FINAL_SEGMENT".equals(flushed.getType()) && flushed.getData() instanceof java.util.Map) {
                            java.util.Map<?, ?> payload = (java.util.Map<?, ?>) flushed.getData();
                            if (payload.get("homePageFields") != null) {
                                sendMessage(sessionId, VoiceStreamMessageDTO.homePageUpdate(sessionId, payload.get("homePageFields")));
                            }
                            if (payload.get("entities") != null) {
                                sendMessage(sessionId, VoiceStreamMessageDTO.entityUpdate(sessionId, payload.get("entities")));
                            }
                        }
                    }
                } catch (Exception e) {
                    log.warn("STOP前flush失败: sessionId={}", sessionId, e);
                }
                VoiceTranscriptionService.VoiceSession vs = transcriptionService.finalizeSession(sessionId);
                sendMessage(sessionId, VoiceStreamMessageDTO.stopped(sessionId,
                        vs.getFullText().toString(), vs.getHomePageFields()));
                return;
            }
            if ("FLUSH".equalsIgnoreCase(text)) {
                try {
                    VoiceStreamMessageDTO result = transcriptionService.flushBuffer(sessionId);
                    if (result != null) {
                        sendMessage(sessionId, result);
                        if ("FINAL_SEGMENT".equals(result.getType()) && result.getData() instanceof java.util.Map) {
                            java.util.Map<?, ?> payload = (java.util.Map<?, ?>) result.getData();
                            if (payload.get("homePageFields") != null) {
                                sendMessage(sessionId, VoiceStreamMessageDTO.homePageUpdate(sessionId, payload.get("homePageFields")));
                            }
                            if (payload.get("entities") != null) {
                                sendMessage(sessionId, VoiceStreamMessageDTO.entityUpdate(sessionId, payload.get("entities")));
                            }
                        }
                    }
                } catch (Exception e) {
                    log.warn("FLUSH失败: sessionId={}", sessionId, e);
                }
                return;
            }
        } catch (Exception e) {
            log.error("处理文本消息失败: sessionId={}", sessionId, e);
            sendMessage(sessionId, VoiceStreamMessageDTO.error(sessionId, e.getMessage()));
        }
    }

    @OnClose
    public void onClose(@PathParam("sessionId") String sessionId, Session session, CloseReason reason) {
        try {
            VoiceStreamMessageDTO flushed = transcriptionService.flushBuffer(sessionId);
            if (flushed != null) {
                sendMessage(sessionId, flushed);
                if ("FINAL_SEGMENT".equals(flushed.getType()) && flushed.getData() instanceof java.util.Map) {
                    java.util.Map<?, ?> payload = (java.util.Map<?, ?>) flushed.getData();
                    if (payload.get("homePageFields") != null) {
                        sendMessage(sessionId, VoiceStreamMessageDTO.homePageUpdate(sessionId, payload.get("homePageFields")));
                    }
                    if (payload.get("entities") != null) {
                        sendMessage(sessionId, VoiceStreamMessageDTO.entityUpdate(sessionId, payload.get("entities")));
                    }
                }
            }
        } catch (Exception e) {
            log.warn("onClose flush失败: sessionId={}", sessionId, e);
        }
        SESSIONS.remove(sessionId);
        SEQ_COUNTERS.remove(sessionId);
        try {
            transcriptionService.removeSession(sessionId);
        } catch (Exception ignored) {}
        log.info("WebSocket关闭: sessionId={}, reason={}", sessionId, reason);
    }

    @OnError
    public void onError(@PathParam("sessionId") String sessionId, Session session, Throwable throwable) {
        log.error("WebSocket错误: sessionId={}", sessionId, throwable);
        SESSIONS.remove(sessionId);
        SEQ_COUNTERS.remove(sessionId);
    }

    public static void sendMessage(String sessionId, Object message) {
        Session s = SESSIONS.get(sessionId);
        if (s == null || !s.isOpen()) return;
        try {
            String json;
            if (message instanceof String) {
                json = (String) message;
            } else {
                json = objectMapper.writeValueAsString(message);
            }
            synchronized (s) {
                s.getBasicRemote().sendText(json);
            }
        } catch (Exception e) {
            log.warn("发送WS消息失败: sessionId={}", sessionId, e);
        }
    }

    public static boolean isSessionConnected(String sessionId) {
        Session s = SESSIONS.get(sessionId);
        return s != null && s.isOpen();
    }
}
