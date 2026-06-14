package com.surg.extract.service;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.surg.extract.common.BusinessException;
import com.surg.extract.common.ErrorCode;
import com.surg.extract.dto.NextStepRecommendDTO;
import com.surg.extract.dto.SurgeryTemplateDTO;
import com.surg.extract.entity.SurgeryEntity;
import com.surg.extract.entity.SurgeryRecord;
import com.surg.extract.entity.SurgeryTemplate;
import com.surg.extract.mapper.SurgeryEntityMapper;
import com.surg.extract.mapper.SurgeryRecordMapper;
import com.surg.extract.mapper.SurgeryTemplateMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class NextStepRecommendService {

    private final SurgeryTemplateMapper templateMapper;
    private final SurgeryRecordMapper recordMapper;
    private final SurgeryEntityMapper entityMapper;
    private final SurgeryTemplateService templateService;
    private final SurgeryRecordService recordService;
    private final ObjectMapper objectMapper;

    public List<NextStepRecommendDTO> getRecommendations(Long recordId, Long userId) {
        SurgeryRecord record = recordMapper.selectById(recordId);
        if (record == null) {
            throw new BusinessException(ErrorCode.RECORD_NOT_FOUND);
        }

        List<SurgeryEntity> entities = entityMapper.selectByRecordId(recordId);
        Map<String, String> entityMap = entities.stream()
                .collect(Collectors.toMap(
                        SurgeryEntity::getEntityType,
                        e -> e.getEntityValue() == null ? "" : e.getEntityValue(),
                        (a, b) -> a
                ));

        String surgeryName = entityMap.getOrDefault("SURGERY_NAME", "");
        String preopDiagnosis = entityMap.getOrDefault("PREOP_DIAGNOSIS", "");
        String surgeryType = entityMap.getOrDefault("SURGERY_LEVEL", "");
        String department = record.getDepartment() == null ? "" : record.getDepartment();

        Long effectiveUserId = (userId != null) ? userId : record.getUploadUserId();

        List<SurgeryTemplate> activeTemplates = templateMapper.selectAvailableTemplates(surgeryType, department);
        if (CollectionUtils.isEmpty(activeTemplates)) {
            activeTemplates = templateMapper.selectAvailableTemplates(null, null);
        }

        Map<Long, Double> collaborativeScores = buildCollaborativeScores(
                effectiveUserId, department, surgeryName, activeTemplates);
        Map<Long, Double> contentScores = buildContentScores(
                activeTemplates, surgeryName, preopDiagnosis, department, surgeryType);
        Map<Long, Double> popularityScores = buildPopularityScores(activeTemplates);

        List<NextStepRecommendDTO> results = new ArrayList<>();
        for (SurgeryTemplate tpl : activeTemplates) {
            double collab = collaborativeScores.getOrDefault(tpl.getId(), 0.0);
            double content = contentScores.getOrDefault(tpl.getId(), 0.0);
            double popular = popularityScores.getOrDefault(tpl.getId(), 0.0);
            double finalScore = collab * 0.45 + content * 0.35 + popular * 0.20;

            NextStepRecommendDTO dto = new NextStepRecommendDTO();
            dto.setTemplateId(tpl.getId());
            dto.setTemplateCode(tpl.getTemplateCode());
            dto.setTemplateName(tpl.getTemplateName());
            dto.setDocumentType(inferDocumentType(tpl));
            dto.setDescription(tpl.getDescription());
            dto.setTags(parseTagList(tpl.getTags()));
            dto.setDepartment(tpl.getDepartment());
            dto.setSurgeryType(tpl.getSurgeryType());
            dto.setUseCount(tpl.getUseCount() == null ? 0 : tpl.getUseCount());
            dto.setIsDefault(tpl.getIsDefault() != null && tpl.getIsDefault() == 1);
            dto.setScore(finalScore);
            dto.setContentScore(content);
            dto.setCollaborativeScore(collab);
            dto.setPopularityScore(popular);
            dto.setPlaceholdersCount(countPlaceholders(tpl.getPlaceholders()));
            dto.setRecommendedReason(buildReason(collab, content, popular, dto.getDocumentType()));
            dto.setExpectedDurationMinutes(inferExpectedDuration(dto.getDocumentType()));
            results.add(dto);
        }

        results.sort((a, b) -> Double.compare(b.getScore(), a.getScore()));

        int rank = 1;
        for (NextStepRecommendDTO dto : results) {
            dto.setRank(rank++);
        }

        log.info("下一步推荐完成: recordId={}, userId={}, 候选{}条, Top1={}",
                recordId, effectiveUserId, results.size(),
                results.isEmpty() ? "-" : results.get(0).getTemplateName());
        return results.size() > 8 ? results.subList(0, 8) : results;
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> generateDraft(Long recordId, Long templateId) {
        if (recordId == null || templateId == null) {
            throw new IllegalArgumentException("recordId 和 templateId 不能为空");
        }
        SurgeryRecord record = recordMapper.selectById(recordId);
        if (record == null) {
            throw new BusinessException(ErrorCode.RECORD_NOT_FOUND);
        }
        SurgeryTemplate tpl = templateMapper.selectById(templateId);
        if (tpl == null || tpl.getDeleted() == 1) {
            throw new BusinessException(ErrorCode.TEMPLATE_NOT_FOUND);
        }

        String draft = templateService.fillTemplateFromRecord(templateId, recordId);
        recordService.saveTemplateDraft(recordId, templateId, draft);
        templateMapper.incrementUseCount(templateId);

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("recordId", recordId);
        res.put("templateId", templateId);
        res.put("templateName", tpl.getTemplateName());
        res.put("documentType", inferDocumentType(tpl));
        res.put("draftPreview", draft.length() > 500 ? draft.substring(0, 500) + "..." : draft);
        res.put("draftLength", draft.length());
        res.put("message", String.format("「%s」草稿已生成，可在模板编辑器中继续编辑", tpl.getTemplateName()));
        log.info("一键生成草稿成功: recordId={}, templateId={}, len={}", recordId, templateId, draft.length());
        return res;
    }

    private Map<Long, Double> buildCollaborativeScores(Long userId, String department,
                                                        String surgeryName,
                                                        List<SurgeryTemplate> activeTemplates) {
        Map<Long, Double> scores = new HashMap<>();
        if (userId == null && StrUtil.isBlank(department)) return scores;

        LocalDateTime fromTime = LocalDateTime.now().minus(6, ChronoUnit.MONTHS);
        List<SurgeryRecord> similarRecords = recordMapper.selectRecentCompletedByDepartment(department, fromTime);
        if (CollectionUtils.isEmpty(similarRecords)) {
            similarRecords = Collections.emptyList();
        }

        Map<Long, Integer> templateFrequency = new HashMap<>();
        int similarRecordCount = 0;
        for (SurgeryRecord r : similarRecords) {
            if (r.getTemplateId() == null) continue;
            boolean surgeryMatched = StrUtil.isNotBlank(surgeryName)
                    && StrUtil.isNotBlank(r.getOriginalFileName())
                    && similarityWords(surgeryName, r.getOriginalFileName()) > 0.15;
            boolean sameUser = userId != null && userId.equals(r.getUploadUserId());
            if (surgeryMatched || sameUser) {
                templateFrequency.merge(r.getTemplateId(), sameUser ? 3 : 1, Integer::sum);
                similarRecordCount++;
            }
        }

        if (templateFrequency.isEmpty()) {
            for (SurgeryTemplate t : activeTemplates) {
                if (StrUtil.isNotBlank(department) && department.equals(t.getDepartment())) {
                    scores.put(t.getId(), 0.35);
                } else {
                    scores.put(t.getId(), 0.15);
                }
            }
            return scores;
        }

        int maxFreq = templateFrequency.values().stream().max(Integer::compare).orElse(1);
        double docFreqWeight = Math.min(1.0, similarRecordCount / 5.0);
        for (SurgeryTemplate t : activeTemplates) {
            int freq = templateFrequency.getOrDefault(t.getId(), 0);
            double base = freq > 0 ? (0.5 + 0.5 * (freq / (double) maxFreq)) * docFreqWeight : 0.1;
            if (StrUtil.isNotBlank(department) && department.equals(t.getDepartment())) {
                base = Math.min(1.0, base + 0.15);
            }
            scores.put(t.getId(), Math.min(1.0, base));
        }
        return scores;
    }

    private Map<Long, Double> buildContentScores(List<SurgeryTemplate> activeTemplates,
                                                  String surgeryName, String preopDiagnosis,
                                                  String department, String surgeryType) {
        Map<Long, Double> scores = new HashMap<>();
        Set<String> docKeywords = new HashSet<>();
        if (StrUtil.isNotBlank(surgeryName)) docKeywords.addAll(splitWords(surgeryName));
        if (StrUtil.isNotBlank(preopDiagnosis)) docKeywords.addAll(splitWords(preopDiagnosis));
        if (StrUtil.isNotBlank(surgeryType)) docKeywords.addAll(splitWords(surgeryType));
        if (StrUtil.isNotBlank(department)) docKeywords.add(department);

        for (SurgeryTemplate t : activeTemplates) {
            Set<String> tplKeywords = new HashSet<>();
            if (StrUtil.isNotBlank(t.getTemplateName())) tplKeywords.addAll(splitWords(t.getTemplateName()));
            if (StrUtil.isNotBlank(t.getSurgeryType())) tplKeywords.addAll(splitWords(t.getSurgeryType()));
            if (StrUtil.isNotBlank(t.getDescription())) tplKeywords.addAll(splitWords(t.getDescription()));
            if (StrUtil.isNotBlank(t.getTags())) tplKeywords.addAll(splitWords(t.getTags()));
            if (StrUtil.isNotBlank(t.getDepartment())) tplKeywords.add(t.getDepartment());

            double sim = similaritySet(docKeywords, tplKeywords);
            if (StrUtil.isNotBlank(department) && department.equals(t.getDepartment())) {
                sim = Math.min(1.0, sim + 0.1);
            }
            if (StrUtil.isNotBlank(surgeryType) && surgeryType.equals(t.getSurgeryType())) {
                sim = Math.min(1.0, sim + 0.1);
            }
            if (t.getIsDefault() != null && t.getIsDefault() == 1) {
                sim = Math.min(1.0, sim + 0.1);
            }
            scores.put(t.getId(), sim);
        }
        return scores;
    }

    private Map<Long, Double> buildPopularityScores(List<SurgeryTemplate> activeTemplates) {
        Map<Long, Double> scores = new HashMap<>();
        if (activeTemplates.isEmpty()) return scores;
        int maxUse = activeTemplates.stream()
                .mapToInt(t -> t.getUseCount() == null ? 0 : t.getUseCount())
                .max().orElse(1);
        if (maxUse == 0) maxUse = 1;
        for (SurgeryTemplate t : activeTemplates) {
            int use = t.getUseCount() == null ? 0 : t.getUseCount();
            double s = 0.3 + 0.7 * (use / (double) maxUse);
            if (t.getSortOrder() != null && t.getSortOrder() > 0) {
                s = Math.min(1.0, s + 0.05);
            }
            scores.put(t.getId(), s);
        }
        return scores;
    }

    private String inferDocumentType(SurgeryTemplate tpl) {
        String name = tpl.getTemplateName() == null ? "" : tpl.getTemplateName();
        String tags = tpl.getTags() == null ? "" : tpl.getTags();
        String desc = tpl.getDescription() == null ? "" : tpl.getDescription();
        String combined = (name + " " + tags + " " + desc).toLowerCase();
        if (combined.contains("出院") || combined.contains("discharge")) return "出院小结";
        if (combined.contains("医嘱") || combined.contains("order")) return "术后医嘱";
        if (combined.contains("记录") || combined.contains("手术记录") || combined.contains("note")) return "手术记录";
        if (combined.contains("病程") || combined.contains("progress")) return "病程记录";
        if (combined.contains("麻醉") || combined.contains("anesthesia")) return "麻醉记录";
        if (combined.contains("知情") || combined.contains("consent")) return "知情同意书";
        if (combined.contains("护理") || combined.contains("nursing")) return "护理记录";
        if (combined.contains("查房") || combined.contains("round")) return "查房记录";
        if (combined.contains("术前") || combined.contains("preop")) return "术前讨论";
        if (combined.contains("术后") || combined.contains("postop")) return "术后病程";
        return "其他文书";
    }

    private List<String> parseTagList(String tagsJson) {
        if (StrUtil.isBlank(tagsJson)) return Collections.emptyList();
        try {
            if (tagsJson.trim().startsWith("[") || tagsJson.trim().startsWith("{")) {
                return objectMapper.readValue(tagsJson, new TypeReference<List<String>>() {});
            }
            return Arrays.stream(tagsJson.split("[,，;；、/\\s]+"))
                    .filter(StrUtil::isNotBlank).collect(Collectors.toList());
        } catch (Exception e) {
            return Arrays.stream(tagsJson.split("[,，;；、/\\s]+"))
                    .filter(StrUtil::isNotBlank).collect(Collectors.toList());
        }
    }

    private int countPlaceholders(String placeholdersJson) {
        if (StrUtil.isBlank(placeholdersJson)) return 0;
        try {
            List<?> list = objectMapper.readValue(placeholdersJson, List.class);
            return list.size();
        } catch (Exception e) {
            return 0;
        }
    }

    private String buildReason(double collab, double content, double popular, String docType) {
        List<String> parts = new ArrayList<>();
        if (collab >= 0.6) parts.add("与您历史行为匹配度高");
        else if (collab >= 0.35) parts.add("同科室医生常用");
        if (content >= 0.5) parts.add("与当前手术类型高度匹配");
        else if (content >= 0.25) parts.add("符合本病案内容特征");
        if (popular >= 0.75) parts.add("全系统使用量最高");
        if (parts.isEmpty()) parts.add("默认推荐：" + docType);
        return String.join(" · ", parts);
    }

    private Integer inferExpectedDuration(String docType) {
        switch (docType) {
            case "出院小结": return 10;
            case "术后医嘱": return 5;
            case "手术记录": return 20;
            case "病程记录": return 8;
            case "麻醉记录": return 12;
            case "知情同意书": return 6;
            case "护理记录": return 5;
            case "查房记录": return 7;
            case "术前讨论": return 15;
            case "术后病程": return 6;
            default: return 8;
        }
    }

    private Set<String> splitWords(String s) {
        if (StrUtil.isBlank(s)) return Collections.emptySet();
        String cleaned = s.replaceAll("[\\p{Punct}\\s]+", " ").toLowerCase(Locale.ROOT);
        Set<String> result = new HashSet<>();
        for (String token : cleaned.split("\\s+")) {
            if (token.length() >= 1) result.add(token);
        }
        if (s.length() >= 2) {
            for (int i = 0; i < s.length() - 1; i++) {
                String gram = s.substring(i, i + 2).toLowerCase(Locale.ROOT);
                if (!gram.trim().isEmpty()) result.add(gram);
            }
        }
        return result;
    }

    private double similaritySet(Set<String> a, Set<String> b) {
        if (a.isEmpty() || b.isEmpty()) return 0.0;
        Set<String> intersection = new HashSet<>(a);
        intersection.retainAll(b);
        Set<String> union = new HashSet<>(a);
        union.addAll(b);
        return union.isEmpty() ? 0.0 : (double) intersection.size() / union.size();
    }

    private double similarityWords(String a, String b) {
        if (StrUtil.isBlank(a) || StrUtil.isBlank(b)) return 0.0;
        return similaritySet(splitWords(a), splitWords(b));
    }
}
