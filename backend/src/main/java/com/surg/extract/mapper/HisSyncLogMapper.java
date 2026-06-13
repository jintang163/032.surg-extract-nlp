package com.surg.extract.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.surg.extract.entity.HisSyncLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface HisSyncLogMapper extends BaseMapper<HisSyncLog> {

    List<HisSyncLog> selectByRecordId(@Param("recordId") Long recordId);

    HisSyncLog selectLatestByRecordId(@Param("recordId") Long recordId);
}
