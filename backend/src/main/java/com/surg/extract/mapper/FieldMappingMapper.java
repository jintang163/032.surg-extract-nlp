package com.surg.extract.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.surg.extract.entity.FieldMapping;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface FieldMappingMapper extends BaseMapper<FieldMapping> {

    List<FieldMapping> selectEnabledMappings(@Param("targetTable") String targetTable);
}
