package com.surg.extract.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.surg.extract.dto.*;
import com.surg.extract.entity.DoctorFeedback;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Mapper
public interface DoctorFeedbackMapper extends BaseMapper<DoctorFeedback> {

    List<DoctorFeedback> selectByRecordId(@Param("recordId") Long recordId);

    List<DoctorFeedback> selectPendingForTraining(
            @Param("limit") Integer limit,
            @Param("minQualityScore") Integer minQualityScore
    );

    Integer markAsUsed(
            @Param("ids") List<Long> ids,
            @Param("trainBatchNo") String trainBatchNo
    );

    FeedbackOverviewDTO selectOverview(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("department") String department
    );

    List<FeedbackTrendDTO> selectFeedbackTrend(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("department") String department,
            @Param("groupBy") String groupBy
    );

    List<EntityFeedbackStatsDTO> selectEntityTypeStats(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("department") String department
    );

    List<CorrectionTypeStatsDTO> selectCorrectionTypeStats(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("department") String department
    );

    List<DoctorFeedbackStatsDTO> selectDoctorStats(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("department") String department,
            @Param("limit") Integer limit
    );

    Map<String, Object> selectDepartmentStats(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("department") String department
    );
}
