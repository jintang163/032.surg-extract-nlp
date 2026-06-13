package com.surg.extract.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.surg.extract.entity.MedicalTermAlias;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface MedicalTermAliasMapper extends BaseMapper<MedicalTermAlias> {

    @Select("SELECT * FROM medical_term_alias WHERE term_id = #{termId} AND deleted = 0 ORDER BY similarity_score DESC")
    List<MedicalTermAlias> selectByTermId(@Param("termId") Long termId);

    @Select("SELECT * FROM medical_term_alias WHERE alias_name = #{aliasName} AND deleted = 0 AND enabled = 1 LIMIT 1")
    MedicalTermAlias selectByAliasName(@Param("aliasName") String aliasName);

    @Select("SELECT a.* FROM medical_term_alias a " +
            "INNER JOIN medical_term t ON a.term_id = t.id " +
            "WHERE a.deleted = 0 AND a.enabled = 1 AND t.enabled = 1 AND t.deleted = 0 " +
            "AND (a.alias_name = #{aliasName} OR a.pinyin = #{pinyin} OR a.pinyin_abbr = #{pinyinAbbr})")
    List<MedicalTermAlias> findExactMatches(@Param("aliasName") String aliasName,
                                            @Param("pinyin") String pinyin,
                                            @Param("pinyinAbbr") String pinyinAbbr);

    @Select("SELECT a.* FROM medical_term_alias a " +
            "INNER JOIN medical_term t ON a.term_id = t.id " +
            "WHERE a.deleted = 0 AND a.enabled = 1 AND t.enabled = 1 AND t.deleted = 0 " +
            "AND a.alias_name LIKE CONCAT('%', #{keyword}, '%')")
    Page<MedicalTermAlias> searchByKeyword(Page<MedicalTermAlias> page, @Param("keyword") String keyword);

    @Select("SELECT * FROM medical_term_alias WHERE term_id = #{termId} AND alias_name = #{aliasName} AND deleted = 0 LIMIT 1")
    MedicalTermAlias selectByTermIdAndAliasName(@Param("termId") Long termId, @Param("aliasName") String aliasName);

    @Update("UPDATE medical_term_alias SET match_count = match_count + 1 WHERE id = #{id}")
    void incrementMatchCount(@Param("id") Long id);

    @Update("UPDATE medical_term_alias SET usage_count = usage_count + 1 WHERE id = #{id}")
    void incrementUsageCount(@Param("id") Long id);
}
