package com.surg.extract.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.surg.extract.entity.SurgeryEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface SurgeryEntityMapper extends BaseMapper<SurgeryEntity> {

    List<SurgeryEntity> selectByRecordId(@Param("recordId") Long recordId);

    Integer batchInsert(@Param("list") List<SurgeryEntity> list);

    Integer deleteByRecordId(@Param("recordId") Long recordId);
}
