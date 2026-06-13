package com.surg.extract.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.surg.extract.common.BusinessException;
import com.surg.extract.common.ErrorCode;
import com.surg.extract.dto.TermGraphStatsDTO;
import com.surg.extract.entity.MedicalTerm;
import com.surg.extract.entity.MedicalTermAlias;
import com.surg.extract.graph.MedicalTermGraphRepository;
import com.surg.extract.graph.MedicalTermNode;
import com.surg.extract.mapper.MedicalTermAliasMapper;
import com.surg.extract.mapper.MedicalTermIcdMapper;
import com.surg.extract.mapper.MedicalTermMapper;
import com.surg.extract.mapper.MedicalTermMappingLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MedicalTermGraphService {

    private final MedicalTermGraphRepository graphRepository;
    private final MedicalTermMapper termMapper;
    private final MedicalTermAliasMapper aliasMapper;
    private final MedicalTermIcdMapper icdMapper;
    private final MedicalTermMappingLogMapper mappingLogMapper;

    @Transactional(rollbackFor = Exception.class)
    public void syncTermToGraph(MedicalTerm term) {
        try {
            String nodeId = "term_" + term.getId();
            Optional<MedicalTermNode> existingOpt = graphRepository.findById(nodeId);

            MedicalTermNode node;
            if (existingOpt.isPresent()) {
                node = existingOpt.get();
            } else {
                node = new MedicalTermNode();
                node.setId(nodeId);
            }

            node.setTermId(term.getId());
            node.setTermCode(term.getTermCode());
            node.setName(term.getStandardName());
            node.setStandardName(term.getStandardName());
            node.setTermType(term.getTermType());
            node.setPinyin(term.getPinyin());
            node.setPinyinAbbr(term.getPinyinAbbr());
            node.setIcdCode(term.getIcdCode());
            node.setIcdName(term.getIcdName());
            node.setIsStandard(true);
            node.setEnabled(term.getEnabled() != null && term.getEnabled() == 1);

            graphRepository.save(node);
            log.debug("同步术语到图谱: termId={}, name={}", term.getId(), term.getStandardName());
        } catch (Exception e) {
            log.error("同步术语到图谱失败: termId={}, error={}", term.getId(), e.getMessage());
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "同步术语到图谱失败");
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void syncAliasToGraph(MedicalTerm term, MedicalTermAlias alias) {
        try {
            String termNodeId = "term_" + term.getId();
            String aliasNodeId = "alias_" + alias.getId();

            Optional<MedicalTermNode> termNodeOpt = graphRepository.findById(termNodeId);
            if (termNodeOpt.isEmpty()) {
                syncTermToGraph(term);
                termNodeOpt = graphRepository.findById(termNodeId);
            }

            MedicalTermNode aliasNode;
            Optional<MedicalTermNode> existingOpt = graphRepository.findById(aliasNodeId);
            if (existingOpt.isPresent()) {
                aliasNode = existingOpt.get();
            } else {
                aliasNode = new MedicalTermNode();
                aliasNode.setId(aliasNodeId);
            }

            aliasNode.setTermId(term.getId());
            aliasNode.setTermCode(term.getTermCode());
            aliasNode.setName(alias.getAliasName());
            aliasNode.setStandardName(term.getStandardName());
            aliasNode.setTermType(term.getTermType());
            aliasNode.setPinyin(alias.getPinyin());
            aliasNode.setPinyinAbbr(alias.getPinyinAbbr());
            aliasNode.setIcdCode(term.getIcdCode());
            aliasNode.setIcdName(term.getIcdName());
            aliasNode.setIsStandard(false);
            aliasNode.setEnabled(alias.getEnabled() != null && alias.getEnabled() == 1);

            double confidence = alias.getSimilarityScore() != null
                    ? alias.getSimilarityScore().doubleValue()
                    : 0.8;
            aliasNode.addSynonym(termNodeOpt.get(), confidence, alias.getSource());

            graphRepository.save(aliasNode);
            log.debug("同步别名到图谱: aliasId={}, name={}", alias.getId(), alias.getAliasName());
        } catch (Exception e) {
            log.error("同步别名到图谱失败: aliasId={}, error={}", alias.getId(), e.getMessage());
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "同步别名到图谱失败");
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void removeTermFromGraph(Long termId) {
        try {
            String nodeId = "term_" + termId;
            graphRepository.deleteById(nodeId);

            List<MedicalTermAlias> aliases = aliasMapper.selectByTermId(termId);
            for (MedicalTermAlias alias : aliases) {
                String aliasNodeId = "alias_" + alias.getId();
                graphRepository.deleteById(aliasNodeId);
            }

            log.info("从图谱删除术语及别名: termId={}", termId);
        } catch (Exception e) {
            log.error("从图谱删除术语失败: termId={}, error={}", termId, e.getMessage());
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "从图谱删除术语失败");
        }
    }

    public List<Map<String, Object>> searchInGraph(String keyword) {
        try {
            if (StrUtil.isBlank(keyword)) {
                return Collections.emptyList();
            }
            List<MedicalTermNode> nodes = graphRepository.searchByKeyword(keyword.trim().toLowerCase());
            return nodes.stream().map(this::nodeToMap).collect(Collectors.toList());
        } catch (Exception e) {
            log.error("图谱搜索失败: keyword={}, error={}", keyword, e.getMessage());
            return Collections.emptyList();
        }
    }

    public List<Map<String, Object>> findSynonymsInGraph(String name, Integer maxHops) {
        try {
            if (StrUtil.isBlank(name)) {
                return Collections.emptyList();
            }
            List<MedicalTermNode> synonyms = graphRepository.findSynonymsByName(name);
            return synonyms.stream()
                    .filter(n -> n.getEnabled() != null && n.getEnabled())
                    .map(this::nodeToMap)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("图谱查找同义词失败: name={}, error={}", name, e.getMessage());
            return Collections.emptyList();
        }
    }

    public List<MedicalTermNode> findSynonymsInGraph(String name) {
        try {
            if (StrUtil.isBlank(name)) {
                return Collections.emptyList();
            }
            List<MedicalTermNode> synonyms = graphRepository.findSynonymsByName(name);
            return synonyms.stream()
                    .filter(n -> n.getEnabled() != null && n.getEnabled())
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("图谱查找同义词失败: name={}, error={}", name, e.getMessage());
            return Collections.emptyList();
        }
    }

    private Map<String, Object> nodeToMap(MedicalTermNode node) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", node.getId());
        map.put("termId", node.getTermId());
        map.put("termCode", node.getTermCode());
        map.put("name", node.getName());
        map.put("standardName", node.getStandardName());
        map.put("termType", node.getTermType());
        map.put("pinyin", node.getPinyin());
        map.put("pinyinAbbr", node.getPinyinAbbr());
        map.put("icdCode", node.getIcdCode());
        map.put("icdName", node.getIcdName());
        map.put("isStandard", node.getIsStandard());
        map.put("enabled", node.getEnabled());
        return map;
    }

    public List<Map<String, Object>> findPathInGraph(String startName, String endName) {
        try {
            if (StrUtil.isBlank(startName) || StrUtil.isBlank(endName)) {
                return Collections.emptyList();
            }
            List<Object> paths = graphRepository.findShortestPath(startName, endName);
            List<Map<String, Object>> result = new ArrayList<>();
            for (Object path : paths) {
                Map<String, Object> pathMap = new HashMap<>();
                pathMap.put("path", path.toString());
                result.add(pathMap);
            }
            return result;
        } catch (Exception e) {
            log.error("图谱查找路径失败: startName={}, endName={}, error={}", startName, endName, e.getMessage());
            return Collections.emptyList();
        }
    }

    public TermGraphStatsDTO getGraphStats() {
        try {
            TermGraphStatsDTO stats = new TermGraphStatsDTO();

            long standardTerms = graphRepository.countStandardTerms();
            stats.setTotalStandardTerms(standardTerms);

            long totalNodes = graphRepository.count();
            stats.setTotalGraphNodes(totalNodes);
            stats.setTotalAliasTerms(totalNodes - standardTerms);

            long synonymRels = graphRepository.countSynonymRelationships();
            stats.setTotalSynonymRelationships(synonymRels);

            long icdCount = icdMapper.selectCount(
                    new LambdaQueryWrapper<com.surg.extract.entity.MedicalTermIcd>()
                            .eq(com.surg.extract.entity.MedicalTermIcd::getEnabled, 1));
            stats.setTotalIcdCodes(icdCount);

            List<Map<String, Object>> mappingStats = mappingLogMapper.getMappingStats(30);
            long totalMapping = 0;
            long successMapping = 0;
            for (Map<String, Object> stat : mappingStats) {
                Long count = ((Number) stat.get("count")).longValue();
                totalMapping += count;
            }
            stats.setTotalMappingCount(totalMapping);

            LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
            long todayCount = mappingLogMapper.selectCount(
                    new LambdaQueryWrapper<com.surg.extract.entity.MedicalTermMappingLog>()
                            .ge(com.surg.extract.entity.MedicalTermMappingLog::getMappingTime, startOfDay));
            stats.setTodayMappingCount(todayCount);

            if (totalMapping > 0) {
                long successCount = mappingLogMapper.selectCount(
                        new LambdaQueryWrapper<com.surg.extract.entity.MedicalTermMappingLog>()
                                .eq(com.surg.extract.entity.MedicalTermMappingLog::getMappingSuccess, 1));
                stats.setMappingSuccessRate((double) successCount / totalMapping);
            } else {
                stats.setMappingSuccessRate(0.0);
            }

            return stats;
        } catch (Exception e) {
            log.error("获取图谱统计失败: {}", e.getMessage());
            TermGraphStatsDTO stats = new TermGraphStatsDTO();
            stats.setTotalStandardTerms(0);
            stats.setTotalAliasTerms(0);
            stats.setTotalGraphNodes(0);
            stats.setTotalSynonymRelationships(0);
            stats.setTotalMappingCount(0);
            stats.setTodayMappingCount(0);
            stats.setMappingSuccessRate(0.0);
            stats.setTotalIcdCodes(0);
            return stats;
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void syncAllToGraph() {
        log.info("开始全量同步数据到图谱...");
        long startTime = System.currentTimeMillis();

        try {
            clearGraph();

            List<MedicalTerm> terms = termMapper.selectList(
                    new LambdaQueryWrapper<MedicalTerm>()
                            .eq(MedicalTerm::getDeleted, 0)
                            .eq(MedicalTerm::getEnabled, 1));

            int termCount = 0;
            int aliasCount = 0;

            for (MedicalTerm term : terms) {
                syncTermToGraph(term);
                termCount++;

                List<MedicalTermAlias> aliases = aliasMapper.selectByTermId(term.getId());
                for (MedicalTermAlias alias : aliases) {
                    if (alias.getEnabled() != null && alias.getEnabled() == 1) {
                        syncAliasToGraph(term, alias);
                        aliasCount++;
                    }
                }

                if (termCount % 100 == 0) {
                    log.info("同步进度: terms={}, aliases={}", termCount, aliasCount);
                }
            }

            long cost = System.currentTimeMillis() - startTime;
            log.info("全量同步完成: terms={}, aliases={}, cost={}ms", termCount, aliasCount, cost);
        } catch (Exception e) {
            log.error("全量同步到图谱失败: {}", e.getMessage());
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "全量同步到图谱失败");
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void clearGraph() {
        try {
            graphRepository.deleteAllNodes();
            log.info("图谱数据已清空");
        } catch (Exception e) {
            log.error("清空图谱失败: {}", e.getMessage());
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "清空图谱失败");
        }
    }

    public List<MedicalTermNode> findExactMatchesInGraph(String text, String pinyin, String pinyinAbbr) {
        try {
            return graphRepository.findExactMatches(text, pinyin, pinyinAbbr);
        } catch (Exception e) {
            log.error("图谱精确匹配失败: text={}, error={}", text, e.getMessage());
            return Collections.emptyList();
        }
    }

    public List<MedicalTermNode> findSynonymsByTermIdInGraph(Long termId, Integer limit) {
        try {
            return graphRepository.findSynonymsByTermId(termId, limit != null ? limit : 10);
        } catch (Exception e) {
            log.error("图谱按术语ID查找同义词失败: termId={}, error={}", termId, e.getMessage());
            return Collections.emptyList();
        }
    }
}
