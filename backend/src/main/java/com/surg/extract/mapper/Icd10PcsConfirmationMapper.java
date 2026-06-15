package com.surg.extract.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.surg.extract.entity.Icd10PcsConfirmation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface Icd10PcsConfirmationMapper extends BaseMapper<Icd10PcsConfirmation> {

    List<Icd10PcsConfirmation> selectByRecordId(@Param("recordId") Long recordId);

    Icd10PcsConfirmation selectLatestByRecordId(@Param("recordId") Long recordId);
}
