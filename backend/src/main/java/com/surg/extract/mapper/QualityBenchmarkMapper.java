package com.surg.extract.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.surg.extract.entity.QualityBenchmark;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface QualityBenchmarkMapper extends BaseMapper<QualityBenchmark> {

    List<QualityBenchmark> selectByConditions(
            @Param("indicatorCategory") String indicatorCategory,
            @Param("department") String department,
            @Param("enabled") Integer enabled,
            @Param("benchmarkYear") Integer benchmarkYear,
            @Param("region") String region
    );

    IPage<QualityBenchmark> selectPageByConditions(
            Page<QualityBenchmark> page,
            @Param("indicatorCategory") String indicatorCategory,
            @Param("department") String department,
            @Param("enabled") Integer enabled,
            @Param("benchmarkYear") Integer benchmarkYear,
            @Param("region") String region
    );

    QualityBenchmark selectByCodeAndDept(
            @Param("indicatorCode") String indicatorCode,
            @Param("department") String department,
            @Param("benchmarkYear") Integer benchmarkYear
    );
}
