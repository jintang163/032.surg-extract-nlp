package com.surg.extract.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.surg.extract.entity.MedicalTerm;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface MedicalTermMapper extends BaseMapper<MedicalTerm> {

    @Select("SELECT * FROM medical_term WHERE term_code = #{termCode} AND deleted = 0")
    MedicalTerm selectByTermCode(@Param("termCode") String termCode);

    @Select("SELECT * FROM medical_term WHERE standard_name = #{standardName} AND deleted = 0 LIMIT 1")
    MedicalTerm selectByStandardName(@Param("standardName") String standardName);

    @Select("SELECT * FROM medical_term WHERE deleted = 0 AND enabled = 1 " +
            "AND (standard_name LIKE CONCAT('%', #{keyword}, '%') OR pinyin_abbr LIKE CONCAT('%', #{keyword}, '%') " +
            "OR pinyin LIKE CONCAT('%', #{keyword}, '%'))")
    Page<MedicalTerm> searchByKeyword(Page<MedicalTerm> page, @Param("keyword") String keyword);

    @Select("SELECT * FROM medical_term WHERE deleted = 0 AND enabled = 1 AND term_type = #{termType} " +
            "AND (standard_name LIKE CONCAT('%', #{keyword}, '%') OR pinyin_abbr LIKE CONCAT('%', #{keyword}, '%'))")
    Page<MedicalTerm> searchByTypeAndKeyword(Page<MedicalTerm> page,
                                             @Param("termType") String termType,
                                             @Param("keyword") String keyword);

    @Select("SELECT * FROM medical_term WHERE deleted = 0 AND enabled = 1 AND category_id = #{categoryId}")
    List<MedicalTerm> selectByCategoryId(@Param("categoryId") Long categoryId);

    @Select("SELECT * FROM medical_term WHERE deleted = 0 AND enabled = 1 AND icd_code = #{icdCode}")
    List<MedicalTerm> selectByIcdCode(@Param("icdCode") String icdCode);

    @Update("UPDATE medical_term SET match_count = match_count + 1 WHERE id = #{id}")
    void incrementMatchCount(@Param("id") Long id);

    @Update("UPDATE medical_term SET usage_count = usage_count + 1 WHERE id = #{id}")
    void incrementUsageCount(@Param("id") Long id);
}
