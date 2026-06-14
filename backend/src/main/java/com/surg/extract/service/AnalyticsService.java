package com.surg.extract.service;

import com.surg.extract.dto.*;
import com.surg.extract.mapper.SurgeryEntityMapper;
import com.surg.extract.mapper.SurgeryRecordMapper;
import com.surg.extract.types.EntityType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

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

        Map<String, Object> accuracyMap = entityMapper.selectOverallAccuracy(startDate, endDate, department);
        if (accuracyMap != null && accuracyMap.get("accuracyRate") != null) {
            dto.setOverallAccuracyRate(((Number) accuracyMap.get("accuracyRate")).doubleValue());
        } else {
            dto.setOverallAccuracyRate(0.0);
        }

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
        List<DepartmentStatsDTO> deptList = recordMapper.selectDepartmentStats(startDate, endDate);

        List<Map<String, Object>> accuracyList = entityMapper.selectAccuracyByDepartment(
                startDate, endDate, null);
        Map<String, Double> accuracyMap = new HashMap<>();
        for (Map<String, Object> m : accuracyList) {
            String dept = (String) m.get("department");
            Double rate = m.get("avgAccuracyRate") != null ?
                    ((Number) m.get("avgAccuracyRate")).doubleValue() : 0.0;
            accuracyMap.put(dept, rate);
        }

        for (DepartmentStatsDTO dept : deptList) {
            dept.setAvgAccuracyRate(accuracyMap.getOrDefault(dept.getDepartment(), 0.0));
        }

        return deptList;
    }

    public List<SurgeonStatsDTO> getSurgeonStats(LocalDate startDate, LocalDate endDate,
                                                  String department, Integer limit) {
        return recordMapper.selectSurgeonStats(startDate, endDate, department, limit != null ? limit : 20);
    }

    public List<SurgeryTypeStatsDTO> getSurgeryTypeStats(LocalDate startDate, LocalDate endDate,
                                                          String department, Integer limit) {
        List<SurgeryTypeStatsDTO> baseList = recordMapper.selectSurgeryTypeStats(
                startDate, endDate, department, limit != null ? limit : 100);

        List<Map<String, Object>> accuracyList = entityMapper.selectAccuracyBySurgeryName(
                startDate, endDate, department, limit != null ? limit : 100);

        Map<String, Double> accuracyMap = new HashMap<>();
        for (Map<String, Object> m : accuracyList) {
            String name = (String) m.get("surgeryName");
            Double rate = m.get("avgAccuracyRate") != null ?
                    ((Number) m.get("avgAccuracyRate")).doubleValue() : 0.0;
            accuracyMap.put(name, rate);
        }

        for (SurgeryTypeStatsDTO s : baseList) {
            s.setAvgAccuracyRate(accuracyMap.getOrDefault(s.getSurgeryName(), 0.0));
        }

        return baseList.stream()
                .sorted(Comparator.comparingInt(SurgeryTypeStatsDTO::getRecordCount).reversed())
                .limit(limit != null ? limit : 20)
                .collect(Collectors.toList());
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
        result.put("coverageTrend", getCoverageTrend(startDate, endDate, department, "department"));
        result.put("efficiencyTrend", getEfficiencyTrend(startDate, endDate, department, null, null));
        result.put("accuracyTrend", getAccuracyTrend(startDate, endDate, department, null, "entityType"));
        result.put("departmentStats", getDepartmentStats(startDate, endDate));
        result.put("surgeonStats", getSurgeonStats(startDate, endDate, department, 15));
        result.put("surgeryTypeStats", getSurgeryTypeStats(startDate, endDate, department, 15));
        result.put("surgeryWordCloud", getSurgeryWordCloud(startDate, endDate, department, 80));
        result.put("lowConfidenceDistribution", getLowConfidenceDistribution(startDate, endDate, department, 0.6));
        return result;
    }

    public byte[] exportDashboardReport(LocalDate startDate, LocalDate endDate, String department) throws IOException {
        Map<String, Object> dashboard = getFullDashboard(startDate, endDate, department);

        try (Workbook workbook = new XSSFWorkbook()) {
            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle numberStyle = createNumberStyle(workbook);

            createOverviewSheet(workbook, dashboard, headerStyle, numberStyle, startDate, endDate, department);
            createCoverageSheet(workbook, dashboard, headerStyle, numberStyle);
            createEfficiencySheet(workbook, dashboard, headerStyle, numberStyle);
            createAccuracySheet(workbook, dashboard, headerStyle, numberStyle);
            createDepartmentSheet(workbook, dashboard, headerStyle, numberStyle);
            createSurgeonSheet(workbook, dashboard, headerStyle, numberStyle);
            createSurgeryTypeSheet(workbook, dashboard, headerStyle, numberStyle);
            createLowConfidenceSheet(workbook, dashboard, headerStyle, numberStyle);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        }
    }

    private CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    private CellStyle createNumberStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        DataFormat format = workbook.createDataFormat();
        style.setDataFormat(format.getFormat("0.00"));
        return style;
    }

    private void createOverviewSheet(Workbook workbook, Map<String, Object> dashboard,
                                      CellStyle headerStyle, CellStyle numberStyle,
                                      LocalDate startDate, LocalDate endDate, String department) {
        Sheet sheet = workbook.createSheet("总览");
        AnalyticsOverviewDTO overview = (AnalyticsOverviewDTO) dashboard.get("overview");
        if (overview == null) return;

        int rowNum = 0;
        Row titleRow = sheet.createRow(rowNum++);
        titleRow.createCell(0).setCellValue("质控统计仪表盘 - 总览报表");

        Row infoRow = sheet.createRow(rowNum++);
        infoRow.createCell(0).setCellValue("统计周期: " +
                (startDate != null ? startDate.format(DateTimeFormatter.ISO_LOCAL_DATE) : "全部") +
                " 至 " +
                (endDate != null ? endDate.format(DateTimeFormatter.ISO_LOCAL_DATE) : "全部"));

        Row deptRow = sheet.createRow(rowNum++);
        deptRow.createCell(0).setCellValue("科室: " + (department != null ? department : "全部"));

        rowNum++;

        Row headerRow = sheet.createRow(rowNum++);
        headerRow.createCell(0).setCellValue("指标");
        headerRow.createCell(1).setCellValue("数值");
        headerRow.getCell(0).setCellStyle(headerStyle);
        headerRow.getCell(1).setCellStyle(headerStyle);

        addMetricRow(sheet, rowNum++, "总记录数", overview.getTotalRecords().toString(), headerStyle);
        addMetricRow(sheet, rowNum++, "已提取记录数", overview.getExtractedRecords().toString(), headerStyle);
        addMetricRow(sheet, rowNum++, "结构化提取覆盖率(%)",
                String.format("%.2f", overview.getOverallCoverageRate()), headerStyle);
        addMetricRow(sheet, rowNum++, "平均填充时间节省率(%)",
                String.format("%.2f", overview.getOverallTimeSavedRate()), headerStyle);
        addMetricRow(sheet, rowNum++, "字段识别准确率(%)",
                String.format("%.2f", overview.getOverallAccuracyRate()), headerStyle);
        addMetricRow(sheet, rowNum++, "覆盖科室数", overview.getTotalDepartments().toString(), headerStyle);
        addMetricRow(sheet, rowNum++, "覆盖医生数", overview.getTotalSurgeons().toString(), headerStyle);

        sheet.autoSizeColumn(0);
        sheet.autoSizeColumn(1);
    }

    private void addMetricRow(Sheet sheet, int rowNum, String metric, String value, CellStyle headerStyle) {
        Row row = sheet.createRow(rowNum);
        Cell cell0 = row.createCell(0);
        cell0.setCellValue(metric);
        cell0.setCellStyle(headerStyle);
        row.createCell(1).setCellValue(value);
    }

    private void createCoverageSheet(Workbook workbook, Map<String, Object> dashboard,
                                      CellStyle headerStyle, CellStyle numberStyle) {
        Sheet sheet = workbook.createSheet("覆盖率趋势");
        @SuppressWarnings("unchecked")
        List<CoverageTrendDTO> list = (List<CoverageTrendDTO>) dashboard.get("coverageTrend");
        if (list == null || list.isEmpty()) return;

        Row headerRow = sheet.createRow(0);
        String[] headers = {"日期", "科室", "总记录数", "已提取数", "覆盖率(%)"};
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        int rowNum = 1;
        for (CoverageTrendDTO item : list) {
            Row row = sheet.createRow(rowNum++);
            row.createCell(0).setCellValue(item.getDate() != null ? item.getDate().toString() : "");
            row.createCell(1).setCellValue(item.getDepartment() != null ? item.getDepartment() : "");
            row.createCell(2).setCellValue(item.getTotalRecords() != null ? item.getTotalRecords() : 0);
            row.createCell(3).setCellValue(item.getExtractedRecords() != null ? item.getExtractedRecords() : 0);
            Cell rateCell = row.createCell(4);
            rateCell.setCellValue(item.getCoverageRate() != null ? item.getCoverageRate() : 0);
            rateCell.setCellStyle(numberStyle);
        }

        for (int i = 0; i < headers.length; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    private void createEfficiencySheet(Workbook workbook, Map<String, Object> dashboard,
                                        CellStyle headerStyle, CellStyle numberStyle) {
        Sheet sheet = workbook.createSheet("效率趋势");
        @SuppressWarnings("unchecked")
        List<EfficiencyTrendDTO> list = (List<EfficiencyTrendDTO>) dashboard.get("efficiencyTrend");
        if (list == null || list.isEmpty()) return;

        Row headerRow = sheet.createRow(0);
        String[] headers = {"日期", "科室", "医生", "记录数", "平均人工时长(秒)", "平均实际时长(秒)", "时间节省率(%)"};
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        int rowNum = 1;
        for (EfficiencyTrendDTO item : list) {
            Row row = sheet.createRow(rowNum++);
            row.createCell(0).setCellValue(item.getDate() != null ? item.getDate().toString() : "");
            row.createCell(1).setCellValue(item.getDepartment() != null ? item.getDepartment() : "");
            row.createCell(2).setCellValue(item.getSurgeon() != null ? item.getSurgeon() : "");
            row.createCell(3).setCellValue(item.getRecordCount() != null ? item.getRecordCount() : 0);
            row.createCell(4).setCellValue(item.getAvgManualDuration() != null ? item.getAvgManualDuration() : 0);
            row.createCell(5).setCellValue(item.getAvgActualDuration() != null ? item.getAvgActualDuration() : 0);
            Cell rateCell = row.createCell(6);
            rateCell.setCellValue(item.getTimeSavedRate() != null ? item.getTimeSavedRate() : 0);
            rateCell.setCellStyle(numberStyle);
        }

        for (int i = 0; i < headers.length; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    private void createAccuracySheet(Workbook workbook, Map<String, Object> dashboard,
                                      CellStyle headerStyle, CellStyle numberStyle) {
        Sheet sheet = workbook.createSheet("准确率趋势");
        @SuppressWarnings("unchecked")
        List<AccuracyTrendDTO> list = (List<AccuracyTrendDTO>) dashboard.get("accuracyTrend");
        if (list == null || list.isEmpty()) return;

        Row headerRow = sheet.createRow(0);
        String[] headers = {"日期", "科室", "字段类型", "实体总数", "已审核数", "高置信度数", "准确率(%)"};
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        int rowNum = 1;
        for (AccuracyTrendDTO item : list) {
            Row row = sheet.createRow(rowNum++);
            row.createCell(0).setCellValue(item.getDate() != null ? item.getDate().toString() : "");
            row.createCell(1).setCellValue(item.getDepartment() != null ? item.getDepartment() : "");
            row.createCell(2).setCellValue(item.getEntityType() != null ? item.getEntityType() : "");
            row.createCell(3).setCellValue(item.getTotalEntities() != null ? item.getTotalEntities() : 0);
            row.createCell(4).setCellValue(item.getVerifiedEntities() != null ? item.getVerifiedEntities() : 0);
            row.createCell(5).setCellValue(item.getHighConfidenceEntities() != null ? item.getHighConfidenceEntities() : 0);
            Cell rateCell = row.createCell(6);
            rateCell.setCellValue(item.getAccuracyRate() != null ? item.getAccuracyRate() : 0);
            rateCell.setCellStyle(numberStyle);
        }

        for (int i = 0; i < headers.length; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    private void createDepartmentSheet(Workbook workbook, Map<String, Object> dashboard,
                                        CellStyle headerStyle, CellStyle numberStyle) {
        Sheet sheet = workbook.createSheet("科室统计");
        @SuppressWarnings("unchecked")
        List<DepartmentStatsDTO> list = (List<DepartmentStatsDTO>) dashboard.get("departmentStats");
        if (list == null || list.isEmpty()) return;

        Row headerRow = sheet.createRow(0);
        String[] headers = {"科室", "总记录数", "已提取数", "覆盖率(%)", "时间节省率(%)", "准确率(%)"};
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        int rowNum = 1;
        for (DepartmentStatsDTO item : list) {
            Row row = sheet.createRow(rowNum++);
            row.createCell(0).setCellValue(item.getDepartment() != null ? item.getDepartment() : "");
            row.createCell(1).setCellValue(item.getTotalRecords() != null ? item.getTotalRecords() : 0);
            row.createCell(2).setCellValue(item.getExtractedRecords() != null ? item.getExtractedRecords() : 0);
            Cell covCell = row.createCell(3);
            covCell.setCellValue(item.getCoverageRate() != null ? item.getCoverageRate() : 0);
            covCell.setCellStyle(numberStyle);
            Cell effCell = row.createCell(4);
            effCell.setCellValue(item.getAvgTimeSavedRate() != null ? item.getAvgTimeSavedRate() : 0);
            effCell.setCellStyle(numberStyle);
            Cell accCell = row.createCell(5);
            accCell.setCellValue(item.getAvgAccuracyRate() != null ? item.getAvgAccuracyRate() : 0);
            accCell.setCellStyle(numberStyle);
        }

        for (int i = 0; i < headers.length; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    private void createSurgeonSheet(Workbook workbook, Map<String, Object> dashboard,
                                     CellStyle headerStyle, CellStyle numberStyle) {
        Sheet sheet = workbook.createSheet("医生统计");
        @SuppressWarnings("unchecked")
        List<SurgeonStatsDTO> list = (List<SurgeonStatsDTO>) dashboard.get("surgeonStats");
        if (list == null || list.isEmpty()) return;

        Row headerRow = sheet.createRow(0);
        String[] headers = {"手术医生", "记录数", "覆盖率(%)", "时间节省率(%)"};
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        int rowNum = 1;
        for (SurgeonStatsDTO item : list) {
            Row row = sheet.createRow(rowNum++);
            row.createCell(0).setCellValue(item.getSurgeon() != null ? item.getSurgeon() : "");
            row.createCell(1).setCellValue(item.getRecordCount() != null ? item.getRecordCount() : 0);
            Cell covCell = row.createCell(2);
            covCell.setCellValue(item.getCoverageRate() != null ? item.getCoverageRate() : 0);
            covCell.setCellStyle(numberStyle);
            Cell effCell = row.createCell(3);
            effCell.setCellValue(item.getAvgTimeSavedRate() != null ? item.getAvgTimeSavedRate() : 0);
            effCell.setCellStyle(numberStyle);
        }

        for (int i = 0; i < headers.length; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    private void createSurgeryTypeSheet(Workbook workbook, Map<String, Object> dashboard,
                                         CellStyle headerStyle, CellStyle numberStyle) {
        Sheet sheet = workbook.createSheet("手术类型统计");
        @SuppressWarnings("unchecked")
        List<SurgeryTypeStatsDTO> list = (List<SurgeryTypeStatsDTO>) dashboard.get("surgeryTypeStats");
        if (list == null || list.isEmpty()) return;

        Row headerRow = sheet.createRow(0);
        String[] headers = {"手术名称", "记录数", "覆盖率(%)", "准确率(%)"};
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        int rowNum = 1;
        for (SurgeryTypeStatsDTO item : list) {
            Row row = sheet.createRow(rowNum++);
            row.createCell(0).setCellValue(item.getSurgeryName() != null ? item.getSurgeryName() : "");
            row.createCell(1).setCellValue(item.getRecordCount() != null ? item.getRecordCount() : 0);
            Cell covCell = row.createCell(2);
            covCell.setCellValue(item.getCoverageRate() != null ? item.getCoverageRate() : 0);
            covCell.setCellStyle(numberStyle);
            Cell accCell = row.createCell(3);
            accCell.setCellValue(item.getAvgAccuracyRate() != null ? item.getAvgAccuracyRate() : 0);
            accCell.setCellStyle(numberStyle);
        }

        for (int i = 0; i < headers.length; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    private void createLowConfidenceSheet(Workbook workbook, Map<String, Object> dashboard,
                                           CellStyle headerStyle, CellStyle numberStyle) {
        Sheet sheet = workbook.createSheet("低置信度分布");
        @SuppressWarnings("unchecked")
        List<LowConfidenceDistributionDTO> list =
                (List<LowConfidenceDistributionDTO>) dashboard.get("lowConfidenceDistribution");
        if (list == null || list.isEmpty()) return;

        Row headerRow = sheet.createRow(0);
        String[] headers = {"字段类型", "字段名称", "低置信度数量", "平均置信度"};
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        int rowNum = 1;
        for (LowConfidenceDistributionDTO item : list) {
            Row row = sheet.createRow(rowNum++);
            row.createCell(0).setCellValue(item.getEntityType() != null ? item.getEntityType() : "");
            row.createCell(1).setCellValue(item.getEntityLabel() != null ? item.getEntityLabel() : "");
            row.createCell(2).setCellValue(item.getCount() != null ? item.getCount() : 0);
            Cell confCell = row.createCell(3);
            confCell.setCellValue(item.getAvgConfidence() != null ? item.getAvgConfidence() : 0);
            confCell.setCellStyle(numberStyle);
        }

        for (int i = 0; i < headers.length; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    private Double round(Double value) {
        if (value == null || value.isNaN() || value.isInfinite()) return 0.0;
        return BigDecimal.valueOf(value).setScale(2, BigDecimal.ROUND_HALF_UP).doubleValue();
    }
}
