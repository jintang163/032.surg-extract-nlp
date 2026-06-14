package com.surg.extract.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.surg.extract.entity.ExportTemplate;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface ExportTemplateMapper extends BaseMapper<ExportTemplate> {

    List<ExportTemplate> selectByConditions(
            @Param("exportFormat") String exportFormat,
            @Param("department") String department,
            @Param("enabled") Integer enabled,
            @Param("offset") int offset,
            @Param("limit") int limit
    );

    long countByConditions(
            @Param("exportFormat") String exportFormat,
            @Param("department") String department,
            @Param("enabled") Integer enabled
    );

    @Select("SELECT * FROM export_template WHERE template_code = #{code} AND deleted = 0 LIMIT 1")
    ExportTemplate selectByCode(@Param("code") String code);

    @Select("SELECT * FROM export_template WHERE is_default = 1 AND export_format = #{format} AND deleted = 0 LIMIT 1")
    ExportTemplate selectDefaultByFormat(@Param("format") String format);

    @Update("UPDATE export_template SET is_default = 0 WHERE export_format = #{format} AND is_default = 1 AND deleted = 0")
    int clearDefaultForFormat(@Param("format") String format);
}
