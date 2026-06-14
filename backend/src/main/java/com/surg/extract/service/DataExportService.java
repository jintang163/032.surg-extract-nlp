package com.surg.extract.service;

import com.surg.extract.common.BusinessException;
import com.surg.extract.common.ErrorCode;
import com.surg.extract.dto.*;
import com.surg.extract.entity.ExportTemplate;
import com.surg.extract.entity.MedicalRecordHome;
import com.surg.extract.entity.SurgeryRecord;
import com.surg.extract.mapper.ExportTemplateMapper;
import com.surg.extract.mapper.MedicalRecordHomeMapper;
import com.surg.extract.mapper.SurgeryRecordMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DataExportService {

    private final ExportTemplateMapper templateMapper;
    private final MedicalRecordHomeMapper homePageMapper;
    private final SurgeryRecordMapper recordMapper;
    private final ObjectMapper objectMapper;

    private static final Map<String, String> FORMAT_LABEL_MAP = Map.of(
            "EXCEL", "Excel格式",
            "JSON", "JSON格式",
            "FHIR", "HL7 FHIR R4"
    );

    private static final List<ExportFieldConfigDTO> DEFAULT_FIELD_CONFIGS = buildDefaultFieldConfigs();

    private static List<ExportFieldConfigDTO> buildDefaultFieldConfigs() {
        String[][] fields = {
                {"patientId", "患者ID", "medical_record_home", "patient_id", "Patient.identifier", "STRING", null, null, null, "1"},
                {"patientName", "患者姓名", "medical_record_home", "patient_name", "Patient.name", "STRING", null, null, null, "1"},
                {"gender", "性别", "medical_record_home", "gender", "Patient.gender", "STRING", null, null, null, "1"},
                {"age", "年龄", "medical_record_home", "age", null, "INTEGER", "岁", null, null, "1"},
                {"hospitalNo", "住院号", "medical_record_home", "hospital_no", "Encounter.identifier", "STRING", null, null, null, "1"},
                {"admissionDate", "入院日期", "medical_record_home", "admission_date", "Encounter.period.start", "DATE", null, null, null, "1"},
                {"dischargeDate", "出院日期", "medical_record_home", "discharge_date", "Encounter.period.end", "DATE", null, null, null, "1"},
                {"admissionDays", "住院天数", "medical_record_home", "admission_days", null, "INTEGER", "天", null, null, "1"},
                {"department", "科室", "medical_record_home", "department", null, "STRING", null, null, null, "1"},
                {"admissionDiagnosis", "入院诊断", "medical_record_home", "admission_diagnosis", null, "STRING", null, null, null, "1"},
                {"dischargeDiagnosis", "出院诊断", "medical_record_home", "discharge_diagnosis", null, "STRING", null, null, null, "1"},
                {"surgeryDate", "手术日期", "medical_record_home", "surgery_date", "Procedure.performedPeriod", "DATETIME", null, null, null, "1"},
                {"surgeryName", "手术名称", "medical_record_home", "surgery_name", "Procedure.code.text", "STRING", null, null, null, "1"},
                {"surgeryCode", "手术编码", "medical_record_home", "surgery_code", "Procedure.code.coding.code", "STRING", null, null, null, "1"},
                {"surgeryLevel", "手术等级", "medical_record_home", "surgery_level", null, "STRING", null, null, null, "1"},
                {"incisionLevel", "切口等级", "medical_record_home", "incision_level", null, "STRING", null, null, null, "1"},
                {"incisionHealing", "切口愈合", "medical_record_home", "incision_healing", null, "STRING", null, null, null, "1"},
                {"anesthesiaType", "麻醉方式", "medical_record_home", "anesthesia_type", null, "STRING", null, null, null, "1"},
                {"bloodLoss", "出血量", "medical_record_home", "blood_loss", null, "DECIMAL", "ml", "L", "value / 1000", "1"},
                {"bloodTransfusion", "输血量", "medical_record_home", "blood_transfusion", null, "DECIMAL", "ml", "L", "value / 1000", "1"},
                {"fluidInfusion", "输液量", "medical_record_home", "fluid_infusion", null, "DECIMAL", "ml", "L", "value / 1000", "1"},
                {"surgeon", "主刀医生", "medical_record_home", "surgeon", "Procedure.performer.actor", "STRING", null, null, null, "1"},
                {"anesthesiologist", "麻醉医生", "medical_record_home", "anesthesiologist", null, "STRING", null, null, null, "1"},
                {"scrubNurse", "洗手护士", "medical_record_home", "scrub_nurse", null, "STRING", null, null, null, "1"},
                {"circulatingNurse", "巡回护士", "medical_record_home", "circulating_nurse", null, "STRING", null, null, null, "1"},
                {"hospitalizationFee", "住院总费用", "medical_record_home", "hospitalization_fee", null, "DECIMAL", "元", null, null, "1"},
        };

        List<ExportFieldConfigDTO> configs = new ArrayList<>();
        int sort = 1;
        for (String[] f : fields) {
            ExportFieldConfigDTO dto = new ExportFieldConfigDTO();
            dto.setFieldCode(f[0]);
            dto.setFieldLabel(f[1]);
            dto.setSourceTable(f[2]);
            dto.setSourceField(f[3]);
            dto.setFhirPath(f[4]);
            dto.setDataType(f[5]);
            dto.setUnit(f[6]);
            dto.setTargetUnit(f[7]);
            dto.setConversionFormula(f[8]);
            dto.setEnabled(Integer.parseInt(f[9]));
            dto.setSortOrder(sort++);
            dto.setRequired(1);
            configs.add(dto);
        }
        return configs;
    }

    public List<ExportTemplateDTO> listTemplates(String format, String department, Integer enabled, int pageNum, int pageSize) {
        int offset = (pageNum - 1) * pageSize;
        List<ExportTemplate> list = templateMapper.selectByConditions(format, department, enabled, offset, pageSize);
        return list.stream().map(this::convertToDTO).collect(Collectors.toList());
    }

    public long countTemplates(String format, String department, Integer enabled) {
        return templateMapper.countByConditions(format, department, enabled);
    }

    public ExportTemplateDTO getTemplate(Long id) {
        ExportTemplate template = templateMapper.selectById(id);
        if (template == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "导出模板不存在");
        }
        return convertToDTO(template);
    }

    public ExportTemplateDTO getTemplateByCode(String code) {
        ExportTemplate template = templateMapper.selectByCode(code);
        if (template == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "导出模板不存在: " + code);
        }
        return convertToDTO(template);
    }

    public List<ExportFieldConfigDTO> getAvailableFields() {
        return DEFAULT_FIELD_CONFIGS;
    }

    @Transactional
    public ExportTemplateDTO createTemplate(ExportTemplateCreateDTO dto, Long userId, String userName) {
        if (StringUtils.hasText(dto.getTemplateCode())) {
            ExportTemplate existing = templateMapper.selectByCode(dto.getTemplateCode());
            if (existing != null) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "模板编码已存在");
            }
        }

        ExportTemplate template = new ExportTemplate();
        BeanUtils.copyProperties(dto, template);

        if (!CollectionUtils.isEmpty(dto.getFieldConfigs())) {
            dto.getFieldConfigs().sort(Comparator.comparing(ExportFieldConfigDTO::getSortOrder));
            try {
                template.setFieldConfigs(objectMapper.writeValueAsString(dto.getFieldConfigs()));
            } catch (Exception e) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "序列化字段配置失败");
            }
        }
        if (!CollectionUtils.isEmpty(dto.getUnitConversions())) {
            try {
                template.setUnitConversions(objectMapper.writeValueAsString(dto.getUnitConversions()));
            } catch (Exception e) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "序列化单位转换配置失败");
            }
        }
        template.setCreateUserId(userId);
        template.setCreateUserName(userName);

        if (dto.getIsDefault() != null && dto.getIsDefault() == 1) {
            templateMapper.clearDefaultForFormat(dto.getExportFormat());
        }

        templateMapper.insert(template);
        return convertToDTO(template);
    }

    @Transactional
    public ExportTemplateDTO updateTemplate(Long id, ExportTemplateCreateDTO dto) {
        ExportTemplate template = templateMapper.selectById(id);
        if (template == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "导出模板不存在");
        }

        if (StringUtils.hasText(dto.getTemplateCode()) && !dto.getTemplateCode().equals(template.getTemplateCode())) {
            ExportTemplate existing = templateMapper.selectByCode(dto.getTemplateCode());
            if (existing != null) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "模板编码已存在");
            }
        }

        BeanUtils.copyProperties(dto, template, "id", "createUserId", "createUserName", "createdTime");

        if (dto.getFieldConfigs() != null) {
            dto.getFieldConfigs().sort(Comparator.comparing(ExportFieldConfigDTO::getSortOrder));
            try {
                template.setFieldConfigs(objectMapper.writeValueAsString(dto.getFieldConfigs()));
            } catch (Exception e) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "序列化字段配置失败");
            }
        }
        if (dto.getUnitConversions() != null) {
            try {
                template.setUnitConversions(objectMapper.writeValueAsString(dto.getUnitConversions()));
            } catch (Exception e) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "序列化单位转换配置失败");
            }
        }

        if (dto.getIsDefault() != null && dto.getIsDefault() == 1
                && !dto.getExportFormat().equals(template.getExportFormat())) {
            templateMapper.clearDefaultForFormat(dto.getExportFormat());
        } else if (dto.getIsDefault() != null && dto.getIsDefault() == 1) {
            templateMapper.clearDefaultForFormat(template.getExportFormat());
        }

        templateMapper.updateById(template);
        return convertToDTO(template);
    }

    @Transactional
    public void deleteTemplate(Long id) {
        ExportTemplate template = templateMapper.selectById(id);
        if (template == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "导出模板不存在");
        }
        templateMapper.deleteById(id);
    }

    public byte[] exportToExcel(List<Long> recordIds, Long templateId) {
        ExportTemplate template = resolveTemplate(templateId, "EXCEL");
        List<ExportFieldConfigDTO> fieldConfigs = resolveFieldConfigs(template);
        List<Map<String, Object>> dataList = loadExportData(recordIds, fieldConfigs);

        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("病案首页数据");

            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle dataStyle = createDataStyle(workbook);
            CellStyle dateStyle = createDateStyle(workbook);
            CellStyle numStyle = createNumStyle(workbook);

            Row headerRow = sheet.createRow(0);
            int colIdx = 0;
            for (ExportFieldConfigDTO field : fieldConfigs) {
                if (field.getEnabled() != null && field.getEnabled() == 0) continue;
                Cell cell = headerRow.createCell(colIdx++);
                String label = field.getFieldLabel();
                if (StringUtils.hasText(field.getTargetUnit()) && !field.getTargetUnit().equals(field.getUnit())) {
                    label += "(" + field.getTargetUnit() + ")";
                } else if (StringUtils.hasText(field.getUnit())) {
                    label += "(" + field.getUnit() + ")";
                }
                cell.setCellValue(label);
                cell.setCellStyle(headerStyle);
            }

            int rowIdx = 1;
            for (Map<String, Object> data : dataList) {
                Row row = sheet.createRow(rowIdx++);
                colIdx = 0;
                for (ExportFieldConfigDTO field : fieldConfigs) {
                    if (field.getEnabled() != null && field.getEnabled() == 0) continue;
                    Cell cell = row.createCell(colIdx++);
                    Object value = data.get(field.getFieldCode());
                    setCellValue(cell, value, field, dataStyle, dateStyle, numStyle);
                }
            }

            for (int i = 0; i < fieldConfigs.size(); i++) {
                if (fieldConfigs.get(i).getEnabled() != null && fieldConfigs.get(i).getEnabled() == 0) continue;
                sheet.autoSizeColumn(i);
                int colWidth = sheet.getColumnWidth(i);
                sheet.setColumnWidth(i, Math.min(colWidth + 1000, 15000));
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            log.error("生成Excel失败", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "生成Excel失败");
        }
    }

    public List<StandardHomePageDTO> exportToJson(List<Long> recordIds, Long templateId) {
        ExportTemplate template = resolveTemplate(templateId, "JSON");
        List<Map<String, Object>> dataList = loadExportData(recordIds, resolveFieldConfigs(template));

        List<StandardHomePageDTO> result = new ArrayList<>();
        for (Map<String, Object> data : dataList) {
            StandardHomePageDTO dto = new StandardHomePageDTO();
            try {
                dto = objectMapper.convertValue(data, StandardHomePageDTO.class);
                applyUnitConversions(dto, template);
                dto.setExtractTime(LocalDateTime.now());
            } catch (Exception e) {
                log.warn("转换StandardHomePageDTO失败", e);
            }
            result.add(dto);
        }
        return result;
    }

    public FhirBundleDTO exportToFhir(List<Long> recordIds, Long templateId) {
        ExportTemplate template = resolveTemplate(templateId, "FHIR");
        List<Map<String, Object>> dataList = loadExportData(recordIds, resolveFieldConfigs(template));

        FhirBundleDTO bundle = new FhirBundleDTO();
        bundle.setId(UUID.randomUUID().toString());
        bundle.setTimestamp(LocalDateTime.now().toString());
        List<FhirBundleEntry> entries = new ArrayList<>();

        for (Map<String, Object> data : dataList) {
            String patientId = getStr(data, "patientId");
            String recordId = getStr(data, "recordNo");

            FhirPatient patient = new FhirPatient();
            patient.setId(patientId != null ? patientId : UUID.randomUUID().toString());
            patient.setGender(getStr(data, "gender"));
            patient.setBirthDate(getStr(data, "admissionDate") != null
                    ? getStr(data, "admissionDate") : null);
            patient.setName(List.of(buildName(getStr(data, "patientName"))));
            patient.setIdentifier(buildIdentifiers(patientId, getStr(data, "hospitalNo"), getStr(data, "idCardNo")));
            entries.add(buildEntry("Patient/" + patient.getId(), patient));

            FhirEncounter encounter = new FhirEncounter();
            encounter.setId(recordId != null ? recordId : UUID.randomUUID().toString());
            encounter.setSubject(buildReference("Patient/" + patient.getId(), getStr(data, "patientName")));
            FhirPeriod period = new FhirPeriod();
            period.setStart(getStr(data, "admissionDate"));
            period.setEnd(getStr(data, "dischargeDate"));
            encounter.setPeriod(period);
            entries.add(buildEntry("Encounter/" + encounter.getId(), encounter));

            if (StringUtils.hasText(getStr(data, "surgeryName"))) {
                FhirProcedure procedure = new FhirProcedure();
                procedure.setId(UUID.randomUUID().toString());
                procedure.setSubject(buildReference("Patient/" + patient.getId(), getStr(data, "patientName")));
                procedure.setEncounter(buildReference("Encounter/" + encounter.getId(), null));

                FhirCodeableConcept code = new FhirCodeableConcept();
                code.setText(getStr(data, "surgeryName"));
                if (StringUtils.hasText(getStr(data, "surgeryCode"))) {
                    FhirCoding coding = new FhirCoding();
                    coding.setSystem("http://www.nhc.gov.cn/icd-9-cm-3");
                    coding.setCode(getStr(data, "surgeryCode"));
                    coding.setDisplay(getStr(data, "surgeryName"));
                    code.setCoding(List.of(coding));
                }
                procedure.setCode(code);

                FhirPeriod procPeriod = new FhirPeriod();
                procPeriod.setStart(getStr(data, "surgeryDate"));
                procedure.setPerformedPeriod(procPeriod);

                if (StringUtils.hasText(getStr(data, "surgeon"))) {
                    FhirProcedurePerformer performer = new FhirProcedurePerformer();
                    performer.setActor(buildReference(null, getStr(data, "surgeon")));
                    procedure.setPerformer(List.of(performer));
                }

                entries.add(buildEntry("Procedure/" + procedure.getId(), procedure));
            }
        }

        bundle.setEntry(entries);
        return bundle;
    }

    public StandardHomePageDTO getStandardHomePage(Long recordId) {
        List<Long> recordIds = List.of(recordId);
        List<StandardHomePageDTO> list = exportToJson(recordIds, null);
        if (list.isEmpty()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "病案首页数据不存在");
        }
        return list.get(0);
    }

    private ExportTemplate resolveTemplate(Long templateId, String format) {
        if (templateId != null) {
            ExportTemplate template = templateMapper.selectById(templateId);
            if (template == null) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "导出模板不存在");
            }
            return template;
        }
        ExportTemplate template = templateMapper.selectDefaultByFormat(format);
        if (template == null) {
            template = new ExportTemplate();
            template.setExportFormat(format);
        }
        return template;
    }

    private List<ExportFieldConfigDTO> resolveFieldConfigs(ExportTemplate template) {
        if (StringUtils.hasText(template.getFieldConfigs())) {
            try {
                List<ExportFieldConfigDTO> configs = objectMapper.readValue(
                        template.getFieldConfigs(),
                        new TypeReference<List<ExportFieldConfigDTO>>() {}
                );
                configs.sort(Comparator.comparing(ExportFieldConfigDTO::getSortOrder));
                return configs;
            } catch (Exception e) {
                log.warn("解析模板字段配置失败，使用默认配置", e);
            }
        }
        return DEFAULT_FIELD_CONFIGS;
    }

    private List<Map<String, Object>> loadExportData(List<Long> recordIds, List<ExportFieldConfigDTO> fieldConfigs) {
        List<Map<String, Object>> result = new ArrayList<>();

        for (Long recordId : recordIds) {
            MedicalRecordHome home = homePageMapper.selectByRecordId(recordId);
            SurgeryRecord record = recordMapper.selectById(recordId);

            if (home == null && record == null) continue;

            Map<String, Object> data = new LinkedHashMap<>();
            for (ExportFieldConfigDTO field : fieldConfigs) {
                Object value = getFieldValue(field, home, record);
                if (value != null) {
                    value = applyValueConversion(value, field);
                }
                data.put(field.getFieldCode(), value);
            }
            if (record != null) {
                data.put("recordNo", record.getRecordNo());
            }
            result.add(data);
        }

        return result;
    }

    private Object getFieldValue(ExportFieldConfigDTO field, MedicalRecordHome home, SurgeryRecord record) {
        if (home == null) return null;
        return switch (field.getFieldCode()) {
            case "patientId" -> home.getPatientId();
            case "patientName" -> home.getPatientName();
            case "gender" -> home.getGender();
            case "age" -> home.getAge();
            case "idCardNo" -> home.getIdCardNo();
            case "hospitalNo" -> home.getHospitalNo();
            case "admissionDate" -> home.getAdmissionDate();
            case "dischargeDate" -> home.getDischargeDate();
            case "admissionDays" -> home.getAdmissionDays();
            case "department" -> home.getDepartment();
            case "bedNo" -> home.getBedNo();
            case "admissionDiagnosis" -> home.getAdmissionDiagnosis();
            case "dischargeDiagnosis" -> home.getDischargeDiagnosis();
            case "surgeryDate" -> home.getSurgeryDate();
            case "surgeryName" -> home.getSurgeryName();
            case "surgeryCode" -> home.getSurgeryCode();
            case "surgeryLevel" -> home.getSurgeryLevel();
            case "incisionLevel" -> home.getIncisionLevel();
            case "incisionHealing" -> home.getIncisionHealing();
            case "anesthesiaType" -> home.getAnesthesiaType();
            case "anesthesiaCode" -> home.getAnesthesiaCode();
            case "bloodLoss" -> home.getBloodLoss();
            case "bloodTransfusion" -> home.getBloodTransfusion();
            case "fluidInfusion" -> home.getFluidInfusion();
            case "surgeon" -> home.getSurgeon();
            case "chiefSurgeon" -> home.getChiefSurgeon();
            case "assistant1" -> home.getAssistant1();
            case "assistant2" -> home.getAssistant2();
            case "anesthesiologist" -> home.getAnesthesiologist();
            case "scrubNurse" -> home.getScrubNurse();
            case "circulatingNurse" -> home.getCirculatingNurse();
            case "criticalPatient" -> home.getCriticalPatient();
            case "hospitalizationFee" -> home.getHospitalizationFee();
            case "surgeryFee" -> home.getSurgeryFee();
            case "anesthesiaFee" -> home.getAnesthesiaFee();
            case "drugFee" -> home.getDrugFee();
            case "examFee" -> home.getExamFee();
            case "treatmentFee" -> home.getTreatmentFee();
            case "bedFee" -> home.getBedFee();
            case "otherFee" -> home.getOtherFee();
            case "status" -> home.getStatus();
            default -> null;
        };
    }

    private Object applyValueConversion(Object value, ExportFieldConfigDTO field) {
        if (value == null || !StringUtils.hasText(field.getConversionFormula())) return value;
        if (!(value instanceof BigDecimal) && !(value instanceof Number)) return value;

        try {
            double numericValue = ((Number) value).doubleValue();
            String formula = field.getConversionFormula().trim();
            double result = numericValue;

            if ("value / 1000".equals(formula) || "value/1000".equals(formula)) {
                result = numericValue / 1000.0;
            } else if ("value * 1000".equals(formula) || "value*1000".equals(formula)) {
                result = numericValue * 1000.0;
            }

            return BigDecimal.valueOf(result).setScale(field.getDataType() != null && field.getDataType().contains("DECIMAL") ? 2 : 0, RoundingMode.HALF_UP);
        } catch (Exception e) {
            log.warn("单位转换失败: field={}, value={}, formula={}", field.getFieldCode(), value, field.getConversionFormula());
            return value;
        }
    }

    private void applyUnitConversions(StandardHomePageDTO dto, ExportTemplate template) {
        if (!StringUtils.hasText(template.getUnitConversions())) return;
        try {
            List<UnitConversionDTO> conversions = objectMapper.readValue(
                    template.getUnitConversions(),
                    new TypeReference<List<UnitConversionDTO>>() {}
            );
            for (UnitConversionDTO conv : conversions) {
                applyUnitConversion(dto, conv);
            }
        } catch (Exception e) {
            log.warn("应用单位转换失败", e);
        }
    }

    private void applyUnitConversion(StandardHomePageDTO dto, UnitConversionDTO conv) {
        try {
            var field = dto.getClass().getDeclaredField(conv.getFieldCode());
            field.setAccessible(true);
            Object value = field.get(dto);
            if (value instanceof BigDecimal num && conv.getMultiplyFactor() != null) {
                BigDecimal result = num.multiply(BigDecimal.valueOf(conv.getMultiplyFactor()));
                if (conv.getAddOffset() != null) {
                    result = result.add(BigDecimal.valueOf(conv.getAddOffset()));
                }
                if (conv.getDecimalPlaces() != null) {
                    result = result.setScale(conv.getDecimalPlaces(), RoundingMode.HALF_UP);
                }
                field.set(dto, result);
            }
        } catch (Exception e) {
            log.warn("单位转换失败: field={}", conv.getFieldCode(), e);
        }
    }

    private void setCellValue(Cell cell, Object value, ExportFieldConfigDTO field,
                             CellStyle dataStyle, CellStyle dateStyle, CellStyle numStyle) {
        if (value == null) {
            cell.setCellValue("");
            cell.setCellStyle(dataStyle);
            return;
        }

        if (value instanceof LocalDate dt) {
            cell.setCellValue(Date.from(dt.atStartOfDay(ZoneId.systemDefault()).toInstant()));
            cell.setCellStyle(dateStyle);
        } else if (value instanceof LocalDateTime dt) {
            cell.setCellValue(Date.from(dt.atZone(ZoneId.systemDefault()).toInstant()));
            cell.setCellStyle(dateStyle);
        } else if (value instanceof BigDecimal num) {
            cell.setCellValue(num.doubleValue());
            cell.setCellStyle(numStyle);
        } else if (value instanceof Number num) {
            cell.setCellValue(num.doubleValue());
            cell.setCellStyle(numStyle);
        } else {
            cell.setCellValue(value.toString());
            cell.setCellStyle(dataStyle);
        }
    }

    private CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 11);
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    private CellStyle createDataStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setAlignment(HorizontalAlignment.LEFT);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    private CellStyle createDateStyle(Workbook workbook) {
        CellStyle style = createDataStyle(workbook);
        CreationHelper helper = workbook.getCreationHelper();
        style.setDataFormat(helper.createDataFormat().getFormat("yyyy-MM-dd"));
        return style;
    }

    private CellStyle createNumStyle(Workbook workbook) {
        CellStyle style = createDataStyle(workbook);
        CreationHelper helper = workbook.getCreationHelper();
        style.setDataFormat(helper.createDataFormat().getFormat("#,##0.00"));
        style.setAlignment(HorizontalAlignment.RIGHT);
        return style;
    }

    private String getStr(Map<String, Object> data, String key) {
        Object v = data.get(key);
        if (v == null) return null;
        if (v instanceof LocalDateTime dt) return dt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        if (v instanceof LocalDate d) return d.format(DateTimeFormatter.ISO_LOCAL_DATE);
        return v.toString();
    }

    private FhirHumanName buildName(String name) {
        FhirHumanName n = new FhirHumanName();
        if (name != null && name.length() >= 2) {
            n.setFamily(name.substring(0, 1));
            n.setGiven(List.of(name.substring(1)));
        } else {
            n.setFamily(name != null ? name : "");
        }
        return n;
    }

    private List<FhirIdentifier> buildIdentifiers(String patientId, String hospitalNo, String idCardNo) {
        List<FhirIdentifier> identifiers = new ArrayList<>();
        if (StringUtils.hasText(patientId)) {
            FhirIdentifier id = new FhirIdentifier();
            id.setSystem("urn:oid:1.2.840.114350.1.13");
            id.setValue(patientId);
            id.setUse("official");
            identifiers.add(id);
        }
        if (StringUtils.hasText(hospitalNo)) {
            FhirIdentifier id = new FhirIdentifier();
            id.setSystem("urn:oid:1.2.840.114350.1.13.hospital");
            id.setValue(hospitalNo);
            id.setUse("usual");
            identifiers.add(id);
        }
        if (StringUtils.hasText(idCardNo)) {
            FhirIdentifier id = new FhirIdentifier();
            id.setSystem("urn:oid:1.2.156.112515");
            id.setValue(idCardNo);
            id.setUse("official");
            identifiers.add(id);
        }
        return identifiers;
    }

    private FhirReference buildReference(String ref, String display) {
        FhirReference r = new FhirReference();
        r.setReference(ref);
        r.setDisplay(display);
        return r;
    }

    private FhirBundleEntry buildEntry(String url, FhirResource resource) {
        FhirBundleEntry entry = new FhirBundleEntry();
        entry.setFullUrl(url);
        entry.setResource(resource);
        return entry;
    }

    private ExportTemplateDTO convertToDTO(ExportTemplate template) {
        ExportTemplateDTO dto = new ExportTemplateDTO();
        BeanUtils.copyProperties(template, dto);
        dto.setExportFormatLabel(FORMAT_LABEL_MAP.getOrDefault(template.getExportFormat(), template.getExportFormat()));

        if (StringUtils.hasText(template.getFieldConfigs())) {
            try {
                dto.setFieldConfigs(objectMapper.readValue(
                        template.getFieldConfigs(),
                        new TypeReference<List<ExportFieldConfigDTO>>() {}
                ));
            } catch (Exception e) {
                log.warn("解析字段配置失败", e);
            }
        }
        if (StringUtils.hasText(template.getUnitConversions())) {
            try {
                dto.setUnitConversions(objectMapper.readValue(
                        template.getUnitConversions(),
                        new TypeReference<List<UnitConversionDTO>>() {}
                ));
            } catch (Exception e) {
                log.warn("解析单位转换配置失败", e);
            }
        }
        return dto;
    }
}
