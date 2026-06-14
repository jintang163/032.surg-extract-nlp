package com.surg.extract.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.surg.extract.entity.SurgeryRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface SurgeryRecordMapper extends BaseMapper<SurgeryRecord> {

    List<SurgeryRecord> selectByConditions(
            @Param("patientName") String patientName,
            @Param("hospitalNo") String hospitalNo,
            @Param("status") String status,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("offset") Integer offset,
            @Param("limit") Integer limit
    );

    Long countByConditions(
            @Param("patientName") String patientName,
            @Param("hospitalNo") String hospitalNo,
            @Param("status") String status,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    Integer updateStatus(@Param("id") Long id, @Param("status") String status, @Param("message") String message);

    Integer updateNerTime(@Param("id") Long id, @Param("startTime") LocalDateTime startTime, @Param("endTime") LocalDateTime endTime);

    Integer updateOcrTime(@Param("id") Long id, @Param("startTime") LocalDateTime startTime, @Param("endTime") LocalDateTime endTime);

    List<SurgeryRecord> selectRecentCompletedByDepartment(
            @Param("department") String department,
            @Param("fromTime") LocalDateTime fromTime
    );
}
