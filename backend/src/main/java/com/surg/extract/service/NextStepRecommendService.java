package com.surg.extract.service;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.surg.extract.common.BusinessException;
import com.surg.extract.common.ErrorCode;
import com.surg.extract.dto.NextStepRecommendDTO;
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
        String postopDiagnosis = entityMap.getOrDefault("POSTOP_DIAGNOSIS", "");
        String surgeryType = entityMap.getOrDefault("SURGERY_LEVEL", "");
        String department = record.getDepartment() == null ? "" : record.getDepartment();

        Long effectiveUserId = (userId != null) ? userId : record.getUploadUserId();

        Set<Long> generatedTemplateIds = findAlreadyGeneratedTemplateIds(record, entityMap);

        List<SurgeryTemplate> activeTemplates = templateMapper.selectAvailableTemplates(surgeryType, department);
        if (CollectionUtils.isEmpty(activeTemplates)) {
            activeTemplates = templateMapper.selectAvailableTemplates(null, null);
        }
        Set<Long> activeIds = activeTemplates.stream().map(SurgeryTemplate::getId).collect(Collectors.toSet());

        Map<Long, Double> transitionScores = buildTransitionScores(
                effectiveUserId, department, surgeryName, preopDiagnosis, postopDiagnosis,
                recordId, activeIds, generatedTemplateIds);

        Map<Long, Double> contentScores = buildContentScores(
                activeTemplates, surgeryName, preopDiagnosis, postopDiagnosis, department, surgeryType);

        Map<Long, Double> popularityScores = buildPopularityScores(activeTemplates);

        List<NextStepRecommendDTO> results = new ArrayList<>();
        for (SurgeryTemplate tpl : activeTemplates) {
            if (generatedTemplateIds.contains(tpl.getId())) continue;

            double transition = transitionScores.getOrDefault(tpl.getId(), 0.0);
            double content = contentScores.getOrDefault(tpl.getId(), 0.0);
            double popular = popularityScores.getOrDefault(tpl.getId(), 0.0);
            double finalScore = transition * 0.45 + content * 0.35 + popular * 0.20;

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
            dto.setCollaborativeScore(transition);
            dto.setPopularityScore(popular);
            dto.setPlaceholdersCount(countPlaceholders(tpl.getPlaceholders()));
            dto.setRecommendedReason(buildReason(transition, content, popular, dto.getDocumentType(), generatedTemplateIds.size()));
            dto.setExpectedDurationMinutes(inferExpectedDuration(dto.getDocumentType()));
            results.add(dto);
        }

        results.sort((a, b) -> Double.compare(b.getScore(), a.getScore()));

        int rank = 1;
        for (NextStepRecommendDTO dto : results) {
            dto.setRank(rank++);
        }

        log.info("下一步推荐完成: recordId={}, userId={}, 已生成{}份, 候选{}条, Top1={}",
                recordId, effectiveUserId, generatedTemplateIds.size(), results.size(),
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

    private Set<Long> findAlreadyGeneratedTemplateIds(SurgeryRecord currentRecord, Map<String, String> entityMap) {
        Set<Long> generated = new HashSet<>();
        if (currentRecord.getTemplateId() != null) {
            generated.add(currentRecord.getTemplateId());
        }
        if (StrUtil.isNotBlank(currentRecord.getPatientId())) {
            List<SurgeryRecord> samePatientRecords = recordMapper.selectList(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SurgeryRecord>()
                            .eq(SurgeryRecord::getPatientId, currentRecord.getPatientId())
                            .eq(SurgeryRecord::getDeleted, 0)
                            .isNotNull(SurgeryRecord::getTemplateId)
                            .ne(SurgeryRecord::getId, currentRecord.getId())
            );
            for (SurgeryRecord r : samePatientRecords) {
                if (r.getTemplateId() != null) {
                    generated.add(r.getTemplateId());
                }
            }
        }
        return generated;
    }

    private Map<Long, Double> buildTransitionScores(Long userId, String department,
                                                     String surgeryName, String preopDiagnosis,
                                                     String postopDiagnosis,
                                                     Long currentRecordId,
                                                     Set<Long> activeIds,
                                                     Set<Long> generatedTemplateIds) {
        Map<Long, Double> scores = new HashMap<>();
        if (userId == null && StrUtil.isBlank(department)) return scores;

        LocalDateTime fromTime = LocalDateTime.now().minus(6, ChronoUnit.MONTHS);
        List<SurgeryRecord> recentRecords = recordMapper.selectRecentCompletedByDepartment(department, fromTime);
        if (CollectionUtils.isEmpty(recentRecords)) {
            recentRecords = Collections.emptyList();
        }

        Map<Long, List<Long>> doctorSequences = new LinkedHashMap<>();
        Map<Long, String> recordSurgeryName = new HashMap<>();
        Map<Long, String> recordDiagnosis = new HashMap<>();

        for (SurgeryRecord r : recentRecords) {
            if (r.getTemplateId() == null) continue;
            if (!activeIds.contains(r.getTemplateId())) continue;

            Long docId = r.getUploadUserId();
            doctorSequences.computeIfAbsent(docId, k -> new ArrayList<>()).add(r.getId());

            if (!recordSurgeryName.containsKey(r.getId())) {
                List<SurgeryEntity> rEntities = entityMapper.selectByRecordId(r.getId());
                for (SurgeryEntity e : rEntities) {
                    if ("SURGERY_NAME".equals(e.getEntityType()) && StrUtil.isNotBlank(e.getEntityValue())) {
                        recordSurgeryName.put(r.getId(), e.getEntityValue());
                    }
                    if ("PREOP_DIAGNOSIS".equals(e.getEntityType()) && StrUtil.isNotBlank(e.getEntityValue())) {
                        recordDiagnosis.put(r.getId(), e.getEntityValue());
                    }
                    if ("POSTOP_DIAGNOSIS".equals(e.getEntityType()) && StrUtil.isNotBlank(e.getEntityValue())) {
                        recordDiagnosis.merge(r.getId(), " " + e.getEntityValue(), String::concat);
                    }
                }
            }
        }

        Map<String, Map<Long, Integer>> transitionCounts = new HashMap<>();
        Map<String, Integer> stateTotals = new HashMap<>();

        for (Map.Entry<Long, List<Long>> entry : doctorSequences.entrySet()) {
            List<Long> seq = entry.getValue();
            Map<Long, Long> recordToTemplate = new HashMap<>();
            for (SurgeryRecord r : recentRecords) {
                if (r.getTemplateId() != null && seq.contains(r.getId())) {
                    recordToTemplate.put(r.getId(), r.getTemplateId());
                }
            }
            seq.sort((a, b) -> {
                SurgeryRecord ra = recentRecords.stream().filter(r -> r.getId().equals(a)).findFirst().orElse(null);
                SurgeryRecord rb = recentRecords.stream().filter(r -> r.getId().equals(b)).findFirst().orElse(null);
                if (ra == null || rb == null) return 0;
                if (ra.getUploadTime() == null || rb.getUploadTime() == null) return 0;
                return ra.getUploadTime().compareTo(rb.getUploadTime());
            });

            Map<Long, SurgeryRecord> recordMap = recentRecords.stream()
                    .collect(Collectors.toMap(SurgeryRecord::getId, r -> r, (a, b) -> a));

            for (int i = 0; i < seq.size(); i++) {
                Long currentTid = recordToTemplate.get(seq.get(i));
                if (currentTid == null) continue;

                for (int j = i + 1; j < seq.size() && j <= i + 3; j++) {
                    Long nextTid = recordToTemplate.get(seq.get(j));
                    if (nextTid == null || nextTid.equals(currentTid)) continue;

                    SurgeryRecord nextRecord = recordMap.get(seq.get(j));
                    boolean sameDoctor = entry.getKey().equals(userId);
                    boolean surgerySimilar = false;
                    if (StrUtil.isNotBlank(surgeryName) && nextRecord != null) {
                        String histSurgery = recordSurgeryName.get(seq.get(j));
                        if (StrUtil.isNotBlank(histSurgery)) {
                            surgerySimilar = similarityWords(surgeryName, histSurgery) > 0.2;
                        }
                    }
                    boolean diagnosisSimilar = false;
                    if (StrUtil.isNotBlank(preopDiagnosis) && nextRecord != null) {
                        String histDiag = recordDiagnosis.get(seq.get(j));
                        if (StrUtil.isNotBlank(histDiag)) {
                            diagnosisSimilar = similarityWords(preopDiagnosis, histDiag) > 0.2;
                        }
                    }

                    if (sameDoctor || surgerySimilar || diagnosisSimilar) {
                        double weight = sameDoctor ? 3.0 : 1.0;
                        if (surgerySimilar) weight *= 1.5;
                        if (diagnosisSimilar) weight *= 1.2;
                        int distance = j - i;
                        weight /= distance;

                        String stateKey = currentTid.toString();
                        transitionCounts.computeIfAbsent(stateKey, k -> new HashMap<>())
                                .merge(nextTid, (int) Math.round(weight), Integer::sum);
                        stateTotals.merge(stateKey, (int) Math.round(weight), Integer::sum);
                    }
                }
            }
        }

        Set<Long> currentStepIds = generatedTemplateIds.isEmpty()
                ? Collections.singleton(0L)
                : generatedTemplateIds;

        Map<Long, Double> transitionProbs = new HashMap<>();
        for (Long stepId : currentStepIds) {
            String stateKey = stepId.toString();
            Map<Long, Integer> targets = transitionCounts.get(stateKey);
            int total = stateTotals.getOrDefault(stateKey, 0);
            if (targets != null && total > 0) {
                for (Map.Entry<Long, Integer> te : targets.entrySet()) {
                    if (!generatedTemplateIds.contains(te.getKey())) {
                        transitionProbs.merge(te.getKey(), te.getValue() / (double) total, Double::sum);
                    }
                }
            }
        }
        if (!transitionProbs.isEmpty()) {
            double maxProb = transitionProbs.values().stream().max(Double::compare).orElse(1.0);
            if (maxProb == 0) maxProb = 1.0;
            for (Map.Entry<Long, Double> e : transitionProbs.entrySet()) {
                scores.put(e.getKey(), Math.min(1.0, e.getValue() / maxProb));
            }
        }

        if (scores.isEmpty()) {
            for (Long tid : activeIds) {
                if (!generatedTemplateIds.contains(tid)) {
                    if (StrUtil.isNotBlank(department)) {
                        SurgeryTemplate t = templateMapper.selectById(tid);
                        if (t != null && department.equals(t.getDepartment())) {
                            scores.put(tid, 0.35);
                        } else {
                            scores.put(tid, 0.15);
                        }
                    } else {
                        scores.put(tid, 0.2);
                    }
                }
            }
        }

        return scores;
    }

    private Map<Long, Double> buildContentScores(List<SurgeryTemplate> activeTemplates,
                                                  String surgeryName, String preopDiagnosis,
                                                  String postopDiagnosis,
                                                  String department, String surgeryType) {
        Map<Long, Double> scores = new HashMap<>();
        Set<String> docKeywords = new HashSet<>();
        if (StrUtil.isNotBlank(surgeryName)) docKeywords.addAll(splitWords(surgeryName));
        if (StrUtil.isNotBlank(preopDiagnosis)) docKeywords.addAll(splitWords(preopDiagnosis));
        if (StrUtil.isNotBlank(postopDiagnosis)) docKeywords.addAll(splitWords(postopDiagnosis));
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

    private String buildReason(double collab, double content, double popular, String docType, int alreadyGenerated) {
        List<String> parts = new ArrayList<>();
        if (alreadyGenerated > 0) {
            parts.add("基于已生成文书推演后续步骤");
        }
        if (collab >= 0.6) parts.add("医生行为序列转移概率高");
        else if (collab >= 0.35) parts.add("同科室医生常用后续文书");
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
