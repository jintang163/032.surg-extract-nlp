package com.surg.extract.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.surg.extract.entity.ModelTrainLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface ModelTrainLogMapper extends BaseMapper<ModelTrainLog> {

    ModelTrainLog selectLatestSuccess();

    List<ModelTrainLog> selectByDateRange(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    List<ModelTrainLog> selectLatestN(@Param("limit") Integer limit);
}
