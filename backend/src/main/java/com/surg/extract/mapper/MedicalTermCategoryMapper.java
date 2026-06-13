package com.surg.extract.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.surg.extract.entity.MedicalTermCategory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface MedicalTermCategoryMapper extends BaseMapper<MedicalTermCategory> {

    @Select("SELECT * FROM medical_term_category WHERE parent_id = #{parentId} AND deleted = 0 ORDER BY sort_order ASC")
    List<MedicalTermCategory> selectByParentId(@Param("parentId") Long parentId);

    @Select("SELECT * FROM medical_term_category WHERE deleted = 0 ORDER BY sort_order ASC")
    List<MedicalTermCategory> selectAllEnabled();
}
