package com.surg.extract.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.houbb.pinyin.constant.enums.PinyinStyleEnum;
import com.github.houbb.pinyin.util.PinyinHelper;
import com.surg.extract.common.BusinessException;
import com.surg.extract.common.ErrorCode;
import com.surg.extract.common.UserContext;
import com.surg.extract.dto.*;
import com.surg.extract.entity.MedicalTerm;
import com.surg.extract.entity.MedicalTermAlias;
import com.surg.extract.entity.MedicalTermCategory;
import com.surg.extract.mapper.MedicalTermAliasMapper;
import com.surg.extract.mapper.MedicalTermCategoryMapper;
import com.surg.extract.mapper.MedicalTermMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MedicalTermService {

    private final MedicalTermMapper termMapper;
    private final MedicalTermAliasMapper aliasMapper;
    private final MedicalTermCategoryMapper categoryMapper;
    private final MedicalTermGraphService graphService;
    private final MedicalTermAliasService aliasService;
    private final ObjectMapper objectMapper;

    @Transactional(rollbackFor = Exception.class)
    public MedicalTerm createTerm(MedicalTermCreateDTO dto) {
        UserContext.checkAdmin();

        MedicalTerm existing = termMapper.selectOne(
                new LambdaQueryWrapper<MedicalTerm>()
                        .eq(MedicalTerm::getTermCode, dto.getTermCode())
                        .eq(MedicalTerm::getDeleted, 0));
        if (existing != null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "术语编码已存在");
        }

        MedicalTerm term = new MedicalTerm();
        BeanUtils.copyProperties(dto, term);

        String pinyin = PinyinHelper.toPinyin(dto.getStandardName(), PinyinStyleEnum.DEFAULT, "");
        String pinyinAbbr = PinyinHelper.toPinyin(dto.getStandardName(), PinyinStyleEnum.FIRST_LETTER, "");
        term.setPinyin(pinyin);
        term.setPinyinAbbr(pinyinAbbr);

        if (term.getUsageCount() == null) {
            term.setUsageCount(0);
        }
        if (term.getMatchCount() == null) {
            term.setMatchCount(0);
        }
        if (term.getEnabled() == null) {
            term.setEnabled(1);
        }
        if (StrUtil.isBlank(term.getReviewStatus())) {
            term.setReviewStatus("PENDING");
        }

        term.setCreatedUserId(1L);
        term.setCreatedUserName("SYSTEM");

        termMapper.insert(term);

        try {
            graphService.syncTermToGraph(term);
        } catch (Exception e) {
            log.warn("同步术语到图谱失败: termId={}, error={}", term.getId(), e.getMessage());
        }

        log.info("创建医学术语: termCode={}, standardName={}", dto.getTermCode(), dto.getStandardName());
        return term;
    }

    @Transactional(rollbackFor = Exception.class)
    public MedicalTerm updateTerm(MedicalTermUpdateDTO dto) {
        UserContext.checkAdmin();

        MedicalTerm term = termMapper.selectById(dto.getId());
        if (term == null || term.getDeleted() == 1) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "术语不存在");
        }

        if (dto.getStandardName() != null && !dto.getStandardName().equals(term.getStandardName())) {
            String pinyin = PinyinHelper.toPinyin(dto.getStandardName(), PinyinStyleEnum.DEFAULT, "");
            String pinyinAbbr = PinyinHelper.toPinyin(dto.getStandardName(), PinyinStyleEnum.FIRST_LETTER, "");
            term.setPinyin(pinyin);
            term.setPinyinAbbr(pinyinAbbr);
            term.setStandardName(dto.getStandardName());
        }

        if (dto.getCategoryId() != null) {
            term.setCategoryId(dto.getCategoryId());
        }
        if (dto.getTermType() != null) {
            term.setTermType(dto.getTermType());
        }
        if (dto.getIcdId() != null) {
            term.setIcdId(dto.getIcdId());
        }
        if (dto.getIcdCode() != null) {
            term.setIcdCode(dto.getIcdCode());
        }
        if (dto.getIcdName() != null) {
            term.setIcdName(dto.getIcdName());
        }
        if (dto.getIcdVersion() != null) {
            term.setIcdVersion(dto.getIcdVersion());
        }
        if (dto.getDefinition() != null) {
            term.setDefinition(dto.getDefinition());
        }
        if (dto.getConfidence() != null) {
            term.setConfidence(dto.getConfidence());
        }
        if (dto.getEnabled() != null) {
            term.setEnabled(dto.getEnabled());
        }

        termMapper.updateById(term);

        try {
            graphService.syncTermToGraph(term);
        } catch (Exception e) {
            log.warn("更新术语到图谱失败: termId={}, error={}", term.getId(), e.getMessage());
        }

        log.info("更新医学术语: termId={}, standardName={}", dto.getId(), term.getStandardName());
        return term;
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteTerm(Long id) {
        UserContext.checkAdmin();

        MedicalTerm term = termMapper.selectById(id);
        if (term == null || term.getDeleted() == 1) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "术语不存在");
        }

        term.setDeleted(1);
        termMapper.updateById(term);

        try {
            graphService.removeTermFromGraph(id);
        } catch (Exception e) {
            log.warn("从图谱删除术语失败: termId={}, error={}", id, e.getMessage());
        }

        log.info("删除医学术语: termId={}, standardName={}", id, term.getStandardName());
    }

    public MedicalTerm getTermById(Long id) {
        MedicalTerm term = termMapper.selectById(id);
        if (term == null || term.getDeleted() == 1) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "术语不存在");
        }
        return term;
    }

    public MedicalTerm getTermByCode(String termCode) {
        MedicalTerm term = termMapper.selectByTermCode(termCode);
        if (term == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "术语不存在");
        }
        return term;
    }

    public List<MedicalTerm> searchTerms(String keyword, String termType) {
        if (StrUtil.isBlank(keyword)) {
            return termMapper.selectList(
                    new LambdaQueryWrapper<MedicalTerm>()
                            .eq(MedicalTerm::getDeleted, 0)
                            .eq(MedicalTerm::getEnabled, 1)
                            .orderByDesc(MedicalTerm::getUsageCount));
        }

        Page<MedicalTerm> page = new Page<>(1, 100);
        Page<MedicalTerm> result;
        if (StrUtil.isNotBlank(termType)) {
            result = termMapper.searchByTypeAndKeyword(page, termType, keyword);
        } else {
            result = termMapper.searchByKeyword(page, keyword);
        }
        return result.getRecords();
    }

    public Page<MedicalTerm> getTermPage(Integer pageNum, Integer pageSize, String keyword, String termType, Long categoryId, String reviewStatus) {
        Page<MedicalTerm> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<MedicalTerm> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MedicalTerm::getDeleted, 0);

        if (StrUtil.isNotBlank(keyword)) {
            wrapper.and(w -> w.like(MedicalTerm::getStandardName, keyword)
                    .or().like(MedicalTerm::getPinyin, keyword)
                    .or().like(MedicalTerm::getPinyinAbbr, keyword));
        }
        if (StrUtil.isNotBlank(termType)) {
            wrapper.eq(MedicalTerm::getTermType, termType);
        }
        if (categoryId != null) {
            wrapper.eq(MedicalTerm::getCategoryId, categoryId);
        }
        if (StrUtil.isNotBlank(reviewStatus)) {
            wrapper.eq(MedicalTerm::getReviewStatus, reviewStatus);
        }
        wrapper.orderByDesc(MedicalTerm::getCreatedTime);

        Page<MedicalTerm> result = termMapper.selectPage(page, wrapper);
        return result;
    }

    public List<MedicalTerm> getTermsByCategory(Long categoryId) {
        return termMapper.selectByCategoryId(categoryId);
    }

    public List<MedicalTerm> getTermsByIcdCode(String icdCode) {
        return termMapper.selectByIcdCode(icdCode);
    }

    @Transactional(rollbackFor = Exception.class)
    public void reviewTerm(Long id, Boolean approved, String reviewRemark) {
        UserContext.checkAdmin();

        MedicalTerm term = termMapper.selectById(id);
        if (term == null || term.getDeleted() == 1) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "术语不存在");
        }

        term.setReviewStatus(approved ? "APPROVED" : "REJECTED");
        term.setReviewRemark(reviewRemark);
        term.setReviewedBy(1L);
        term.setReviewedTime(LocalDateTime.now());

        termMapper.updateById(term);

        log.info("审核医学术语: termId={}, approved={}", id, approved);
    }

    @Transactional(rollbackFor = Exception.class)
    public void incrementMatchCount(Long id) {
        try {
            termMapper.incrementMatchCount(id);
        } catch (Exception e) {
            log.warn("增加匹配次数失败: termId={}, error={}", id, e.getMessage());
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void incrementUsageCount(Long id) {
        try {
            termMapper.incrementUsageCount(id);
        } catch (Exception e) {
            log.warn("增加使用次数失败: termId={}, error={}", id, e.getMessage());
        }
    }

    public Map<String, Object> getTermDetail(Long id) {
        MedicalTerm term = getTermById(id);
        List<MedicalTermAlias> aliases = aliasMapper.selectByTermId(id);

        Map<String, Object> result = new LinkedHashMap<>();
        try {
            result = objectMapper.convertValue(term, new com.fasterxml.jackson.core.type.TypeReference<>() {});
            result.put("aliases", aliases);
        } catch (Exception e) {
            log.warn("转换术语详情失败", e);
            result.put("term", term);
            result.put("aliases", aliases);
        }
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public void mergeTerms(TermMergeRequestDTO dto) {
        aliasService.mergeAliases(dto);
        log.info("合并术语完成: targetTermId={}, sourceTermIds={}", dto.getTargetTermId(), dto.getSourceTermIds());
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> batchImport(TermBatchImportDTO dto) {
        UserContext.checkAdmin();

        int successCount = 0;
        int skipCount = 0;
        int failCount = 0;
        List<Map<String, Object>> errors = new ArrayList<>();

        for (TermImportItemDTO item : dto.getItems()) {
            try {
                if (StrUtil.isBlank(item.getStandardName())) {
                    failCount++;
                    errors.add(Map.of("item", item, "error", "标准名称不能为空"));
                    continue;
                }

                String termCode = StrUtil.isNotBlank(item.getTermCode())
                        ? item.getTermCode()
                        : ("ST-IM-" + System.currentTimeMillis() + "-" + successCount);

                if (Boolean.TRUE.equals(dto.getSkipDuplicate())) {
                    MedicalTerm existing = termMapper.selectByStandardName(item.getStandardName());
                    if (existing != null) {
                        skipCount++;
                        continue;
                    }
                    MedicalTerm existingByCode = termMapper.selectByTermCode(termCode);
                    if (existingByCode != null) {
                        skipCount++;
                        continue;
                    }
                }

                MedicalTermCreateDTO createDTO = new MedicalTermCreateDTO();
                createDTO.setTermCode(termCode);
                createDTO.setStandardName(item.getStandardName());
                createDTO.setTermType(StrUtil.isNotBlank(item.getTermType()) ? item.getTermType()
                        : (StrUtil.isNotBlank(dto.getTermType()) ? dto.getTermType() : "SURGERY"));
                createDTO.setCategoryId(dto.getCategoryId());
                createDTO.setIcdCode(item.getIcdCode());
                createDTO.setIcdName(item.getIcdName());
                createDTO.setDefinition(item.getDefinition());
                createDTO.setConfidence(BigDecimal.ONE);
                createDTO.setReviewStatus("APPROVED");
                createDTO.setEnabled(1);

                MedicalTerm term = createTerm(createDTO);

                if (item.getSynonyms() != null && !item.getSynonyms().isEmpty()) {
                    for (String synonym : item.getSynonyms()) {
                        if (StrUtil.isNotBlank(synonym) && !synonym.equals(item.getStandardName())) {
                            try {
                                MedicalTermAliasCreateDTO aliasDTO = new MedicalTermAliasCreateDTO();
                                aliasDTO.setTermId(term.getId());
                                aliasDTO.setAliasName(synonym);
                                aliasDTO.setAliasType("SYNONYM");
                                aliasDTO.setSimilarityScore(BigDecimal.valueOf(0.9));
                                aliasDTO.setSource("IMPORT");
                                aliasDTO.setReviewStatus("APPROVED");
                                aliasDTO.setEnabled(1);
                                aliasService.addAlias(aliasDTO);
                            } catch (Exception e) {
                                log.warn("添加同义词失败: termId={}, synonym={}, error={}", term.getId(), synonym, e.getMessage());
                            }
                        }
                    }
                }

                successCount++;
            } catch (Exception e) {
                failCount++;
                errors.add(Map.of("item", item, "error", e.getMessage()));
                log.error("导入术语失败: item={}, error={}", item, e.getMessage());
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", dto.getItems().size());
        result.put("success", successCount);
        result.put("skip", skipCount);
        result.put("fail", failCount);
        result.put("errors", errors);

        log.info("批量导入术语完成: total={}, success={}, skip={}, fail={}",
                dto.getItems().size(), successCount, skipCount, failCount);
        return result;
    }

    public List<Map<String, String>> getTermTypes() {
        List<Map<String, String>> types = new ArrayList<>();
        types.add(Map.of("code", "SURGERY", "name", "手术名称"));
        types.add(Map.of("code", "DIAGNOSIS", "name", "诊断"));
        types.add(Map.of("code", "ANESTHESIA", "name", "麻醉"));
        types.add(Map.of("code", "INSTRUMENT", "name", "器械"));
        types.add(Map.of("code", "DRUG", "name", "药品"));
        types.add(Map.of("code", "OTHER", "name", "其他"));
        return types;
    }

    @Transactional(rollbackFor = Exception.class)
    public void toggleEnabled(Long id) {
        MedicalTerm term = termMapper.selectById(id);
        if (term == null || term.getDeleted() == 1) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "术语不存在");
        }
        term.setEnabled(term.getEnabled() == 1 ? 0 : 1);
        term.setUpdatedTime(LocalDateTime.now());
        termMapper.updateById(term);

        try {
            graphService.syncTermToGraph(term);
        } catch (Exception e) {
            log.warn("同步图谱失败: termId={}, error={}", id, e.getMessage());
        }

        log.info("切换术语状态: termId={}, enabled={}", id, term.getEnabled());
    }

    public List<Map<String, Object>> getCategoryTree() {
        List<MedicalTermCategory> categories = categoryMapper.selectList(
                new LambdaQueryWrapper<MedicalTermCategory>()
                        .eq(MedicalTermCategory::getDeleted, 0)
                        .orderByAsc(MedicalTermCategory::getSortOrder));
        return buildCategoryTree(categories, null);
    }

    private List<Map<String, Object>> buildCategoryTree(List<MedicalTermCategory> categories, Long parentId) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (MedicalTermCategory category : categories) {
            if ((parentId == null && category.getParentId() == null)
                    || (parentId != null && parentId.equals(category.getParentId()))) {
                Map<String, Object> node = new LinkedHashMap<>();
                node.put("id", category.getId());
                node.put("categoryName", category.getCategoryName());
                node.put("categoryCode", category.getCategoryCode());
                node.put("parentId", category.getParentId());
                node.put("description", category.getDescription());
                node.put("sortOrder", category.getSortOrder());
                List<Map<String, Object>> children = buildCategoryTree(categories, category.getId());
                if (!children.isEmpty()) {
                    node.put("children", children);
                }
                result.add(node);
            }
        }
        return result;
    }
}
