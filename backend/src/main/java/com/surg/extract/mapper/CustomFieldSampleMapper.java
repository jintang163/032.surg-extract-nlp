package com.surg.extract.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.surg.extract.entity.CustomFieldSample;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface CustomFieldSampleMapper extends BaseMapper<CustomFieldSample> {

    List<CustomFieldSample> selectByFieldId(@Param("fieldId") Long fieldId);

    int countByFieldId(@Param("fieldId") Long fieldId);
}
