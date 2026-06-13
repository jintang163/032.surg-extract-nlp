package com.surg.extract.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.houbb.pinyin.constant.enums.PinyinStyleEnum;
import com.github.houbb.pinyin.util.PinyinHelper;
import com.surg.extract.common.BusinessException;
import com.surg.extract.common.ErrorCode;
import com.surg.extract.dto.*;
import com.surg.extract.entity.MedicalTerm;
import com.surg.extract.entity.MedicalTermAlias;
import com.surg.extract.entity.MedicalTermMappingLog;
import com.surg.extract.graph.MedicalTermNode;
import com.surg.extract.mapper.MedicalTermAliasMapper;
import com.surg.extract.mapper.MedicalTermMapper;
import com.surg.extract.mapper.MedicalTermMappingLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.text.similarity.LevenshteinDistance;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TermMappingService {

    private final MedicalTermMapper termMapper;
    private final MedicalTermAliasMapper aliasMapper;
    private final MedicalTermGraphService graphService;
    private final MedicalTermMappingLogMapper mappingLogMapper;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    private static final String MAPPING_CACHE_PREFIX = "term:mapping:";
    private static final long MAPPING_CACHE_EXPIRE = 30;
    private static final double DEFAULT_MIN_SIMILARITY = 0.7;

    @Transactional(rollbackFor = Exception.class)
    public TermMappingResultDTO mapTerm(TermMappingRequestDTO request) {
        long startTime = System.currentTimeMillis();
        String text = request.getText().trim();
        Double minSimilarity = request.getMinSimilarity() != null ? request.getMinSimilarity() : DEFAULT_MIN_SIMILARITY;
        Integer maxResults = request.getMaxResults() != null ? request.getMaxResults() : 5;

        TermMappingResultDTO result = new TermMappingResultDTO();
        result.setSourceText(text);
        result.setMappingTime(LocalDateTime.now());
        result.setMappingSuccess(false);
        result.setCandidates(new ArrayList<>());

        try {
            String pinyin = PinyinHelper.toPinyin(text, PinyinStyleEnum.DEFAULT, "");
            String pinyinAbbr = PinyinHelper.toPinyin(text, PinyinStyleEnum.FIRST_LETTER, "");

            String cacheKey = MAPPING_CACHE_PREFIX + text;
            String cachedResult = redisTemplate.opsForValue().get(cacheKey);
            if (StrUtil.isNotBlank(cachedResult)) {
                try {
                    TermMappingResultDTO cached = objectMapper.readValue(cachedResult, TermMappingResultDTO.class);
                    if (cached.getMappingSuccess()) {
                        log.debug("命中Redis缓存: text={}", text);
                        incrementCounts(cached.getStandardTermId(), null);
                        saveMappingLog(request, cached, (int) (System.currentTimeMillis() - startTime));
                        return cached;
                    }
                } catch (Exception e) {
                    log.warn("解析缓存结果失败: {}", e.getMessage());
                }
            }

            TermMappingCandidateDTO exactMatch = findExactMatch(text, pinyin, pinyinAbbr, request.getTermType());
            if (exactMatch != null) {
                result.setStandardTermId(exactMatch.getTermId());
                result.setStandardTermName(exactMatch.getTermName());
                result.setStandardTermCode(exactMatch.getTermCode());
                result.setIcdCode(exactMatch.getIcdCode());
                result.setIcdName(exactMatch.getIcdName());
                result.setMatchMethod("EXACT");
                result.setSimilarityScore(exactMatch.getSimilarityScore());
                result.setMappingSuccess(true);
                result.getCandidates().add(exactMatch);
                log.info("精确匹配成功: text={} -> termId={}", text, exactMatch.getTermId());
                finalizeResult(result, request, startTime, cacheKey);
                return result;
            }

            if (Boolean.TRUE.equals(request.getUseGraph())) {
                List<TermMappingCandidateDTO> graphCandidates = findGraphMatches(text, pinyin, pinyinAbbr, request.getTermType(), minSimilarity);
                if (!graphCandidates.isEmpty()) {
                    TermMappingCandidateDTO best = graphCandidates.get(0);
                    result.setStandardTermId(best.getTermId());
                    result.setStandardTermName(best.getTermName());
                    result.setStandardTermCode(best.getTermCode());
                    result.setIcdCode(best.getIcdCode());
                    result.setIcdName(best.getIcdName());
                    result.setMatchMethod("GRAPH");
                    result.setSimilarityScore(best.getSimilarityScore());
                    result.setMappingSuccess(true);
                    result.setCandidates(graphCandidates.stream().limit(maxResults).collect(Collectors.toList()));
                    result.setMatchPath(Collections.singletonList(Collections.singletonMap("reason", "图谱推理匹配")));
                    log.info("图谱匹配成功: text={} -> termId={}", text, best.getTermId());
                    finalizeResult(result, request, startTime, cacheKey);
                    return result;
                }
            }

            if (Boolean.TRUE.equals(request.getUseFuzzy())) {
                List<TermMappingCandidateDTO> fuzzyCandidates = findFuzzyMatches(text, request.getTermType(), minSimilarity, maxResults);
                if (!fuzzyCandidates.isEmpty()) {
                    TermMappingCandidateDTO best = fuzzyCandidates.get(0);
                    result.setStandardTermId(best.getTermId());
                    result.setStandardTermName(best.getTermName());
                    result.setStandardTermCode(best.getTermCode());
                    result.setIcdCode(best.getIcdCode());
                    result.setIcdName(best.getIcdName());
                    result.setMatchMethod("FUZZY");
                    result.setSimilarityScore(best.getSimilarityScore());
                    result.setMappingSuccess(true);
                    result.setCandidates(fuzzyCandidates);
                    log.info("模糊匹配成功: text={} -> termId={}, similarity={}", text, best.getTermId(), best.getSimilarityScore());
                    finalizeResult(result, request, startTime, cacheKey);
                    return result;
                }
            }

            if (Boolean.TRUE.equals(request.getUsePinyin())) {
                List<TermMappingCandidateDTO> pinyinCandidates = findPinyinMatches(text, pinyin, pinyinAbbr, request.getTermType(), minSimilarity, maxResults);
                if (!pinyinCandidates.isEmpty()) {
                    TermMappingCandidateDTO best = pinyinCandidates.get(0);
                    result.setStandardTermId(best.getTermId());
                    result.setStandardTermName(best.getTermName());
                    result.setStandardTermCode(best.getTermCode());
                    result.setIcdCode(best.getIcdCode());
                    result.setIcdName(best.getIcdName());
                    result.setMatchMethod("PINYIN");
                    result.setSimilarityScore(best.getSimilarityScore());
                    result.setMappingSuccess(true);
                    result.setCandidates(pinyinCandidates);
                    log.info("拼音匹配成功: text={} -> termId={}", text, best.getTermId());
                    finalizeResult(result, request, startTime, cacheKey);
                    return result;
                }
            }

            result.setFailReason("未找到匹配的术语");
            log.info("术语映射失败: text={}", text);
            finalizeResult(result, request, startTime, cacheKey);
            return result;

        } catch (Exception e) {
            log.error("术语映射异常: text={}, error={}", text, e.getMessage(), e);
            result.setMappingSuccess(false);
            result.setFailReason("映射异常: " + e.getMessage());
            finalizeResult(result, request, startTime, null);
            return result;
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public List<TermMappingResultDTO> batchMapTerms(List<TermMappingRequestDTO> requests) {
        List<TermMappingResultDTO> results = new ArrayList<>();
        for (TermMappingRequestDTO request : requests) {
            try {
                TermMappingResultDTO result = mapTerm(request);
                results.add(result);
            } catch (Exception e) {
                log.error("批量映射单条失败: text={}, error={}", request.getText(), e.getMessage());
                TermMappingResultDTO errorResult = new TermMappingResultDTO();
                errorResult.setSourceText(request.getText());
                errorResult.setMappingSuccess(false);
                errorResult.setFailReason(e.getMessage());
                errorResult.setMappingTime(LocalDateTime.now());
                results.add(errorResult);
            }
        }
        log.info("批量术语映射完成: total={}, success={}", requests.size(),
                results.stream().filter(TermMappingResultDTO::getMappingSuccess).count());
        return results;
    }

    public PageResult<MedicalTermMappingLog> getMappingLog(Integer pageNum, Integer pageSize, Long recordId) {
        Page<MedicalTermMappingLog> page = new Page<>(pageNum, pageSize);
        Page<MedicalTermMappingLog> result;

        if (recordId != null) {
            result = mappingLogMapper.selectPage(page,
                    new LambdaQueryWrapper<MedicalTermMappingLog>()
                            .eq(MedicalTermMappingLog::getRecordId, recordId)
                            .orderByDesc(MedicalTermMappingLog::getMappingTime));
        } else {
            result = mappingLogMapper.selectPage(page);
        }

        return PageResult.of(result.getRecords(), result.getTotal(), pageNum, pageSize);
    }

    public List<Map<String, Object>> getMappingStats(Integer days) {
        try {
            return mappingLogMapper.getMappingStats(days != null ? days : 30);
        } catch (Exception e) {
            log.error("获取映射统计失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private TermMappingCandidateDTO findExactMatch(String text, String pinyin, String pinyinAbbr, String termType) {
        try {
            MedicalTerm term = termMapper.selectByStandardName(text);
            if (term != null && term.getEnabled() == 1) {
                if (StrUtil.isBlank(termType) || termType.equals(term.getTermType())) {
                    return buildCandidate(term, "EXACT", BigDecimal.valueOf(1.0), text);
                }
            }

            List<MedicalTermAlias> aliases = aliasMapper.findExactMatches(text, pinyin, pinyinAbbr);
            for (MedicalTermAlias alias : aliases) {
                MedicalTerm aliasTerm = termMapper.selectById(alias.getTermId());
                if (aliasTerm != null && aliasTerm.getEnabled() == 1) {
                    if (StrUtil.isBlank(termType) || termType.equals(aliasTerm.getTermType())) {
                        return buildCandidate(aliasTerm, "EXACT", BigDecimal.valueOf(1.0), alias.getAliasName());
                    }
                }
            }

            List<MedicalTermNode> graphNodes = graphService.findExactMatchesInGraph(text, pinyin, pinyinAbbr);
            for (MedicalTermNode node : graphNodes) {
                if (node.getEnabled() != null && node.getEnabled()) {
                    if (StrUtil.isBlank(termType) || termType.equals(node.getTermType())) {
                        MedicalTerm graphTerm = termMapper.selectById(node.getTermId());
                        if (graphTerm != null && graphTerm.getEnabled() == 1) {
                            return buildCandidate(graphTerm, "EXACT", BigDecimal.valueOf(1.0), node.getName());
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("精确匹配异常: text={}, error={}", text, e.getMessage());
        }
        return null;
    }

    private List<TermMappingCandidateDTO> findGraphMatches(String text, String pinyin, String pinyinAbbr, String termType, double minSimilarity) {
        List<TermMappingCandidateDTO> candidates = new ArrayList<>();
        try {
            List<MedicalTermNode> synonyms = graphService.findSynonymsInGraph(text);
            Set<Long> addedTermIds = new HashSet<>();

            for (MedicalTermNode node : synonyms) {
                if (node.getEnabled() == null || !node.getEnabled()) {
                    continue;
                }
                if (StrUtil.isNotBlank(termType) && !termType.equals(node.getTermType())) {
                    continue;
                }
                if (addedTermIds.contains(node.getTermId())) {
                    continue;
                }

                MedicalTerm term = termMapper.selectById(node.getTermId());
                if (term != null && term.getEnabled() == 1) {
                    double similarity = calculateSimilarity(text, node.getName());
                    if (similarity >= minSimilarity) {
                        candidates.add(buildCandidate(term, "GRAPH", BigDecimal.valueOf(similarity), node.getName()));
                        addedTermIds.add(node.getTermId());
                    }
                }
            }

            candidates.sort((a, b) -> b.getSimilarityScore().compareTo(a.getSimilarityScore()));
        } catch (Exception e) {
            log.error("图谱匹配异常: text={}, error={}", text, e.getMessage());
        }
        return candidates;
    }

    private List<TermMappingCandidateDTO> findFuzzyMatches(String text, String termType, double minSimilarity, int maxResults) {
        List<TermMappingCandidateDTO> candidates = new ArrayList<>();
        try {
            List<MedicalTerm> allTerms = termMapper.selectList(
                    new LambdaQueryWrapper<MedicalTerm>()
                            .eq(MedicalTerm::getDeleted, 0)
                            .eq(MedicalTerm::getEnabled, 1)
                            .eq(StrUtil.isNotBlank(termType), MedicalTerm::getTermType, termType));

            LevenshteinDistance levenshtein = new LevenshteinDistance();

            for (MedicalTerm term : allTerms) {
                double similarity = calculateLevenshteinSimilarity(text, term.getStandardName(), levenshtein);
                if (similarity >= minSimilarity) {
                    candidates.add(buildCandidate(term, "FUZZY", BigDecimal.valueOf(similarity), term.getStandardName()));
                }

                List<MedicalTermAlias> aliases = aliasMapper.selectByTermId(term.getId());
                for (MedicalTermAlias alias : aliases) {
                    if (alias.getEnabled() == null || alias.getEnabled() == 0) {
                        continue;
                    }
                    double aliasSimilarity = calculateLevenshteinSimilarity(text, alias.getAliasName(), levenshtein);
                    if (aliasSimilarity >= minSimilarity) {
                        boolean exists = candidates.stream()
                                .anyMatch(c -> c.getTermId().equals(term.getId()));
                        if (!exists || aliasSimilarity > similarity) {
                            if (exists) {
                                candidates.removeIf(c -> c.getTermId().equals(term.getId()));
                            }
                            candidates.add(buildCandidate(term, "FUZZY", BigDecimal.valueOf(aliasSimilarity), alias.getAliasName()));
                        }
                    }
                }
            }

            candidates.sort((a, b) -> b.getSimilarityScore().compareTo(a.getSimilarityScore()));
            if (candidates.size() > maxResults) {
                candidates = candidates.subList(0, maxResults);
            }
        } catch (Exception e) {
            log.error("模糊匹配异常: text={}, error={}", text, e.getMessage());
        }
        return candidates;
    }

    private List<TermMappingCandidateDTO> findPinyinMatches(String text, String pinyin, String pinyinAbbr, String termType, double minSimilarity, int maxResults) {
        List<TermMappingCandidateDTO> candidates = new ArrayList<>();
        try {
            List<MedicalTerm> allTerms = termMapper.selectList(
                    new LambdaQueryWrapper<MedicalTerm>()
                            .eq(MedicalTerm::getDeleted, 0)
                            .eq(MedicalTerm::getEnabled, 1)
                            .eq(StrUtil.isNotBlank(termType), MedicalTerm::getTermType, termType));

            LevenshteinDistance levenshtein = new LevenshteinDistance();

            for (MedicalTerm term : allTerms) {
                double pinyinSim = 0;
                String matchedText = term.getStandardName();

                if (StrUtil.isNotBlank(term.getPinyin())) {
                    double sim = calculateLevenshteinSimilarity(pinyin, term.getPinyin(), levenshtein);
                    if (sim > pinyinSim) {
                        pinyinSim = sim;
                    }
                }

                if (StrUtil.isNotBlank(term.getPinyinAbbr())) {
                    double sim = calculateLevenshteinSimilarity(pinyinAbbr, term.getPinyinAbbr(), levenshtein);
                    if (sim > pinyinSim) {
                        pinyinSim = sim;
                    }
                }

                List<MedicalTermAlias> aliases = aliasMapper.selectByTermId(term.getId());
                for (MedicalTermAlias alias : aliases) {
                    if (alias.getEnabled() == null || alias.getEnabled() == 0) {
                        continue;
                    }
                    if (StrUtil.isNotBlank(alias.getPinyin())) {
                        double sim = calculateLevenshteinSimilarity(pinyin, alias.getPinyin(), levenshtein);
                        if (sim > pinyinSim) {
                            pinyinSim = sim;
                            matchedText = alias.getAliasName();
                        }
                    }
                    if (StrUtil.isNotBlank(alias.getPinyinAbbr())) {
                        double sim = calculateLevenshteinSimilarity(pinyinAbbr, alias.getPinyinAbbr(), levenshtein);
                        if (sim > pinyinSim) {
                            pinyinSim = sim;
                            matchedText = alias.getAliasName();
                        }
                    }
                }

                if (pinyinSim >= minSimilarity) {
                    boolean exists = candidates.stream().anyMatch(c -> c.getTermId().equals(term.getId()));
                    if (!exists) {
                        candidates.add(buildCandidate(term, "PINYIN", BigDecimal.valueOf(pinyinSim), matchedText));
                    }
                }
            }

            candidates.sort((a, b) -> b.getSimilarityScore().compareTo(a.getSimilarityScore()));
            if (candidates.size() > maxResults) {
                candidates = candidates.subList(0, maxResults);
            }
        } catch (Exception e) {
            log.error("拼音匹配异常: text={}, error={}", text, e.getMessage());
        }
        return candidates;
    }

    private double calculateSimilarity(String str1, String str2) {
        if (StrUtil.isBlank(str1) || StrUtil.isBlank(str2)) {
            return 0;
        }
        LevenshteinDistance levenshtein = new LevenshteinDistance();
        return calculateLevenshteinSimilarity(str1, str2, levenshtein);
    }

    private double calculateLevenshteinSimilarity(String str1, String str2, LevenshteinDistance levenshtein) {
        if (StrUtil.isBlank(str1) || StrUtil.isBlank(str2)) {
            return 0;
        }
        String s1 = str1.toLowerCase();
        String s2 = str2.toLowerCase();
        int maxLen = Math.max(s1.length(), s2.length());
        if (maxLen == 0) {
            return 1.0;
        }
        int distance = levenshtein.apply(s1, s2);
        return 1.0 - (double) distance / maxLen;
    }

    private TermMappingCandidateDTO buildCandidate(MedicalTerm term, String matchMethod, BigDecimal score, String matchedText) {
        TermMappingCandidateDTO candidate = new TermMappingCandidateDTO();
        candidate.setTermId(term.getId());
        candidate.setTermName(term.getStandardName());
        candidate.setTermCode(term.getTermCode());
        candidate.setIcdCode(term.getIcdCode());
        candidate.setIcdName(term.getIcdName());
        candidate.setMatchMethod(matchMethod);
        candidate.setSimilarityScore(score);
        candidate.setMatchedText(matchedText);
        return candidate;
    }

    private void finalizeResult(TermMappingResultDTO result, TermMappingRequestDTO request, long startTime, String cacheKey) {
        int costMs = (int) (System.currentTimeMillis() - startTime);
        result.setCostMs(costMs);

        if (result.getCandidates() != null) {
            for (int i = 0; i < result.getCandidates().size(); i++) {
                result.getCandidates().get(i).setRank(i + 1);
            }
        }

        saveMappingLog(request, result, costMs);

        if (Boolean.TRUE.equals(result.getMappingSuccess()) && StrUtil.isNotBlank(cacheKey)) {
            try {
                String json = objectMapper.writeValueAsString(result);
                redisTemplate.opsForValue().set(cacheKey, json, MAPPING_CACHE_EXPIRE, TimeUnit.MINUTES);
            } catch (Exception e) {
                log.warn("写入缓存失败: {}", e.getMessage());
            }
        }

        if (Boolean.TRUE.equals(result.getMappingSuccess())) {
            incrementCounts(result.getStandardTermId(), null);
        }
    }

    private void saveMappingLog(TermMappingRequestDTO request, TermMappingResultDTO result, int costMs) {
        try {
            MedicalTermMappingLog log = new MedicalTermMappingLog();
            log.setRecordId(request.getRecordId());
            log.setSourceText(result.getSourceText());
            log.setStandardTermId(result.getStandardTermId());
            log.setStandardTermName(result.getStandardTermName());
            log.setIcdCode(result.getIcdCode());
            log.setMatchMethod(result.getMatchMethod());
            log.setSimilarityScore(result.getSimilarityScore());
            log.setMappingSuccess(Boolean.TRUE.equals(result.getMappingSuccess()) ? 1 : 0);
            log.setFailReason(result.getFailReason());
            log.setMappingTime(result.getMappingTime());
            log.setCostMs(costMs);

            if (result.getMatchPath() != null && !result.getMatchPath().isEmpty()) {
                try {
                    log.setMatchPath(objectMapper.writeValueAsString(result.getMatchPath()));
                } catch (Exception e) {
                    log.setMatchPath(null);
                }
            }

            mappingLogMapper.insert(log);
        } catch (Exception e) {
            log.error("保存映射日志失败: {}", e.getMessage());
        }
    }

    private void incrementCounts(Long termId, Long aliasId) {
        try {
            if (termId != null) {
                termMapper.incrementMatchCount(termId);
                termMapper.incrementUsageCount(termId);
            }
            if (aliasId != null) {
                aliasMapper.incrementMatchCount(aliasId);
                aliasMapper.incrementUsageCount(aliasId);
            }
        } catch (Exception e) {
            log.warn("更新计数失败: termId={}, aliasId={}", termId, aliasId);
        }
    }

    public void evictMappingCache(String text) {
        try {
            String cacheKey = MAPPING_CACHE_PREFIX + text;
            redisTemplate.delete(cacheKey);
            log.info("清除映射缓存: text={}", text);
        } catch (Exception e) {
            log.warn("清除缓存失败: text={}, error={}", text, e.getMessage());
        }
    }
}
