package com.surg.extract.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.github.houbb.pinyin.constant.enums.PinyinStyleEnum;
import com.github.houbb.pinyin.util.PinyinHelper;
import com.surg.extract.common.BusinessException;
import com.surg.extract.common.ErrorCode;
import com.surg.extract.common.UserContext;
import com.surg.extract.dto.MedicalTermAliasCreateDTO;
import com.surg.extract.dto.MedicalTermAliasUpdateDTO;
import com.surg.extract.dto.PageResult;
import com.surg.extract.dto.TermMergeRequestDTO;
import com.surg.extract.entity.MedicalTerm;
import com.surg.extract.entity.MedicalTermAlias;
import com.surg.extract.mapper.MedicalTermAliasMapper;
import com.surg.extract.mapper.MedicalTermMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class MedicalTermAliasService {

    private final MedicalTermAliasMapper aliasMapper;
    private final MedicalTermMapper termMapper;
    private final MedicalTermGraphService graphService;

    @Transactional(rollbackFor = Exception.class)
    public MedicalTermAlias addAlias(MedicalTermAliasCreateDTO dto) {
        UserContext.checkAdmin();

        MedicalTerm term = termMapper.selectById(dto.getTermId());
        if (term == null || term.getDeleted() == 1) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "标准术语不存在");
        }

        MedicalTermAlias existing = aliasMapper.selectByTermIdAndAliasName(dto.getTermId(), dto.getAliasName());
        if (existing != null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "该别名已存在");
        }

        MedicalTermAlias alias = new MedicalTermAlias();
        BeanUtils.copyProperties(dto, alias);

        String pinyin = PinyinHelper.toPinyin(dto.getAliasName(), PinyinStyleEnum.DEFAULT, "");
        String pinyinAbbr = PinyinHelper.toPinyin(dto.getAliasName(), PinyinStyleEnum.FIRST_LETTER, "");
        alias.setPinyin(pinyin);
        alias.setPinyinAbbr(pinyinAbbr);

        if (alias.getSimilarityScore() == null) {
            alias.setSimilarityScore(BigDecimal.valueOf(0.8));
        }
        if (alias.getUsageCount() == null) {
            alias.setUsageCount(0);
        }
        if (alias.getMatchCount() == null) {
            alias.setMatchCount(0);
        }
        if (alias.getEnabled() == null) {
            alias.setEnabled(1);
        }
        if (StrUtil.isBlank(alias.getSource())) {
            alias.setSource("MANUAL");
        }
        if (StrUtil.isBlank(alias.getReviewStatus())) {
            alias.setReviewStatus("APPROVED");
        }

        alias.setCreatedUserId(1L);
        alias.setCreatedUserName("SYSTEM");

        aliasMapper.insert(alias);

        try {
            graphService.syncAliasToGraph(term, alias);
        } catch (Exception e) {
            log.warn("同步别名到图谱失败: aliasId={}, error={}", alias.getId(), e.getMessage());
        }

        log.info("添加术语别名: termId={}, aliasName={}", dto.getTermId(), dto.getAliasName());
        return alias;
    }

    @Transactional(rollbackFor = Exception.class)
    public MedicalTermAlias updateAlias(MedicalTermAliasUpdateDTO dto) {
        UserContext.checkAdmin();

        MedicalTermAlias alias = aliasMapper.selectById(dto.getId());
        if (alias == null || alias.getDeleted() == 1) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "别名不存在");
        }

        if (dto.getAliasName() != null && !dto.getAliasName().equals(alias.getAliasName())) {
            String pinyin = PinyinHelper.toPinyin(dto.getAliasName(), PinyinStyleEnum.DEFAULT, "");
            String pinyinAbbr = PinyinHelper.toPinyin(dto.getAliasName(), PinyinStyleEnum.FIRST_LETTER, "");
            alias.setPinyin(pinyin);
            alias.setPinyinAbbr(pinyinAbbr);
            alias.setAliasName(dto.getAliasName());
        }

        if (dto.getAliasType() != null) {
            alias.setAliasType(dto.getAliasType());
        }
        if (dto.getSimilarityScore() != null) {
            alias.setSimilarityScore(dto.getSimilarityScore());
        }
        if (dto.getEnabled() != null) {
            alias.setEnabled(dto.getEnabled());
        }

        aliasMapper.updateById(alias);

        MedicalTerm term = termMapper.selectById(alias.getTermId());
        if (term != null) {
            try {
                graphService.syncAliasToGraph(term, alias);
            } catch (Exception e) {
                log.warn("更新别名到图谱失败: aliasId={}, error={}", alias.getId(), e.getMessage());
            }
        }

        log.info("更新术语别名: aliasId={}, aliasName={}", dto.getId(), alias.getAliasName());
        return alias;
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteAlias(Long id) {
        UserContext.checkAdmin();

        MedicalTermAlias alias = aliasMapper.selectById(id);
        if (alias == null || alias.getDeleted() == 1) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "别名不存在");
        }

        alias.setDeleted(1);
        aliasMapper.updateById(alias);

        log.info("删除术语别名: aliasId={}, aliasName={}", id, alias.getAliasName());
    }

    public List<MedicalTermAlias> getAliasesByTermId(Long termId) {
        return aliasMapper.selectByTermId(termId);
    }

    public PageResult<MedicalTermAlias> searchAliases(Integer pageNum, Integer pageSize, String keyword) {
        Page<MedicalTermAlias> page = new Page<>(pageNum, pageSize);
        Page<MedicalTermAlias> result;

        if (StrUtil.isNotBlank(keyword)) {
            result = aliasMapper.searchByKeyword(page, keyword);
        } else {
            result = aliasMapper.selectPage(page,
                    new LambdaQueryWrapper<MedicalTermAlias>()
                            .eq(MedicalTermAlias::getDeleted, 0)
                            .eq(MedicalTermAlias::getEnabled, 1)
                            .orderByDesc(MedicalTermAlias::getCreatedTime));
        }

        return PageResult.of(result.getRecords(), result.getTotal(), pageNum, pageSize);
    }

    @Transactional(rollbackFor = Exception.class)
    public void reviewAlias(Long id, Boolean approved, String reviewRemark) {
        UserContext.checkAdmin();

        MedicalTermAlias alias = aliasMapper.selectById(id);
        if (alias == null || alias.getDeleted() == 1) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "别名不存在");
        }

        alias.setReviewStatus(approved ? "APPROVED" : "REJECTED");
        alias.setReviewedBy(1L);
        alias.setReviewedTime(LocalDateTime.now());

        aliasMapper.updateById(alias);

        log.info("审核术语别名: aliasId={}, approved={}", id, approved);
    }

    @Transactional(rollbackFor = Exception.class)
    public void mergeAliases(TermMergeRequestDTO dto) {
        UserContext.checkAdmin();

        MedicalTerm targetTerm = termMapper.selectById(dto.getTargetTermId());
        if (targetTerm == null || targetTerm.getDeleted() == 1) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "目标术语不存在");
        }

        for (Long sourceTermId : dto.getSourceTermIds()) {
            if (sourceTermId.equals(dto.getTargetTermId())) {
                continue;
            }

            MedicalTerm sourceTerm = termMapper.selectById(sourceTermId);
            if (sourceTerm == null || sourceTerm.getDeleted() == 1) {
                log.warn("源术语不存在，跳过: sourceTermId={}", sourceTermId);
                continue;
            }

            List<MedicalTermAlias> sourceAliases = aliasMapper.selectByTermId(sourceTermId);
            for (MedicalTermAlias alias : sourceAliases) {
                MedicalTermAlias existing = aliasMapper.selectByTermIdAndAliasName(dto.getTargetTermId(), alias.getAliasName());
                if (existing == null) {
                    alias.setTermId(dto.getTargetTermId());
                    aliasMapper.updateById(alias);

                    try {
                        graphService.syncAliasToGraph(targetTerm, alias);
                    } catch (Exception e) {
                        log.warn("同步别名到图谱失败: aliasId={}, error={}", alias.getId(), e.getMessage());
                    }
                }
            }

            MedicalTermAlias newAlias = new MedicalTermAlias();
            newAlias.setTermId(dto.getTargetTermId());
            newAlias.setAliasName(sourceTerm.getStandardName());
            newAlias.setPinyin(sourceTerm.getPinyin());
            newAlias.setPinyinAbbr(sourceTerm.getPinyinAbbr());
            newAlias.setAliasType("MERGED");
            newAlias.setSimilarityScore(BigDecimal.valueOf(0.9));
            newAlias.setSource("MERGE");
            newAlias.setUsageCount(sourceTerm.getUsageCount());
            newAlias.setMatchCount(sourceTerm.getMatchCount());
            newAlias.setReviewStatus("APPROVED");
            newAlias.setEnabled(1);
            newAlias.setCreatedUserId(1L);
            newAlias.setCreatedUserName("SYSTEM");

            MedicalTermAlias checkExisting = aliasMapper.selectByTermIdAndAliasName(dto.getTargetTermId(), sourceTerm.getStandardName());
            if (checkExisting == null) {
                aliasMapper.insert(newAlias);
            }

            sourceTerm.setDeleted(1);
            termMapper.updateById(sourceTerm);

            try {
                graphService.removeTermFromGraph(sourceTermId);
            } catch (Exception e) {
                log.warn("从图谱删除源术语失败: termId={}, error={}", sourceTermId, e.getMessage());
            }

            log.info("合并术语: sourceTermId={} -> targetTermId={}", sourceTermId, dto.getTargetTermId());
        }
    }

    public List<Map<String, String>> getAliasTypes() {
        List<Map<String, String>> types = new ArrayList<>();
        types.add(Map.of("code", "SYNONYM", "name", "同义词"));
        types.add(Map.of("code", "ABBREVIATION", "name", "缩写"));
        types.add(Map.of("code", "MISTAKE", "name", "常见误写"));
        types.add(Map.of("code", "TRANSLATION", "name", "译名"));
        types.add(Map.of("code", "REGIONAL", "name", "地域说法"));
        types.add(Map.of("code", "MERGED", "name", "合并来源"));
        return types;
    }
}
