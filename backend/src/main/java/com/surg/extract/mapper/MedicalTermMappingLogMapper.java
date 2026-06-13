package com.surg.extract.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.surg.extract.entity.MedicalTermMappingLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface MedicalTermMappingLogMapper extends BaseMapper<MedicalTermMappingLog> {

    @Select("SELECT * FROM medical_term_mapping_log WHERE record_id = #{recordId} ORDER BY mapping_time DESC")
    List<MedicalTermMappingLog> selectByRecordId(@Param("recordId") Long recordId);

    @Select("SELECT * FROM medical_term_mapping_log WHERE source_text = #{sourceText} AND mapping_success = 1 ORDER BY mapping_time DESC LIMIT 1")
    MedicalTermMappingLog selectLatestSuccessBySourceText(@Param("sourceText") String sourceText);

    @Select("SELECT match_method, COUNT(*) as count, AVG(cost_ms) as avg_cost " +
            "FROM medical_term_mapping_log " +
            "WHERE mapping_time >= DATE_SUB(NOW(), INTERVAL #{days} DAY) " +
            "GROUP BY match_method")
    List<Map<String, Object>> getMappingStats(@Param("days") Integer days);

    @Select("SELECT * FROM medical_term_mapping_log ORDER BY mapping_time DESC")
    Page<MedicalTermMappingLog> selectPage(Page<MedicalTermMappingLog> page);
}
