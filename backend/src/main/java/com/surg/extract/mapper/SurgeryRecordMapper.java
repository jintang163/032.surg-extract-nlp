package com.surg.extract.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.surg.extract.dto.CoverageTrendDTO;
import com.surg.extract.dto.DepartmentStatsDTO;
import com.surg.extract.dto.EfficiencyTrendDTO;
import com.surg.extract.dto.SurgeonStatsDTO;
import com.surg.extract.dto.SurgeryTypeStatsDTO;
import com.surg.extract.dto.SurgeryWordCloudDTO;
import com.surg.extract.entity.SurgeryRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

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

    Map<String, Object> selectOverviewStats(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("department") String department
    );

    List<CoverageTrendDTO> selectCoverageTrend(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("department") String department,
            @Param("groupBy") String groupBy
    );

    List<EfficiencyTrendDTO> selectEfficiencyTrend(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("department") String department,
            @Param("surgeon") String surgeon,
            @Param("groupBy") String groupBy
    );

    List<DepartmentStatsDTO> selectDepartmentStats(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    List<SurgeonStatsDTO> selectSurgeonStats(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("department") String department,
            @Param("limit") Integer limit
    );

    List<SurgeryTypeStatsDTO> selectSurgeryTypeStats(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("department") String department,
            @Param("limit") Integer limit
    );

    List<SurgeryWordCloudDTO> selectSurgeryWordCloud(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("department") String department,
            @Param("limit") Integer limit
    );
}
