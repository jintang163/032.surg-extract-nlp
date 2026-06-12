package com.surg.extract.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.surg.extract.entity.SurgeryTemplate;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface SurgeryTemplateMapper extends BaseMapper<SurgeryTemplate> {

    List<SurgeryTemplate> selectByConditions(
            @Param("templateName") String templateName,
            @Param("surgeryType") String surgeryType,
            @Param("department") String department,
            @Param("status") String status,
            @Param("offset") Integer offset,
            @Param("limit") Integer limit
    );

    Long countByConditions(
            @Param("templateName") String templateName,
            @Param("surgeryType") String surgeryType,
            @Param("department") String department,
            @Param("status") String status
    );

    Integer incrementUseCount(@Param("id") Long id);

    List<SurgeryTemplate> selectAvailableTemplates(
            @Param("surgeryType") String surgeryType,
            @Param("department") String department
    );
}
