package com.surg.extract.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.surg.extract.common.BusinessException;
import com.surg.extract.common.ErrorCode;
import com.surg.extract.dto.*;
import com.surg.extract.entity.DoctorFeedback;
import com.surg.extract.entity.ModelTrainLog;
import com.surg.extract.entity.SurgeryRecord;
import com.surg.extract.mapper.DoctorFeedbackMapper;
import com.surg.extract.mapper.ModelTrainLogMapper;
import com.surg.extract.mapper.SurgeryRecordMapper;
import com.surg.extract.types.EntityType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DoctorFeedbackService {

    private final DoctorFeedbackMapper feedbackMapper;
    private final ModelTrainLogMapper trainLogMapper;
    private final SurgeryRecordMapper recordMapper;

    private static final Map<String, String> CORRECTION_TYPE_LABEL_MAP = new HashMap<>();
    private static final Map<String, String> SOURCE_LABEL_MAP = new HashMap<>();

    static {
        CORRECTION_TYPE_LABEL_MAP.put("CORRECTION", "修改");
        CORRECTION_TYPE_LABEL_MAP.put("ADDITION", "新增");
        CORRECTION_TYPE_LABEL_MAP.put("DELETION", "删除");
        SOURCE_LABEL_MAP.put("MODEL", "模型识别");
        SOURCE_LABEL_MAP.put("REGEX", "正则匹配");
        SOURCE_LABEL_MAP.put("RULE", "规则引擎");
        SOURCE_LABEL_MAP.put("MANUAL", "人工修正");
    }

    @Transactional
    public DoctorFeedbackDTO createFeedback(DoctorFeedbackCreateDTO dto, Long userId, String userName) {
        if (dto.getRecordId() == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "recordId不能为空");
        }
        if (StrUtil.isBlank(dto.getEntityType())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "entityType不能为空");
        }
        if (StrUtil.isBlank(dto.getCorrectionType())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "correctionType不能为空");
        }

        SurgeryRecord record = recordMapper.selectById(dto.getRecordId());
        if (record == null) {
            throw new BusinessException(ErrorCode.RECORD_NOT_FOUND);
        }

        DoctorFeedback feedback = new DoctorFeedback();
        BeanUtils.copyProperties(dto, feedback);
        feedback.setRecordNo(record.getRecordNo());
        feedback.setDepartment(record.getDepartment());
        feedback.setFeedbackUserId(userId);
        feedback.setFeedbackUserName(userName);
        feedback.setFeedbackSource("ENTITY_EDIT");
        feedback.setUsedForTraining(0);
        if (feedback.getQualityScore() == null) {
            feedback.setQualityScore(calculateQualityScore(dto));
        }

        feedbackMapper.insert(feedback);
        return convertToDTO(feedback);
    }

    @Transactional
    public List<DoctorFeedbackDTO> batchCreateFeedback(List<DoctorFeedbackCreateDTO> dtoList, Long userId, String userName) {
        List<DoctorFeedbackDTO> result = new ArrayList<>();
        for (DoctorFeedbackCreateDTO dto : dtoList) {
            result.add(createFeedback(dto, userId, userName));
        }
        return result;
    }

    public DoctorFeedbackDTO getFeedbackById(Long id) {
        DoctorFeedback feedback = feedbackMapper.selectById(id);
        if (feedback == null) {
            throw new BusinessException(ErrorCode.RECORD_NOT_FOUND);
        }
        return convertToDTO(feedback);
    }

    public List<DoctorFeedbackDTO> getFeedbackByRecordId(Long recordId) {
        List<DoctorFeedback> list = feedbackMapper.selectByRecordId(recordId);
        return list.stream().map(this::convertToDTO).collect(Collectors.toList());
    }

    public PageResult<DoctorFeedbackDTO> getFeedbackPage(
            Integer pageNum, Integer pageSize,
            Long recordId, String entityType, String correctionType,
            Integer usedForTraining, String department,
            LocalDate startDate, LocalDate endDate) {

        Page<DoctorFeedback> page = new Page<>(pageNum, pageSize);
        com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<DoctorFeedback> wrapper =
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>();

        wrapper.eq(DoctorFeedback::getDeleted, 0);
        if (recordId != null) wrapper.eq(DoctorFeedback::getRecordId, recordId);
        if (StrUtil.isNotBlank(entityType)) wrapper.eq(DoctorFeedback::getEntityType, entityType);
        if (StrUtil.isNotBlank(correctionType)) wrapper.eq(DoctorFeedback::getCorrectionType, correctionType);
        if (usedForTraining != null) wrapper.eq(DoctorFeedback::getUsedForTraining, usedForTraining);
        if (StrUtil.isNotBlank(department)) wrapper.eq(DoctorFeedback::getDepartment, department);
        if (startDate != null) wrapper.ge(DoctorFeedback::getCreatedTime, startDate.atStartOfDay());
        if (endDate != null) wrapper.le(DoctorFeedback::getCreatedTime, endDate.atTime(23, 59, 59));

        wrapper.orderByDesc(DoctorFeedback::getCreatedTime);

        IPage<DoctorFeedback> resultPage = feedbackMapper.selectPage(page, wrapper);
        List<DoctorFeedbackDTO> dtoList = resultPage.getRecords().stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());

        return PageResult.of(dtoList, resultPage.getTotal(), pageNum, pageSize);
    }

    public FeedbackDashboardDTO getDashboard(LocalDate startDate, LocalDate endDate, String department) {
        FeedbackDashboardDTO dashboard = new FeedbackDashboardDTO();

        FeedbackOverviewDTO overview = feedbackMapper.selectOverview(startDate, endDate, department);
        ModelTrainLog latestTrain = trainLogMapper.selectLatestSuccess();
        if (latestTrain != null) {
            overview.setTotalTrainCount(((Number) trainLogMapper.selectCount(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ModelTrainLog>()
                            .eq(ModelTrainLog::getDeleted, 0)
                            .eq(ModelTrainLog::getTrainStatus, "SUCCESS")
            )).longValue());
            overview.setLatestF1Score(latestTrain.getF1Score());
            overview.setF1Improvement(latestTrain.getF1Improvement());
        } else {
            overview.setTotalTrainCount(0L);
            overview.setLatestF1Score(BigDecimal.ZERO);
            overview.setF1Improvement(BigDecimal.ZERO);
        }
        dashboard.setOverview(overview);

        List<FeedbackTrendDTO> trend = feedbackMapper.selectFeedbackTrend(startDate, endDate, department, "day");
        dashboard.setFeedbackTrend(trend);

        List<EntityFeedbackStatsDTO> entityStats = feedbackMapper.selectEntityTypeStats(startDate, endDate, department);
        entityStats.forEach(e -> {
            String label = EntityType.fromCode(e.getEntityType());
            if (label != null) {
                e.setEntityTypeLabel(label);
            } else {
                e.setEntityTypeLabel(e.getEntityType());
            }
        });
        dashboard.setEntityTypeStats(entityStats);

        List<CorrectionTypeStatsDTO> corrStats = feedbackMapper.selectCorrectionTypeStats(startDate, endDate, department);
        corrStats.forEach(c -> c.setCorrectionTypeLabel(
                CORRECTION_TYPE_LABEL_MAP.getOrDefault(c.getCorrectionType(), c.getCorrectionType())
        ));
        dashboard.setCorrectionTypeStats(corrStats);

        List<DoctorFeedbackStatsDTO> doctorStats = feedbackMapper.selectDoctorStats(startDate, endDate, department, 10);
        dashboard.setTopDoctors(doctorStats);

        List<ModelTrainLog> recentLogs = trainLogMapper.selectLatestN(5);
        dashboard.setRecentTrainLogs(recentLogs.stream().map(this::convertTrainLogToDTO).collect(Collectors.toList()));

        return dashboard;
    }

    public List<FeedbackTrendDTO> getFeedbackTrend(LocalDate startDate, LocalDate endDate, String department, String groupBy) {
        return feedbackMapper.selectFeedbackTrend(startDate, endDate, department, groupBy);
    }

    public List<EntityFeedbackStatsDTO> getEntityTypeStats(LocalDate startDate, LocalDate endDate, String department) {
        List<EntityFeedbackStatsDTO> list = feedbackMapper.selectEntityTypeStats(startDate, endDate, department);
        list.forEach(e -> {
            String label = EntityType.fromCode(e.getEntityType());
            if (label != null) {
                e.setEntityTypeLabel(label);
            } else {
                e.setEntityTypeLabel(e.getEntityType());
            }
        });
        return list;
    }

    public List<CorrectionTypeStatsDTO> getCorrectionTypeStats(LocalDate startDate, LocalDate endDate, String department) {
        List<CorrectionTypeStatsDTO> list = feedbackMapper.selectCorrectionTypeStats(startDate, endDate, department);
        list.forEach(c -> c.setCorrectionTypeLabel(
                CORRECTION_TYPE_LABEL_MAP.getOrDefault(c.getCorrectionType(), c.getCorrectionType())
        ));
        return list;
    }

    public List<DoctorFeedbackStatsDTO> getTopDoctors(LocalDate startDate, LocalDate endDate, String department, Integer limit) {
        return feedbackMapper.selectDoctorStats(startDate, endDate, department, limit);
    }

    public byte[] exportTrainingData(LocalDate startDate, LocalDate endDate,
                                      String department, Integer minQualityScore,
                                      Integer limit) {
        List<DoctorFeedback> feedbackList = feedbackMapper.selectPendingForTraining(
                limit != null ? limit : 10000,
                minQualityScore != null ? minQualityScore : 60
        );

        StringBuilder sb = new StringBuilder();
        sb.append("id\trecord_id\tentity_type\toriginal_value\tcorrected_value\tcorrection_type\toriginal_confidence\tquality_score\n");
        for (DoctorFeedback fb : feedbackList) {
            sb.append(fb.getId()).append("\t");
            sb.append(fb.getRecordId()).append("\t");
            sb.append(fb.getEntityType()).append("\t");
            sb.append(nullToEmpty(fb.getOriginalValue())).append("\t");
            sb.append(nullToEmpty(fb.getCorrectedValue())).append("\t");
            sb.append(nullToEmpty(fb.getCorrectionType())).append("\t");
            sb.append(fb.getOriginalConfidence() != null ? fb.getOriginalConfidence().toPlainString() : "").append("\t");
            sb.append(fb.getQualityScore() != null ? fb.getQualityScore() : "").append("\n");
        }
        return sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    private String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private Integer calculateQualityScore(DoctorFeedbackCreateDTO dto) {
        int score = 50;
        if (StrUtil.isNotBlank(dto.getCorrectedValue())) score += 20;
        if (dto.getOriginalConfidence() != null) {
            if (dto.getOriginalConfidence().compareTo(new BigDecimal("0.8")) >= 0) {
                score += 15;
            } else if (dto.getOriginalConfidence().compareTo(new BigDecimal("0.5")) >= 0) {
                score += 10;
            }
        }
        if (StrUtil.isNotBlank(dto.getCorrectedUnit()) || StrUtil.isNotBlank(dto.getOriginalUnit())) {
            score += 10;
        }
        if (StrUtil.isNotBlank(dto.getFeedbackRemark())) score += 5;
        return Math.min(score, 100);
    }

    private DoctorFeedbackDTO convertToDTO(DoctorFeedback feedback) {
        DoctorFeedbackDTO dto = new DoctorFeedbackDTO();
        BeanUtils.copyProperties(feedback, dto);
        dto.setCorrectionTypeLabel(CORRECTION_TYPE_LABEL_MAP.getOrDefault(
                feedback.getCorrectionType(), feedback.getCorrectionType()));
        dto.setOriginalSourceLabel(SOURCE_LABEL_MAP.getOrDefault(
                feedback.getOriginalSource(), feedback.getOriginalSource()));
        String entityLabel = EntityType.fromCode(feedback.getEntityType());
        dto.setEntityTypeLabel(entityLabel != null ? entityLabel : feedback.getEntityType());
        return dto;
    }

    private ModelTrainLogDTO convertTrainLogToDTO(ModelTrainLog log) {
        ModelTrainLogDTO dto = new ModelTrainLogDTO();
        BeanUtils.copyProperties(log, dto);
        return dto;
    }
}
