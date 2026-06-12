package com.surg.extract.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.surg.extract.entity.MedicalRecordHomeExt;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface MedicalRecordHomeExtMapper extends BaseMapper<MedicalRecordHomeExt> {

    List<MedicalRecordHomeExt> selectByRecordId(@Param("recordId") Long recordId);

    MedicalRecordHomeExt selectByRecordIdAndFieldId(@Param("recordId") Long recordId, @Param("fieldId") Long fieldId);

    int deleteByRecordId(@Param("recordId") Long recordId);
}
