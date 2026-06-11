package com.surg.extract.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.surg.extract.entity.MedicalRecordHome;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface MedicalRecordHomeMapper extends BaseMapper<MedicalRecordHome> {

    MedicalRecordHome selectByRecordId(@Param("recordId") Long recordId);

    Integer updateStatus(@Param("id") Long id, @Param("status") String status,
                         @Param("auditUserId") Long auditUserId, @Param("auditUserName") String auditUserName,
                         @Param("auditTime") java.time.LocalDateTime auditTime, @Param("auditRemark") String auditRemark);
}
