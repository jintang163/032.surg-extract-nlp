package com.surg.extract.service;

import com.surg.extract.dto.*;
import com.surg.extract.entity.FieldMapping;
import com.surg.extract.entity.MedicalRecordHome;
import com.surg.extract.entity.SurgeryRecord;
import com.surg.extract.mapper.FieldMappingMapper;
import com.surg.extract.mapper.MedicalRecordHomeMapper;
import com.surg.extract.mapper.SurgeryRecordMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class QualityControlService {

    private final KieContainer kieContainer;
    private final MedicalRecordHomeMapper homePageMapper;
    private final SurgeryRecordMapper recordMapper;
    private final FieldMappingMapper fieldMappingMapper;
    private final ObjectMapper objectMapper;

    private static final Map<String, String> FIELD_LABEL_MAP = new LinkedHashMap<>();
    private static final Set<String> REQUIRED_FIELDS = new HashSet<>();
    private static final Map<String, String> FIELD_DB_MAP = new LinkedHashMap<>();

    static {
        FIELD_LABEL_MAP.put("patientName", "患者姓名");
        FIELD_LABEL_MAP.put("gender", "性别");
        FIELD_LABEL_MAP.put("age", "年龄");
        FIELD_LABEL_MAP.put("hospitalNo", "住院号");
        FIELD_LABEL_MAP.put("department", "科室");
        FIELD_LABEL_MAP.put("admissionDiagnosis", "术前诊断");
        FIELD_LABEL_MAP.put("dischargeDiagnosis", "术后诊断");
        FIELD_LABEL_MAP.put("surgeryDate", "手术日期");
        FIELD_LABEL_MAP.put("surgeryName", "手术名称");
        FIELD_LABEL_MAP.put("surgeryCode", "手术编码");
        FIELD_LABEL_MAP.put("surgeryLevel", "手术等级");
        FIELD_LABEL_MAP.put("incisionLevel", "切口等级");
        FIELD_LABEL_MAP.put("incisionHealing", "切口愈合");
        FIELD_LABEL_MAP.put("anesthesiaType", "麻醉方式");
        FIELD_LABEL_MAP.put("bloodLoss", "失血量");
        FIELD_LABEL_MAP.put("bloodTransfusion", "输血量");
        FIELD_LABEL_MAP.put("fluidInfusion", "输液量");
        FIELD_LABEL_MAP.put("surgeon", "手术医生");
        FIELD_LABEL_MAP.put("chiefSurgeon", "主刀医生");
        FIELD_LABEL_MAP.put("assistant1", "第一助手");
        FIELD_LABEL_MAP.put("assistant2", "第二助手");
        FIELD_LABEL_MAP.put("anesthesiologist", "麻醉医生");
        FIELD_LABEL_MAP.put("scrubNurse", "器械护士");
        FIELD_LABEL_MAP.put("circulatingNurse", "巡回护士");
        FIELD_LABEL_MAP.put("complications", "术中并发症");
        FIELD_LABEL_MAP.put("criticalPatient", "危重患者");
        FIELD_LABEL_MAP.put("bedNo", "床号");

        REQUIRED_FIELDS.add("patientName");
        REQUIRED_FIELDS.add("gender");
        REQUIRED_FIELDS.add("age");
        REQUIRED_FIELDS.add("hospitalNo");
        REQUIRED_FIELDS.add("surgeryDate");
        REQUIRED_FIELDS.add("surgeryName");
        REQUIRED_FIELDS.add("incisionLevel");
        REQUIRED_FIELDS.add("anesthesiaType");
        REQUIRED_FIELDS.add("surgeon");
        REQUIRED_FIELDS.add("anesthesiologist");

        FIELD_DB_MAP.put("patientName", "patient_name");
        FIELD_DB_MAP.put("gender", "gender");
        FIELD_DB_MAP.put("age", "age");
        FIELD_DB_MAP.put("hospitalNo", "hospital_no");
        FIELD_DB_MAP.put("surgeryDate", "surgery_date");
        FIELD_DB_MAP.put("surgeryName", "surgery_name");
        FIELD_DB_MAP.put("incisionLevel", "incision_level");
        FIELD_DB_MAP.put("anesthesiaType", "anesthesia_type");
        FIELD_DB_MAP.put("surgeon", "surgeon");
        FIELD_DB_MAP.put("anesthesiologist", "anesthesiologist");
    }

    public QcCheckResult validate(Long recordId) {
        MedicalRecordHome home = homePageMapper.selectByRecordId(recordId);
        if (home == null) {
            throw new RuntimeException("病案首页不存在，recordId=" + recordId);
        }
        return validateByHomeData(home);
    }

    public QcCheckResult validateByHomeData(MedicalRecordHome home) {
        QcCheckFact fact = buildFact(home);
        QcCheckResult result = executeRules(fact);
        return result;
    }

    public QcCheckResult validateByFormData(HomePageUpdateDTO dto) {
        QcCheckFact fact = buildFactFromDTO(dto);
        return executeRules(fact);
    }

    private QcCheckResult executeRules(QcCheckFact fact) {
        fact.setViolations(new ArrayList<>());
        KieSession kieSession = kieContainer.newKieSession("qcKSession");
        try {
            kieSession.setGlobal("violations", fact.getViolations());
            kieSession.insert(fact);
            int firedRules = kieSession.fireAllRules();
            log.info("Drools规则引擎执行完成，触发了{}条规则", firedRules);

            QcCheckResult result = new QcCheckResult();
            result.setViolations(fact.getViolations());

            int logicRules = 11;
            int completenessRules = 8;
            int totalRules = logicRules + completenessRules;

            long logicViolations = fact.getViolations().stream()
                    .filter(v -> "LOGIC_CONSISTENCY".equals(v.getCategory()))
                    .count();
            long completenessViolations = fact.getViolations().stream()
                    .filter(v -> "COMPLETENESS".equals(v.getCategory()))
                    .count();

            result.setTotalChecks(totalRules);
            result.setPassedChecks(totalRules - (int)(logicViolations + completenessViolations));
            result.setFailedChecks((int)(logicViolations + completenessViolations));

            return result;
        } finally {
            kieSession.dispose();
        }
    }

    public QcScorecardDTO generateScorecard(Long recordId) {
        MedicalRecordHome home = homePageMapper.selectByRecordId(recordId);
        if (home == null) {
            throw new RuntimeException("病案首页不存在，recordId=" + recordId);
        }

        SurgeryRecord record = recordMapper.selectById(recordId);
        QcCheckResult checkResult = validateByHomeData(home);

        QcScorecardDTO scorecard = new QcScorecardDTO();
        scorecard.setRecordId(recordId);
        scorecard.setRecordNo(record != null ? record.getRecordNo() : "");
        scorecard.setPatientName(home.getPatientName());
        scorecard.setSurgeryName(home.getSurgeryName());
        scorecard.setViolations(checkResult.getViolations());

        int totalFields = FIELD_LABEL_MAP.size();
        int filledFields = countFilledFields(home);
        int requiredTotal = REQUIRED_FIELDS.size();
        int requiredFilled = countRequiredFilledFields(home);

        scorecard.setTotalFields(totalFields);
        scorecard.setFilledFields(filledFields);
        scorecard.setRequiredFields(requiredTotal);
        scorecard.setRequiredFilled(requiredFilled);

        double completenessScore = totalFields > 0 ? (double) filledFields / totalFields * 100 : 0;

        long logicErrors = checkResult.getViolations().stream()
                .filter(v -> "LOGIC_CONSISTENCY".equals(v.getCategory()) && "ERROR".equals(v.getSeverity()))
                .count();
        long logicWarnings = checkResult.getViolations().stream()
                .filter(v -> "LOGIC_CONSISTENCY".equals(v.getCategory()) && "WARNING".equals(v.getSeverity()))
                .count();
        int logicRuleCount = 11;
        int logicPassed = logicRuleCount - (int) logicErrors - (int) logicWarnings;
        if (logicPassed < 0) logicPassed = 0;

        double logicScore = logicRuleCount > 0 ? (double) logicPassed / logicRuleCount * 100 : 100;
        logicScore -= logicWarnings * 5;
        if (logicScore < 0) logicScore = 0;

        scorecard.setLogicRuleCount(logicRuleCount);
        scorecard.setLogicPassed(logicPassed);
        scorecard.setLogicFailed((int) (logicErrors + logicWarnings));

        scorecard.setCompletenessScore(Math.round(completenessScore * 10.0) / 10.0);
        scorecard.setLogicConsistencyScore(Math.round(logicScore * 10.0) / 10.0);

        double overall = completenessScore * 0.4 + logicScore * 0.6;
        scorecard.setOverallScore(Math.round(overall * 10.0) / 10.0);
        scorecard.setGrade(calculateGrade(overall));

        scorecard.setFieldChecks(buildFieldChecks(home, checkResult));

        return scorecard;
    }

    public byte[] exportReport(Long recordId) throws IOException {
        QcScorecardDTO scorecard = generateScorecard(recordId);

        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("质控报告");

            CellStyle titleStyle = workbook.createCellStyle();
            Font titleFont = workbook.createFont();
            titleFont.setFontHeightInPoints((short) 18);
            titleFont.setBold(true);
            titleStyle.setFont(titleFont);
            titleStyle.setAlignment(HorizontalAlignment.CENTER);

            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setFontHeightInPoints((short) 11);
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.PALE_BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setBorderBottom(BorderStyle.THIN);
            headerStyle.setBorderTop(BorderStyle.THIN);
            headerStyle.setBorderLeft(BorderStyle.THIN);
            headerStyle.setBorderRight(BorderStyle.THIN);

            CellStyle cellStyle = workbook.createCellStyle();
            cellStyle.setBorderBottom(BorderStyle.THIN);
            cellStyle.setBorderTop(BorderStyle.THIN);
            cellStyle.setBorderLeft(BorderStyle.THIN);
            cellStyle.setBorderRight(BorderStyle.THIN);

            CellStyle errorStyle = workbook.createCellStyle();
            errorStyle.cloneStyleFrom(cellStyle);
            errorStyle.setFillForegroundColor(IndexedColors.LIGHT_ORANGE.getIndex());
            errorStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            int rowIdx = 0;

            Row titleRow = sheet.createRow(rowIdx++);
            Cell titleCell = titleRow.createCell(0);
            titleCell.setCellValue("病案首页质控报告");
            titleCell.setCellStyle(titleStyle);
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 5));

            rowIdx++;
            Row infoRow = sheet.createRow(rowIdx++);
            infoRow.createCell(0).setCellValue("记录编号");
            infoRow.createCell(1).setCellValue(scorecard.getRecordNo());
            infoRow.createCell(2).setCellValue("患者姓名");
            infoRow.createCell(3).setCellValue(scorecard.getPatientName() != null ? scorecard.getPatientName() : "");
            infoRow.createCell(4).setCellValue("手术名称");
            infoRow.createCell(5).setCellValue(scorecard.getSurgeryName() != null ? scorecard.getSurgeryName() : "");

            Row dateRow = sheet.createRow(rowIdx++);
            dateRow.createCell(0).setCellValue("生成时间");
            dateRow.createCell(1).setCellValue(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));

            rowIdx++;
            Row scoreHeaderRow = sheet.createRow(rowIdx++);
            String[] scoreHeaders = {"评分维度", "得分", "等级"};
            for (int i = 0; i < scoreHeaders.length; i++) {
                Cell cell = scoreHeaderRow.createCell(i);
                cell.setCellValue(scoreHeaders[i]);
                cell.setCellStyle(headerStyle);
            }

            Row completenessRow = sheet.createRow(rowIdx++);
            completenessRow.createCell(0).setCellValue("完整性评分");
            completenessRow.createCell(1).setCellValue(scorecard.getCompletenessScore());
            completenessRow.createCell(2).setCellValue(calculateGrade(scorecard.getCompletenessScore()));

            Row logicRow = sheet.createRow(rowIdx++);
            logicRow.createCell(0).setCellValue("逻辑一致性评分");
            logicRow.createCell(1).setCellValue(scorecard.getLogicConsistencyScore());
            logicRow.createCell(2).setCellValue(calculateGrade(scorecard.getLogicConsistencyScore()));

            Row overallRow = sheet.createRow(rowIdx++);
            overallRow.createCell(0).setCellValue("综合评分");
            overallRow.createCell(1).setCellValue(scorecard.getOverallScore());
            overallRow.createCell(2).setCellValue(scorecard.getGrade());

            rowIdx++;
            Row statHeaderRow = sheet.createRow(rowIdx++);
            String[] statHeaders = {"统计项", "值"};
            for (int i = 0; i < statHeaders.length; i++) {
                Cell cell = statHeaderRow.createCell(i);
                cell.setCellValue(statHeaders[i]);
                cell.setCellStyle(headerStyle);
            }

            addStatRow(sheet, rowIdx++, "总字段数", String.valueOf(scorecard.getTotalFields()), cellStyle);
            addStatRow(sheet, rowIdx++, "已填字段数", String.valueOf(scorecard.getFilledFields()), cellStyle);
            addStatRow(sheet, rowIdx++, "必填字段数", String.valueOf(scorecard.getRequiredFields()), cellStyle);
            addStatRow(sheet, rowIdx++, "必填已填", String.valueOf(scorecard.getRequiredFilled()), cellStyle);
            addStatRow(sheet, rowIdx++, "逻辑规则数", String.valueOf(scorecard.getLogicRuleCount()), cellStyle);
            addStatRow(sheet, rowIdx++, "逻辑通过", String.valueOf(scorecard.getLogicPassed()), cellStyle);
            addStatRow(sheet, rowIdx++, "逻辑未通过", String.valueOf(scorecard.getLogicFailed()), cellStyle);

            rowIdx++;
            Row violationHeaderRow = sheet.createRow(rowIdx++);
            String[] violationHeaders = {"规则编码", "规则名称", "类别", "严重程度", "问题描述", "关联字段"};
            for (int i = 0; i < violationHeaders.length; i++) {
                Cell cell = violationHeaderRow.createCell(i);
                cell.setCellValue(violationHeaders[i]);
                cell.setCellStyle(headerStyle);
            }

            if (scorecard.getViolations() != null) {
                for (QcCheckResult.QcViolation v : scorecard.getViolations()) {
                    Row vRow = sheet.createRow(rowIdx++);
                    boolean isError = "ERROR".equals(v.getSeverity());
                    createStyledCell(vRow, 0, v.getRuleCode(), isError ? errorStyle : cellStyle);
                    createStyledCell(vRow, 1, v.getRuleName(), isError ? errorStyle : cellStyle);
                    createStyledCell(vRow, 2, v.getCategory(), isError ? errorStyle : cellStyle);
                    createStyledCell(vRow, 3, v.getSeverity(), isError ? errorStyle : cellStyle);
                    createStyledCell(vRow, 4, v.getMessage(), isError ? errorStyle : cellStyle);
                    createStyledCell(vRow, 5, v.getRelatedFields() != null ? String.join(", ", v.getRelatedFields()) : "", isError ? errorStyle : cellStyle);
                }
            }

            rowIdx++;
            Row fieldHeaderRow = sheet.createRow(rowIdx++);
            String[] fieldHeaders = {"字段名", "字段标签", "是否必填", "是否已填", "是否合规", "问题描述"};
            for (int i = 0; i < fieldHeaders.length; i++) {
                Cell cell = fieldHeaderRow.createCell(i);
                cell.setCellValue(fieldHeaders[i]);
                cell.setCellStyle(headerStyle);
            }

            if (scorecard.getFieldChecks() != null) {
                for (QcScorecardDTO.FieldCheck fc : scorecard.getFieldChecks()) {
                    Row fcRow = sheet.createRow(rowIdx++);
                    boolean hasIssue = !fc.isValid();
                    createStyledCell(fcRow, 0, fc.getFieldName(), hasIssue ? errorStyle : cellStyle);
                    createStyledCell(fcRow, 1, fc.getFieldLabel(), hasIssue ? errorStyle : cellStyle);
                    createStyledCell(fcRow, 2, fc.isRequired() ? "是" : "否", cellStyle);
                    createStyledCell(fcRow, 3, fc.isFilled() ? "是" : "否", hasIssue && !fc.isFilled() ? errorStyle : cellStyle);
                    createStyledCell(fcRow, 4, fc.isValid() ? "是" : "否", hasIssue ? errorStyle : cellStyle);
                    createStyledCell(fcRow, 5, fc.getIssue() != null ? fc.getIssue() : "", hasIssue ? errorStyle : cellStyle);
                }
            }

            for (int i = 0; i < 6; i++) {
                sheet.autoSizeColumn(i);
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        }
    }

    private void addStatRow(Sheet sheet, int rowIdx, String label, String value, CellStyle style) {
        Row row = sheet.getRow(rowIdx);
        if (row == null) row = sheet.createRow(rowIdx);
        createStyledCell(row, 0, label, style);
        createStyledCell(row, 1, value, style);
    }

    private void createStyledCell(Row row, int col, String value, CellStyle style) {
        Cell cell = row.createCell(col);
        cell.setCellValue(value != null ? value : "");
        cell.setCellStyle(style);
    }

    private QcCheckFact buildFact(MedicalRecordHome home) {
        QcCheckFact fact = new QcCheckFact();
        fact.setRecordId(home.getRecordId());
        fact.setPatientName(home.getPatientName());
        fact.setGender(home.getGender());
        fact.setAge(home.getAge());
        fact.setHospitalNo(home.getHospitalNo());
        fact.setDepartment(home.getDepartment());
        fact.setSurgeryDate(home.getSurgeryDate() != null ? home.getSurgeryDate().toString() : null);
        fact.setSurgeryName(home.getSurgeryName());
        fact.setSurgeryLevel(home.getSurgeryLevel());
        fact.setIncisionLevel(home.getIncisionLevel());
        fact.setIncisionHealing(home.getIncisionHealing());
        fact.setAnesthesiaType(home.getAnesthesiaType());
        fact.setBloodLoss(home.getBloodLoss());
        fact.setBloodTransfusion(home.getBloodTransfusion());
        fact.setFluidInfusion(home.getFluidInfusion());
        fact.setComplications(home.getComplications());
        fact.setSurgeon(home.getSurgeon());
        fact.setChiefSurgeon(home.getChiefSurgeon());
        fact.setAssistant1(home.getAssistant1());
        fact.setAssistant2(home.getAssistant2());
        fact.setAnesthesiologist(home.getAnesthesiologist());
        fact.setScrubNurse(home.getScrubNurse());
        fact.setCirculatingNurse(home.getCirculatingNurse());
        fact.setCriticalPatient(home.getCriticalPatient());
        fact.setAdmissionDiagnosis(home.getAdmissionDiagnosis());
        fact.setDischargeDiagnosis(home.getDischargeDiagnosis());
        fact.setBedNo(home.getBedNo());
        fact.setAdmissionDate(home.getAdmissionDate() != null ? home.getAdmissionDate().toString() : null);
        return fact;
    }

    private QcCheckFact buildFactFromDTO(HomePageUpdateDTO dto) {
        QcCheckFact fact = new QcCheckFact();
        fact.setPatientName(dto.getPatientName());
        fact.setGender(dto.getGender());
        fact.setAge(dto.getAge());
        fact.setHospitalNo(dto.getHospitalNo());
        fact.setDepartment(dto.getDepartment());
        fact.setSurgeryDate(dto.getSurgeryDate() != null ? dto.getSurgeryDate().toString() : null);
        fact.setSurgeryName(dto.getSurgeryName());
        fact.setSurgeryLevel(dto.getSurgeryLevel());
        fact.setIncisionLevel(dto.getIncisionLevel());
        fact.setIncisionHealing(dto.getIncisionHealing());
        fact.setAnesthesiaType(dto.getAnesthesiaType());
        fact.setBloodLoss(dto.getBloodLoss());
        fact.setBloodTransfusion(dto.getBloodTransfusion());
        fact.setFluidInfusion(dto.getFluidInfusion());
        fact.setSurgeon(dto.getSurgeon());
        fact.setChiefSurgeon(dto.getChiefSurgeon());
        fact.setAssistant1(dto.getAssistant1());
        fact.setAssistant2(dto.getAssistant2());
        fact.setAnesthesiologist(dto.getAnesthesiologist());
        fact.setScrubNurse(dto.getScrubNurse());
        fact.setCirculatingNurse(dto.getCirculatingNurse());
        fact.setCriticalPatient(dto.getCriticalPatient());
        fact.setAdmissionDiagnosis(dto.getAdmissionDiagnosis());
        fact.setDischargeDiagnosis(dto.getDischargeDiagnosis());
        fact.setBedNo(dto.getBedNo());
        if (dto.getComplications() != null) {
            try {
                fact.setComplications(objectMapper.writeValueAsString(dto.getComplications()));
            } catch (JsonProcessingException e) {
                fact.setComplications("[]");
            }
        } else if (dto.getComplicationsStr() != null) {
            fact.setComplications(dto.getComplicationsStr());
        }
        return fact;
    }

    private int countFilledFields(MedicalRecordHome home) {
        int count = 0;
        if (StringUtils.hasText(home.getPatientName())) count++;
        if (StringUtils.hasText(home.getGender())) count++;
        if (home.getAge() != null) count++;
        if (StringUtils.hasText(home.getHospitalNo())) count++;
        if (StringUtils.hasText(home.getDepartment())) count++;
        if (StringUtils.hasText(home.getAdmissionDiagnosis())) count++;
        if (StringUtils.hasText(home.getDischargeDiagnosis())) count++;
        if (home.getSurgeryDate() != null) count++;
        if (StringUtils.hasText(home.getSurgeryName())) count++;
        if (StringUtils.hasText(home.getSurgeryCode())) count++;
        if (StringUtils.hasText(home.getSurgeryLevel())) count++;
        if (StringUtils.hasText(home.getIncisionLevel())) count++;
        if (StringUtils.hasText(home.getIncisionHealing())) count++;
        if (StringUtils.hasText(home.getAnesthesiaType())) count++;
        if (home.getBloodLoss() != null) count++;
        if (home.getBloodTransfusion() != null) count++;
        if (home.getFluidInfusion() != null) count++;
        if (StringUtils.hasText(home.getSurgeon())) count++;
        if (StringUtils.hasText(home.getChiefSurgeon())) count++;
        if (StringUtils.hasText(home.getAssistant1())) count++;
        if (StringUtils.hasText(home.getAssistant2())) count++;
        if (StringUtils.hasText(home.getAnesthesiologist())) count++;
        if (StringUtils.hasText(home.getScrubNurse())) count++;
        if (StringUtils.hasText(home.getCirculatingNurse())) count++;
        if (StringUtils.hasText(home.getComplications()) && !"[]".equals(home.getComplications())) count++;
        if (home.getCriticalPatient() != null) count++;
        if (StringUtils.hasText(home.getBedNo())) count++;
        return count;
    }

    private int countRequiredFilledFields(MedicalRecordHome home) {
        int count = 0;
        if (StringUtils.hasText(home.getPatientName())) count++;
        if (StringUtils.hasText(home.getGender())) count++;
        if (home.getAge() != null) count++;
        if (StringUtils.hasText(home.getHospitalNo())) count++;
        if (home.getSurgeryDate() != null) count++;
        if (StringUtils.hasText(home.getSurgeryName())) count++;
        if (StringUtils.hasText(home.getIncisionLevel())) count++;
        if (StringUtils.hasText(home.getAnesthesiaType())) count++;
        if (StringUtils.hasText(home.getSurgeon())) count++;
        if (StringUtils.hasText(home.getAnesthesiologist())) count++;
        return count;
    }

    private List<QcScorecardDTO.FieldCheck> buildFieldChecks(MedicalRecordHome home, QcCheckResult checkResult) {
        List<QcScorecardDTO.FieldCheck> checks = new ArrayList<>();
        Map<String, List<QcCheckResult.QcViolation>> fieldViolations = new HashMap<>();
        if (checkResult.getViolations() != null) {
            for (QcCheckResult.QcViolation v : checkResult.getViolations()) {
                if (v.getRelatedFields() != null) {
                    for (String field : v.getRelatedFields()) {
                        fieldViolations.computeIfAbsent(field, k -> new ArrayList<>()).add(v);
                    }
                }
            }
        }

        addFieldCheck(checks, "patientName", "患者姓名", home.getPatientName() != null && !home.getPatientName().isEmpty(), true, fieldViolations);
        addFieldCheck(checks, "gender", "性别", home.getGender() != null && !home.getGender().isEmpty(), true, fieldViolations);
        addFieldCheck(checks, "age", "年龄", home.getAge() != null, true, fieldViolations);
        addFieldCheck(checks, "hospitalNo", "住院号", home.getHospitalNo() != null && !home.getHospitalNo().isEmpty(), true, fieldViolations);
        addFieldCheck(checks, "department", "科室", home.getDepartment() != null && !home.getDepartment().isEmpty(), false, fieldViolations);
        addFieldCheck(checks, "admissionDiagnosis", "术前诊断", home.getAdmissionDiagnosis() != null && !home.getAdmissionDiagnosis().isEmpty(), false, fieldViolations);
        addFieldCheck(checks, "dischargeDiagnosis", "术后诊断", home.getDischargeDiagnosis() != null && !home.getDischargeDiagnosis().isEmpty(), false, fieldViolations);
        addFieldCheck(checks, "surgeryDate", "手术日期", home.getSurgeryDate() != null, true, fieldViolations);
        addFieldCheck(checks, "surgeryName", "手术名称", home.getSurgeryName() != null && !home.getSurgeryName().isEmpty(), true, fieldViolations);
        addFieldCheck(checks, "surgeryLevel", "手术等级", home.getSurgeryLevel() != null && !home.getSurgeryLevel().isEmpty(), false, fieldViolations);
        addFieldCheck(checks, "incisionLevel", "切口等级", home.getIncisionLevel() != null && !home.getIncisionLevel().isEmpty(), true, fieldViolations);
        addFieldCheck(checks, "incisionHealing", "切口愈合", home.getIncisionHealing() != null && !home.getIncisionHealing().isEmpty(), false, fieldViolations);
        addFieldCheck(checks, "anesthesiaType", "麻醉方式", home.getAnesthesiaType() != null && !home.getAnesthesiaType().isEmpty(), true, fieldViolations);
        addFieldCheck(checks, "bloodLoss", "失血量", home.getBloodLoss() != null, false, fieldViolations);
        addFieldCheck(checks, "bloodTransfusion", "输血量", home.getBloodTransfusion() != null, false, fieldViolations);
        addFieldCheck(checks, "fluidInfusion", "输液量", home.getFluidInfusion() != null, false, fieldViolations);
        addFieldCheck(checks, "surgeon", "手术医生", home.getSurgeon() != null && !home.getSurgeon().isEmpty(), true, fieldViolations);
        addFieldCheck(checks, "chiefSurgeon", "主刀医生", home.getChiefSurgeon() != null && !home.getChiefSurgeon().isEmpty(), false, fieldViolations);
        addFieldCheck(checks, "assistant1", "第一助手", home.getAssistant1() != null && !home.getAssistant1().isEmpty(), false, fieldViolations);
        addFieldCheck(checks, "anesthesiologist", "麻醉医生", home.getAnesthesiologist() != null && !home.getAnesthesiologist().isEmpty(), true, fieldViolations);
        addFieldCheck(checks, "scrubNurse", "器械护士", home.getScrubNurse() != null && !home.getScrubNurse().isEmpty(), false, fieldViolations);
        addFieldCheck(checks, "circulatingNurse", "巡回护士", home.getCirculatingNurse() != null && !home.getCirculatingNurse().isEmpty(), false, fieldViolations);
        addFieldCheck(checks, "complications", "术中并发症", home.getComplications() != null && !home.getComplications().isEmpty() && !"[]".equals(home.getComplications()), false, fieldViolations);

        return checks;
    }

    private void addFieldCheck(List<QcScorecardDTO.FieldCheck> checks, String fieldName, String label,
                               boolean filled, boolean required,
                               Map<String, List<QcCheckResult.QcViolation>> fieldViolations) {
        QcScorecardDTO.FieldCheck fc = new QcScorecardDTO.FieldCheck();
        fc.setFieldName(fieldName);
        fc.setFieldLabel(label);
        fc.setFilled(filled);
        fc.setRequired(required);

        List<QcCheckResult.QcViolation> violations = fieldViolations.get(fieldName);
        if (violations != null && !violations.isEmpty()) {
            fc.setValid(false);
            fc.setIssue(violations.stream().map(QcCheckResult.QcViolation::getMessage).reduce((a, b) -> a + "; " + b).orElse(""));
        } else {
            fc.setValid(true);
            if (required && !filled) {
                fc.setValid(false);
                fc.setIssue("必填项未填写");
            }
        }

        checks.add(fc);
    }

    private String calculateGrade(double score) {
        if (score >= 90) return "A（优秀）";
        if (score >= 80) return "B（良好）";
        if (score >= 70) return "C（合格）";
        if (score >= 60) return "D（待改进）";
        return "F（不合格）";
    }
}
