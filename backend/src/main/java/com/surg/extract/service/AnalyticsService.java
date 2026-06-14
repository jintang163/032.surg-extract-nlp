package com.surg.extract.service;

import com.surg.extract.dto.*;
import com.surg.extract.mapper.SurgeryEntityMapper;
import com.surg.extract.mapper.SurgeryRecordMapper;
import com.surg.extract.types.EntityType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private final SurgeryRecordMapper recordMapper;
    private final SurgeryEntityMapper entityMapper;

    public AnalyticsOverviewDTO getOverview(LocalDate startDate, LocalDate endDate, String department) {
        Map<String, Object> raw = recordMapper.selectOverviewStats(startDate, endDate, department);
        AnalyticsOverviewDTO dto = new AnalyticsOverviewDTO();

        Integer totalRecords = ((Number) raw.getOrDefault("totalRecords", 0)).intValue();
        Integer extractedRecords = ((Number) raw.getOrDefault("extractedRecords", 0)).intValue();
        Long totalManual = ((Number) raw.getOrDefault("totalManualDuration", 0L)).longValue();
        Long totalActual = ((Number) raw.getOrDefault("totalActualDuration", 0L)).longValue();

        dto.setTotalRecords(totalRecords);
        dto.setExtractedRecords(extractedRecords);
        dto.setOverallCoverageRate(totalRecords > 0 ?
                round(extractedRecords * 100.0 / totalRecords) : 0.0);
        dto.setOverallTimeSavedRate(totalManual > 0 ?
                round((totalManual - totalActual) * 100.0 / totalManual) : 0.0);
        dto.setTotalDepartments(((Number) raw.getOrDefault("totalDepartments", 0)).intValue());
        dto.setTotalSurgeons(((Number) raw.getOrDefault("totalSurgeons", 0)).intValue());

        List<AccuracyTrendDTO> accuracyList = entityMapper.selectAccuracyTrend(
                startDate, endDate, department, null, null);
        int totalEntities = 0;
        int accurateEntities = 0;
        for (AccuracyTrendDTO a : accuracyList) {
            totalEntities += a.getTotalEntities() != null ? a.getTotalEntities() : 0;
            int acc = (a.getVerifiedEntities() != null ? a.getVerifiedEntities() : 0) +
                    (a.getHighConfidenceEntities() != null ? a.getHighConfidenceEntities() : 0);
            accurateEntities += acc;
        }
        dto.setOverallAccuracyRate(totalEntities > 0 ? round(accurateEntities * 100.0 / totalEntities / 2) : 0.0);

        return dto;
    }

    public List<CoverageTrendDTO> getCoverageTrend(LocalDate startDate, LocalDate endDate,
                                                    String department, String groupBy) {
        return recordMapper.selectCoverageTrend(startDate, endDate, department, groupBy);
    }

    public List<EfficiencyTrendDTO> getEfficiencyTrend(LocalDate startDate, LocalDate endDate,
                                                        String department, String surgeon, String groupBy) {
        return recordMapper.selectEfficiencyTrend(startDate, endDate, department, surgeon, groupBy);
    }

    public List<AccuracyTrendDTO> getAccuracyTrend(LocalDate startDate, LocalDate endDate,
                                                    String department, String entityType, String groupBy) {
        return entityMapper.selectAccuracyTrend(startDate, endDate, department, entityType, groupBy);
    }

    public List<DepartmentStatsDTO> getDepartmentStats(LocalDate startDate, LocalDate endDate) {
        return recordMapper.selectDepartmentStats(startDate, endDate);
    }

    public List<SurgeonStatsDTO> getSurgeonStats(LocalDate startDate, LocalDate endDate,
                                                  String department, Integer limit) {
        return recordMapper.selectSurgeonStats(startDate, endDate, department, limit != null ? limit : 20);
    }

    public List<SurgeryTypeStatsDTO> getSurgeryTypeStats(LocalDate startDate, LocalDate endDate,
                                                          String department, Integer limit) {
        return recordMapper.selectSurgeryTypeStats(startDate, endDate, department, limit != null ? limit : 20);
    }

    public List<SurgeryWordCloudDTO> getSurgeryWordCloud(LocalDate startDate, LocalDate endDate,
                                                          String department, Integer limit) {
        return recordMapper.selectSurgeryWordCloud(startDate, endDate, department, limit != null ? limit : 100);
    }

    public List<LowConfidenceDistributionDTO> getLowConfidenceDistribution(LocalDate startDate, LocalDate endDate,
                                                                             String department, Double threshold) {
        double t = threshold != null ? threshold : 0.6;
        List<LowConfidenceDistributionDTO> list = entityMapper.selectLowConfidenceDistribution(
                startDate, endDate, department, t);
        Map<String, String> labelMap = EntityType.getLabelMap();
        for (LowConfidenceDistributionDTO dto : list) {
            dto.setEntityLabel(labelMap.getOrDefault(dto.getEntityType(), dto.getEntityType()));
        }
        return list;
    }

    public Map<String, Object> getFullDashboard(LocalDate startDate, LocalDate endDate, String department) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("overview", getOverview(startDate, endDate, department));
        result.put("coverageTrend", getCoverageTrend(startDate, endDate, department, null));
        result.put("efficiencyTrend", getEfficiencyTrend(startDate, endDate, department, null, null));
        result.put("accuracyTrend", getAccuracyTrend(startDate, endDate, department, null, null));
        result.put("departmentStats", getDepartmentStats(startDate, endDate));
        result.put("surgeonStats", getSurgeonStats(startDate, endDate, department, 15));
        result.put("surgeryTypeStats", getSurgeryTypeStats(startDate, endDate, department, 15));
        result.put("surgeryWordCloud", getSurgeryWordCloud(startDate, endDate, department, 80));
        result.put("lowConfidenceDistribution", getLowConfidenceDistribution(startDate, endDate, department, 0.6));
        return result;
    }

    private Double round(Double value) {
        if (value == null || value.isNaN() || value.isInfinite()) return 0.0;
        return BigDecimal.valueOf(value).setScale(2, BigDecimal.ROUND_HALF_UP).doubleValue();
    }
}
