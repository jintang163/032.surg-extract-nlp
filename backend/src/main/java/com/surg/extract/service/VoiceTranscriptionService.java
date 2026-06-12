package com.surg.extract.service;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.surg.extract.common.BusinessException;
import com.surg.extract.common.ErrorCode;
import com.surg.extract.dto.*;
import com.surg.extract.entity.MedicalRecordHome;
import com.surg.extract.entity.SurgeryEntity;
import com.surg.extract.entity.SurgeryRecord;
import com.surg.extract.feign.NlpServiceClient;
import com.surg.extract.mapper.MedicalRecordHomeMapper;
import com.surg.extract.mapper.SurgeryEntityMapper;
import com.surg.extract.mapper.SurgeryRecordMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class VoiceTranscriptionService {

    private static final Set<String> SENTENCE_ENDINGS = new HashSet<>(
            Arrays.asList("。", "！", "!", "？", "?", ".", "；", ";", "\n", "。", "，,", "，"));
    private static final Pattern PATIENT_PATTERN = Pattern.compile("患者[是为叫](.+?)[，,。.！!？?；;\\s]");
    private static final Pattern SURGERY_PATTERN = Pattern.compile("(行|做|进行|实施)(.+?)(术|手术)");
    private static final Pattern BLOOD_PATTERN = Pattern.compile("出血约?(\\d+(?:\\.\\d+)?)\\s*(ml|毫升)");
    private static final Pattern ANESTHESIA_PATTERN = Pattern.compile("(全麻|全身麻醉|硬膜外麻醉|椎管内麻醉|腰麻|局部麻醉|局麻|静脉麻醉|吸入麻醉)");

    private final NlpServiceClient nlpClient;
    private final MedicalRecordHomeMapper homePageMapper;
    private final SurgeryRecordMapper recordMapper;
    private final SurgeryEntityMapper entityMapper;
    private final ObjectMapper objectMapper;
    private final MedicalRecordHomeService homeService;

    private final Map<String, VoiceSession> sessions = new ConcurrentHashMap<>();

    public VoiceSession createSession(Long recordId) {
        String sessionId = IdUtil.fastSimpleUUID();
        VoiceSession session = new VoiceSession();
        session.setSessionId(sessionId);
        session.setRecordId(recordId);
        session.setStartTime(LocalDateTime.now());
        session.setBuffer(new StringBuilder());
        session.setFullText(new StringBuilder());
        session.setEntities(new ArrayList<>());
        session.setHomePageFields(new LinkedHashMap<>());
        sessions.put(sessionId, session);
        log.info("创建语音会话: sessionId={}, recordId={}", sessionId, recordId);
        return session;
    }

    public VoiceSession getSession(String sessionId) {
        VoiceSession session = sessions.get(sessionId);
        if (session == null) {
            throw new BusinessException(ErrorCode.TEMPLATE_NOT_FOUND.getCode(), "语音会话不存在或已过期");
        }
        return session;
    }

    public void removeSession(String sessionId) {
        sessions.remove(sessionId);
    }

    public String appendAndDetect(String sessionId, String partialText, boolean isEndOfUtterance) {
        VoiceSession session = getSession(sessionId);
        StringBuilder buffer = session.getBuffer();
        StringBuilder fullText = session.getFullText();

        if (StrUtil.isNotBlank(partialText)) {
            buffer.append(partialText);
        }

        String completedSentence = null;

        if (isEndOfUtterance) {
            completedSentence = addPunctuation(buffer.toString().trim());
            if (StrUtil.isNotBlank(completedSentence)) {
                fullText.append(completedSentence).append(" ");
                session.setLastSegmentTime(LocalDateTime.now());
                buffer.setLength(0);
            }
        } else {
            String result = detectAndSplitSentence(buffer.toString());
            if (result != null) {
                completedSentence = result;
                fullText.append(completedSentence).append(" ");
                session.setLastSegmentTime(LocalDateTime.now());
                int cutLen = findSentenceEndIndex(buffer.toString());
                if (cutLen > 0 && cutLen <= buffer.length()) {
                    buffer.delete(0, cutLen);
                }
            }
        }

        return completedSentence;
    }

    private String detectAndSplitSentence(String text) {
        if (StrUtil.isBlank(text) || text.length() < 6) return null;
        int idx = findSentenceEndIndex(text);
        if (idx > 0) {
            String sentence = text.substring(0, idx).trim();
            if (sentence.length() >= 3) {
                return addPunctuation(sentence);
            }
        }
        return null;
    }

    private int findSentenceEndIndex(String text) {
        for (int i = Math.min(text.length() - 1, 150); i >= Math.max(3, text.length() - 150); i--) {
            char c = text.charAt(i);
            if (SENTENCE_ENDINGS.contains(String.valueOf(c))) {
                return i + 1;
            }
        }
        if (text.length() >= 100) {
            int lastComma = -1;
            for (int i = text.length() - 1; i >= Math.max(3, text.length() - 60); i--) {
                char c = text.charAt(i);
                if (c == '，' || c == ',' || c == ' ' || c == '。') {
                    lastComma = i + 1;
                    break;
                }
            }
            return lastComma > 0 ? lastComma : text.length();
        }
        return -1;
    }

    public String addPunctuation(String text) {
        if (StrUtil.isBlank(text)) return text;
        String t = text.trim();

        String last = t.substring(t.length() - 1);
        if (!SENTENCE_ENDINGS.contains(last) && !last.matches("[a-zA-Z0-9]")) {
            if (t.contains("请问") || t.contains("有没有") || t.contains("是否") || t.contains("吗？") || t.contains("呢")) {
                t = t + "？";
            } else if (t.contains("注意") || t.contains("禁止") || t.contains("必须") || t.contains("！")) {
                t = t + "！";
            } else {
                t = t + "。";
            }
        }

        t = t.replaceAll("([，,])(\\s*)(患者|手术|麻醉|出血|切口|探查|分离|缝合|切除|冲洗|放置|置入|游离|结扎|切断|剪开|切开|牵拉|止血|冲洗|清点|逐层)，", "$1$2");

        return t;
    }

    public List<SurgeryEntity> extractEntitiesFromText(String sentence) {
        List<SurgeryEntity> result = new ArrayList<>();
        if (StrUtil.isBlank(sentence)) return result;

        try {
            NlpNerRequest request = new NlpNerRequest();
            request.setText(sentence);
            request.setDomain("surgery");
            request.setIncludeConfidence(true);
            NlpNerResponse response = nlpClient.extractEntities(request);
            if (response != null && Boolean.TRUE.equals(response.getSuccess()) && response.getEntities() != null) {
                for (NerEntityDTO dto : response.getEntities()) {
                    SurgeryEntity entity = new SurgeryEntity();
                    entity.setEntityType(dto.getEntityType());
                    entity.setEntityValue(dto.getEntityValue());
                    entity.setEntityUnit(dto.getEntityUnit());
                    entity.setConfidence(dto.getConfidence());
                    entity.setVerified(0);
                    entity.setSource("ASR_REALTIME");
                    result.add(entity);
                }
            }
        } catch (Exception e) {
            log.warn("实时NER抽取失败，使用规则提取: {}", e.getMessage());
            result.addAll(extractByRules(sentence));
        }

        if (result.isEmpty()) {
            result.addAll(extractByRules(sentence));
        }

        return result;
    }

    private List<SurgeryEntity> extractByRules(String text) {
        List<SurgeryEntity> result = new ArrayList<>();
        if (StrUtil.isBlank(text)) return result;

        var surgeryMatcher = SURGERY_PATTERN.matcher(text);
        if (surgeryMatcher.find()) {
            String name = surgeryMatcher.group(2) + surgeryMatcher.group(3);
            if (name.length() >= 2) {
                SurgeryEntity e = new SurgeryEntity();
                e.setEntityType("SURGERY_NAME");
                e.setEntityValue(name.trim());
                e.setSource("RULE_ASR");
                e.setConfidence(0.6);
                e.setVerified(0);
                result.add(e);
            }
        }

        var bloodMatcher = BLOOD_PATTERN.matcher(text);
        if (bloodMatcher.find()) {
            SurgeryEntity e = new SurgeryEntity();
            e.setEntityType("BLOOD_LOSS");
            e.setEntityValue(bloodMatcher.group(1));
            e.setEntityUnit(bloodMatcher.group(2));
            e.setSource("RULE_ASR");
            e.setConfidence(0.85);
            e.setVerified(0);
            result.add(e);
        }

        var anesMatcher = ANESTHESIA_PATTERN.matcher(text);
        if (anesMatcher.find()) {
            SurgeryEntity e = new SurgeryEntity();
            e.setEntityType("ANESTHESIA_TYPE");
            e.setEntityValue(anesMatcher.group(1));
            e.setSource("RULE_ASR");
            e.setConfidence(0.9);
            e.setVerified(0);
            result.add(e);
        }

        return result;
    }

    public Map<String, Object> updateHomePageFromEntities(String sessionId, List<SurgeryEntity> newEntities) {
        VoiceSession session = getSession(sessionId);
        Map<String, Object> fields = session.getHomePageFields();

        Map<String, SurgeryEntity> latestByType = new LinkedHashMap<>();
        for (SurgeryEntity e : session.getEntities()) {
            latestByType.put(e.getEntityType(), e);
        }
        for (SurgeryEntity e : newEntities) {
            latestByType.put(e.getEntityType(), e);
            session.getEntities().add(e);
        }

        Map<String, String> mapping = getEntityToHomeMapping();

        for (Map.Entry<String, String> entry : mapping.entrySet()) {
            String entityType = entry.getKey();
            String homeField = entry.getValue();
            SurgeryEntity entity = latestByType.get(entityType);
            if (entity != null && StrUtil.isNotBlank(entity.getEntityValue())) {
                fields.put(homeField, convertValue(homeField, entity.getEntityValue()));
            }
        }

        return fields;
    }

    private Map<String, String> getEntityToHomeMapping() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("PATIENT_NAME", "patientName");
        map.put("GENDER", "gender");
        map.put("AGE", "age");
        map.put("HOSPITAL_NO", "hospitalNo");
        map.put("DEPARTMENT", "department");
        map.put("SURGERY_DATE", "surgeryDate");
        map.put("SURGERY_NAME", "surgeryName");
        map.put("SURGERY_CODE", "surgeryCode");
        map.put("INCISION_LEVEL", "incisionLevel");
        map.put("INCISION_HEALING", "incisionHealing");
        map.put("ANESTHESIA_TYPE", "anesthesiaType");
        map.put("ANESTHESIA_CODE", "anesthesiaCode");
        map.put("BLOOD_LOSS", "bloodLoss");
        map.put("BLOOD_TRANSFUSION", "bloodTransfusion");
        map.put("FLUID_INFUSION", "fluidInfusion");
        map.put("COMPLICATION", "complications");
        map.put("SURGEON", "surgeon");
        map.put("ASSISTANT", "assistant1");
        map.put("ANESTHESIOLOGIST", "anesthesiologist");
        map.put("SCRUB_NURSE", "scrubNurse");
        map.put("CIRCULATING_NURSE", "circulatingNurse");
        map.put("PREOP_DIAGNOSIS", "admissionDiagnosis");
        map.put("POSTOP_DIAGNOSIS", "dischargeDiagnosis");
        return map;
    }

    private Object convertValue(String field, String value) {
        if (StrUtil.isBlank(value)) return null;
        try {
            switch (field) {
                case "age":
                    return Integer.parseInt(value.replaceAll("\\D", ""));
                case "bloodLoss":
                case "bloodTransfusion":
                case "fluidInfusion":
                    return new java.math.BigDecimal(value.replaceAll("\\D", ""));
                default:
                    return value;
            }
        } catch (Exception e) {
            return value;
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public VoiceSession finalizeSession(String sessionId) {
        VoiceSession session = getSession(sessionId);
        session.setEndTime(LocalDateTime.now());

        StringBuilder buffer = session.getBuffer();
        if (buffer.length() > 0) {
            String last = addPunctuation(buffer.toString().trim());
            if (StrUtil.isNotBlank(last)) {
                session.getFullText().append(last);
            }
            buffer.setLength(0);
        }

        String fullText = session.getFullText().toString();
        Long recordId = session.getRecordId();

        if (recordId != null) {
            SurgeryRecord record = recordMapper.selectById(recordId);
            if (record != null) {
                record.setAsrText(fullText);
                record.setMultimodalStatus("ASR_DONE");
                record.setAsrSegments(toJson(buildSegments(session)));
                if (session.getDurationSeconds() != null) {
                    record.setAudioDuration(session.getDurationSeconds().doubleValue());
                }
                recordMapper.updateById(record);

                List<SurgeryEntity> allEntities = session.getEntities();
                for (SurgeryEntity e : allEntities) {
                    e.setRecordId(recordId);
                    e.setCreatedTime(LocalDateTime.now());
                }
                if (!allEntities.isEmpty()) {
                    try {
                        entityMapper.batchInsert(allEntities);
                    } catch (Exception ignored) {
                        for (SurgeryEntity e : allEntities) {
                            try { entityMapper.insert(e); } catch (Exception ignored2) {}
                        }
                    }
                }

                Map<String, Object> fields = session.getHomePageFields();
                if (!fields.isEmpty()) {
                    MedicalRecordHome home = homePageMapper.selectByRecordId(recordId);
                    if (home == null) {
                        home = new MedicalRecordHome();
                        home.setRecordId(recordId);
                        home.setStatus("DRAFT");
                        homePageMapper.insert(home);
                    }
                    applyFieldsToHome(home, fields);
                    homePageMapper.updateById(home);
                }
            }
        }

        log.info("语音会话结束: sessionId={}, 总字数={}, 实体数={}", sessionId,
                fullText.length(), session.getEntities().size());
        return session;
    }

    private List<AsrSegmentDTO> buildSegments(VoiceSession session) {
        List<AsrSegmentDTO> list = new ArrayList<>();
        AsrSegmentDTO seg = new AsrSegmentDTO();
        seg.setSegmentIndex(0);
        seg.setSpeaker("医生");
        seg.setText(session.getFullText().toString());
        seg.setStartTime(0.0);
        seg.setEndTime(session.getDurationSeconds() != null ? session.getDurationSeconds().doubleValue() : 0.0);
        seg.setConfidence(0.9);
        list.add(seg);
        return list;
    }

    private void applyFieldsToHome(MedicalRecordHome home, Map<String, Object> fields) {
        for (Map.Entry<String, Object> entry : fields.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (value == null) continue;
            try {
                switch (key) {
                    case "patientName": home.setPatientName(String.valueOf(value)); break;
                    case "gender": home.setGender(String.valueOf(value)); break;
                    case "age": home.setAge(((Number) value).intValue()); break;
                    case "hospitalNo": home.setHospitalNo(String.valueOf(value)); break;
                    case "department": home.setDepartment(String.valueOf(value)); break;
                    case "surgeryDate":
                        if (value instanceof LocalDateTime) home.setSurgeryDate((LocalDateTime) value);
                        break;
                    case "surgeryName": home.setSurgeryName(String.valueOf(value)); break;
                    case "surgeryCode": home.setSurgeryCode(String.valueOf(value)); break;
                    case "incisionLevel": home.setIncisionLevel(String.valueOf(value)); break;
                    case "incisionHealing": home.setIncisionHealing(String.valueOf(value)); break;
                    case "anesthesiaType": home.setAnesthesiaType(String.valueOf(value)); break;
                    case "anesthesiaCode": home.setAnesthesiaCode(String.valueOf(value)); break;
                    case "bloodLoss": home.setBloodLoss(new java.math.BigDecimal(String.valueOf(value))); break;
                    case "bloodTransfusion": home.setBloodTransfusion(new java.math.BigDecimal(String.valueOf(value))); break;
                    case "fluidInfusion": home.setFluidInfusion(new java.math.BigDecimal(String.valueOf(value))); break;
                    case "surgeon": home.setSurgeon(String.valueOf(value)); break;
                    case "assistant1": home.setAssistant1(String.valueOf(value)); break;
                    case "anesthesiologist": home.setAnesthesiologist(String.valueOf(value)); break;
                    case "scrubNurse": home.setScrubNurse(String.valueOf(value)); break;
                    case "circulatingNurse": home.setCirculatingNurse(String.valueOf(value)); break;
                    case "admissionDiagnosis": home.setAdmissionDiagnosis(String.valueOf(value)); break;
                    case "dischargeDiagnosis": home.setDischargeDiagnosis(String.valueOf(value)); break;
                    case "complications":
                        home.setComplications(toJson(Collections.singletonList(String.valueOf(value))));
                        break;
                }
            } catch (Exception e) {
                log.warn("应用首页字段失败: field={}, value={}", key, value);
            }
        }
    }

    public VoiceStreamMessageDTO processChunk(String sessionId, byte[] audioChunk, int seq, boolean lastChunk) {
        VoiceSession session = getSession(sessionId);
        session.setLastActivityTime(LocalDateTime.now());

        if (audioChunk == null || audioChunk.length == 0) {
            return null;
        }

        try {
            session.getAudioBuffer().write(audioChunk);
        } catch (IOException e) {
            log.warn("写入音频缓冲失败", e);
        }

        String fakePartial = simulatePartialAsr(session, audioChunk.length);
        if (StrUtil.isNotBlank(fakePartial)) {
            session.setSimulatedText(session.getSimulatedText() + fakePartial);
        }

        if (lastChunk || seq % 10 == 0) {
            try {
                return processAccumulatedAudio(session);
            } catch (Exception e) {
                log.warn("处理累积音频失败，模拟转写: {}", e.getMessage());
                return processBySimulation(session, seq, lastChunk);
            }
        }

        if (StrUtil.isNotBlank(fakePartial)) {
            return VoiceStreamMessageDTO.partial(sessionId, fakePartial);
        }
        return null;
    }

    private VoiceStreamMessageDTO processBySimulation(VoiceSession session, int seq, boolean lastChunk) {
        String text = session.getSimulatedText();
        if (StrUtil.isBlank(text)) return null;
        String sentence = appendAndDetect(session.getSessionId(), text, lastChunk || seq % 50 == 0);
        session.setSimulatedText("");

        if (StrUtil.isNotBlank(sentence)) {
            List<SurgeryEntity> newEntities = extractEntitiesFromText(sentence);
            Map<String, Object> homeFields = updateHomePageFromEntities(session.getSessionId(), newEntities);
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("sentence", sentence);
            payload.put("entities", newEntities);
            payload.put("homePageFields", homeFields);
            return VoiceStreamMessageDTO.finalSegment(session.getSessionId(), sentence, payload);
        }
        return VoiceStreamMessageDTO.partial(session.getSessionId(), text);
    }

    private VoiceStreamMessageDTO processAccumulatedAudio(VoiceSession session) {
        byte[] audio = session.getAudioBuffer().toByteArray();
        if (audio.length < 2048) return null;

        try {
            MultipartFile mf = new MockMultipartFile("file", "chunk.webm",
                    "audio/webm", audio);
            AsrProcessResponseDTO response = nlpClient.processAsr(mf, "zh");
            if (response != null && Boolean.TRUE.equals(response.getSuccess())
                    && StrUtil.isNotBlank(response.getFullText())) {
                String text = response.getFullText();
                String sentence = appendAndDetect(session.getSessionId(), text, true);
                session.getAudioBuffer().reset();
                session.setSimulatedText("");

                if (StrUtil.isNotBlank(sentence)) {
                    List<SurgeryEntity> newEntities = extractEntitiesFromText(sentence);
                    Map<String, Object> homeFields = updateHomePageFromEntities(session.getSessionId(), newEntities);
                    Map<String, Object> payload = new LinkedHashMap<>();
                    payload.put("sentence", sentence);
                    payload.put("entities", newEntities);
                    payload.put("homePageFields", homeFields);
                    session.setLastSentence(sentence);
                    return VoiceStreamMessageDTO.finalSegment(session.getSessionId(), sentence, payload);
                }
                return VoiceStreamMessageDTO.partial(session.getSessionId(), text);
            }
        } catch (Exception e) {
            log.warn("ASR调用失败: {}", e.getMessage());
        }
        return null;
    }

    private String simulatePartialAsr(VoiceSession session, int chunkLen) {
        return "";
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return null;
        }
    }

    @Data
    public static class VoiceSession {
        private String sessionId;
        private Long recordId;
        private LocalDateTime startTime;
        private LocalDateTime endTime;
        private LocalDateTime lastActivityTime;
        private LocalDateTime lastSegmentTime;
        private StringBuilder buffer;
        private StringBuilder fullText;
        private String simulatedText = "";
        private String lastSentence;
        private List<SurgeryEntity> entities;
        private Map<String, Object> homePageFields;
        private ByteArrayOutputStream audioBuffer = new ByteArrayOutputStream();

        public Long getDurationSeconds() {
            if (startTime == null) return null;
            LocalDateTime end = endTime != null ? endTime : LocalDateTime.now();
            return java.time.Duration.between(startTime, end).getSeconds();
        }
    }
}
