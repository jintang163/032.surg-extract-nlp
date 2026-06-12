package com.surg.extract.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.surg.extract.entity.DepartmentCustomField;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface DepartmentCustomFieldMapper extends BaseMapper<DepartmentCustomField> {

    List<DepartmentCustomField> selectByDepartment(@Param("department") String department);

    List<DepartmentCustomField> selectEnabledByDepartment(@Param("department") String department);

    List<DepartmentCustomField> selectNerEnabledByDepartment(@Param("department") String department);
}
