package com.surg.extract.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.surg.extract.entity.MedicalTermIcd;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface MedicalTermIcdMapper extends BaseMapper<MedicalTermIcd> {

    @Select("SELECT * FROM medical_term_icd WHERE icd_code = #{icdCode} AND icd_version = #{icdVersion} AND enabled = 1")
    MedicalTermIcd selectByCodeAndVersion(@Param("icdCode") String icdCode, @Param("icdVersion") String icdVersion);

    @Select("SELECT * FROM medical_term_icd WHERE icd_version = #{icdVersion} AND enabled = 1 " +
            "AND (icd_code LIKE CONCAT('%', #{keyword}, '%') OR icd_name LIKE CONCAT('%', #{keyword}, '%'))")
    Page<MedicalTermIcd> searchByKeyword(Page<MedicalTermIcd> page,
                                         @Param("keyword") String keyword,
                                         @Param("icdVersion") String icdVersion);

    @Select("SELECT * FROM medical_term_icd WHERE icd_version = #{icdVersion} AND enabled = 1 ORDER BY icd_code ASC")
    List<MedicalTermIcd> selectByVersion(@Param("icdVersion") String icdVersion);
}
