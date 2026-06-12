package com.surg.extract.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.surg.extract.entity.SurgeryTemplateVersion;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface SurgeryTemplateVersionMapper extends BaseMapper<SurgeryTemplateVersion> {

    List<SurgeryTemplateVersion> selectByTemplateId(@Param("templateId") Long templateId);

    Integer clearCurrentVersion(@Param("templateId") Long templateId);

    SurgeryTemplateVersion selectLatestVersion(@Param("templateId") Long templateId);
}
