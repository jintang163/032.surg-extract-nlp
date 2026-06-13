package com.surg.extract.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.surg.extract.common.BusinessException;
import com.surg.extract.common.ErrorCode;
import com.surg.extract.dto.PageResult;
import com.surg.extract.entity.MedicalTermIcd;
import com.surg.extract.mapper.MedicalTermIcdMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class MedicalTermIcdService {

    private final MedicalTermIcdMapper icdMapper;

    public MedicalTermIcd getIcdById(Long id) {
        MedicalTermIcd icd = icdMapper.selectById(id);
        if (icd == null || icd.getEnabled() == null || icd.getEnabled() == 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "ICD编码不存在");
        }
        return icd;
    }

    public MedicalTermIcd getIcdByCode(String icdCode, String icdVersion) {
        if (StrUtil.isBlank(icdVersion)) {
            icdVersion = "ICD-10";
        }
        MedicalTermIcd icd = icdMapper.selectByCodeAndVersion(icdCode, icdVersion);
        if (icd == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "ICD编码不存在");
        }
        return icd;
    }

    public List<MedicalTermIcd> searchIcd(String keyword, String icdVersion) {
        if (StrUtil.isBlank(keyword)) {
            return icdMapper.selectList(
                    new LambdaQueryWrapper<MedicalTermIcd>()
                            .eq(MedicalTermIcd::getEnabled, 1)
                            .eq(StrUtil.isNotBlank(icdVersion), MedicalTermIcd::getIcdVersion, icdVersion)
                            .orderByAsc(MedicalTermIcd::getIcdCode));
        }

        if (StrUtil.isBlank(icdVersion)) {
            icdVersion = "ICD-10";
        }

        Page<MedicalTermIcd> page = new Page<>(1, 100);
        Page<MedicalTermIcd> result = icdMapper.searchByKeyword(page, keyword, icdVersion);
        return result.getRecords();
    }

    public PageResult<MedicalTermIcd> getIcdPage(Integer pageNum, Integer pageSize, String keyword, String icdVersion) {
        Page<MedicalTermIcd> page = new Page<>(pageNum, pageSize);
        Page<MedicalTermIcd> result;

        if (StrUtil.isBlank(icdVersion)) {
            icdVersion = "ICD-10";
        }

        if (StrUtil.isNotBlank(keyword)) {
            result = icdMapper.searchByKeyword(page, keyword, icdVersion);
        } else {
            result = icdMapper.selectPage(page,
                    new LambdaQueryWrapper<MedicalTermIcd>()
                            .eq(MedicalTermIcd::getEnabled, 1)
                            .eq(MedicalTermIcd::getIcdVersion, icdVersion)
                            .orderByAsc(MedicalTermIcd::getIcdCode));
        }

        return PageResult.of(result.getRecords(), result.getTotal(), pageNum, pageSize);
    }

    public List<MedicalTermIcd> getIcdsByVersion(String icdVersion) {
        if (StrUtil.isBlank(icdVersion)) {
            icdVersion = "ICD-10";
        }
        return icdMapper.selectByVersion(icdVersion);
    }
}
