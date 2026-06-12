package com.surg.extract.service;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.surg.extract.common.BusinessException;
import com.surg.extract.common.ErrorCode;
import com.surg.extract.dto.*;
import com.surg.extract.entity.CustomFieldSample;
import com.surg.extract.entity.DepartmentCustomField;
import com.surg.extract.entity.MedicalRecordHomeExt;
import com.surg.extract.feign.NlpServiceClient;
import com.surg.extract.mapper.CustomFieldSampleMapper;
import com.surg.extract.mapper.DepartmentCustomFieldMapper;
import com.surg.extract.mapper.MedicalRecordHomeExtMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DepartmentCustomFieldService {

    private final DepartmentCustomFieldMapper fieldMapper;
    private final CustomFieldSampleMapper sampleMapper;
    private final MedicalRecordHomeExtMapper homeExtMapper;
    private final NlpServiceClient nlpClient;
    private final ObjectMapper objectMapper;

    public List<CustomFieldDTO> getFieldsByDepartment(String department) {
        List<DepartmentCustomField> fields = fieldMapper.selectEnabledByDepartment(department);
        return fields.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    public List<CustomFieldDTO> getAllFieldsByDepartment(String department) {
        List<DepartmentCustomField> fields = fieldMapper.selectByDepartment(department);
        return fields.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    public CustomFieldDTO getField(Long id) {
        DepartmentCustomField field = fieldMapper.selectById(id);
        if (field == null || field.getDeleted() == 1) {
            throw new BusinessException(ErrorCode.FIELD_NOT_FOUND);
        }
        return convertToDTO(field);
    }

    @Transactional(rollbackFor = Exception.class)
    public CustomFieldDTO createField(CustomFieldCreateDTO dto) {
        DepartmentCustomField existing = fieldMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<DepartmentCustomField>()
                        .eq(DepartmentCustomField::getDepartment, dto.getDepartment())
                        .eq(DepartmentCustomField::getFieldCode, dto.getFieldCode())
                        .eq(DepartmentCustomField::getDeleted, 0));
        if (existing != null) {
            throw new BusinessException(ErrorCode.FIELD_CODE_DUPLICATE);
        }

        DepartmentCustomField field = new DepartmentCustomField();
        BeanUtils.copyProperties(dto, field);
        field.setEnumOptions(toJson(dto.getEnumOptions()));
        field.setEnabled(1);
        field.setModelStatus("NOT_TRAINED");
        field.setSampleCount(0);
        if (field.getSortOrder() == null) {
            field.setSortOrder(0);
        }
        if (field.getRequired() == null) {
            field.setRequired(0);
        }
        if (field.getNerEnabled() == null) {
            field.setNerEnabled(1);
        }

        fieldMapper.insert(field);
        log.info("创建自定义字段: department={}, fieldCode={}", dto.getDepartment(), dto.getFieldCode());
        return convertToDTO(field);
    }

    @Transactional(rollbackFor = Exception.class)
    public CustomFieldDTO updateField(Long id, CustomFieldUpdateDTO dto) {
        DepartmentCustomField field = fieldMapper.selectById(id);
        if (field == null || field.getDeleted() == 1) {
            throw new BusinessException(ErrorCode.FIELD_NOT_FOUND);
        }

        if (dto.getFieldName() != null) {
            field.setFieldName(dto.getFieldName());
        }
        if (dto.getFieldType() != null) {
            field.setFieldType(dto.getFieldType());
        }
        if (dto.getUnit() != null) {
            field.setUnit(dto.getUnit());
        }
        if (dto.getEnumOptions() != null) {
            field.setEnumOptions(toJson(dto.getEnumOptions()));
        }
        if (dto.getDescription() != null) {
            field.setDescription(dto.getDescription());
        }
        if (dto.getSortOrder() != null) {
            field.setSortOrder(dto.getSortOrder());
        }
        if (dto.getRequired() != null) {
            field.setRequired(dto.getRequired());
        }
        if (dto.getNerEnabled() != null) {
            field.setNerEnabled(dto.getNerEnabled());
        }
        if (dto.getEnabled() != null) {
            field.setEnabled(dto.getEnabled());
        }

        fieldMapper.updateById(field);
        log.info("更新自定义字段: id={}, fieldCode={}", id, field.getFieldCode());
        return convertToDTO(field);
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteField(Long id) {
        DepartmentCustomField field = fieldMapper.selectById(id);
        if (field == null || field.getDeleted() == 1) {
            throw new BusinessException(ErrorCode.FIELD_NOT_FOUND);
        }
        field.setDeleted(1);
        fieldMapper.updateById(field);
        log.info("删除自定义字段: id={}, fieldCode={}", id, field.getFieldCode());
    }

    public List<CustomFieldSample> getSamplesByFieldId(Long fieldId) {
        return sampleMapper.selectByFieldId(fieldId);
    }

    @Transactional(rollbackFor = Exception.class)
    public CustomFieldSample addSample(CustomFieldSampleCreateDTO dto) {
        DepartmentCustomField field = fieldMapper.selectById(dto.getFieldId());
        if (field == null || field.getDeleted() == 1) {
            throw new BusinessException(ErrorCode.FIELD_NOT_FOUND);
        }

        CustomFieldSample sample = new CustomFieldSample();
        BeanUtils.copyProperties(dto, sample);
        if (sample.getSource() == null) {
            sample.setSource("MANUAL");
        }
        sampleMapper.insert(sample);

        int count = sampleMapper.countByFieldId(dto.getFieldId());
        field.setSampleCount(count);
        fieldMapper.updateById(field);

        log.info("添加训练样本: fieldId={}, entityValue={}", dto.getFieldId(), dto.getEntityValue());
        return sample;
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteSample(Long sampleId) {
        CustomFieldSample sample = sampleMapper.selectById(sampleId);
        if (sample == null || sample.getDeleted() == 1) {
            throw new BusinessException(ErrorCode.SAMPLE_NOT_FOUND);
        }
        sample.setDeleted(1);
        sampleMapper.updateById(sample);

        int count = sampleMapper.countByFieldId(sample.getFieldId());
        DepartmentCustomField field = fieldMapper.selectById(sample.getFieldId());
        if (field != null) {
            field.setSampleCount(count);
            fieldMapper.updateById(field);
        }

        log.info("删除训练样本: sampleId={}", sampleId);
    }

    @Transactional(rollbackFor = Exception.class)
    public String trainModel(Long fieldId) {
        DepartmentCustomField field = fieldMapper.selectById(fieldId);
        if (field == null || field.getDeleted() == 1) {
            throw new BusinessException(ErrorCode.FIELD_NOT_FOUND);
        }

        int sampleCount = sampleMapper.countByFieldId(fieldId);
        if (sampleCount < 5) {
            throw new BusinessException(ErrorCode.NOT_ENOUGH_SAMPLES);
        }

        List<CustomFieldSample> samples = sampleMapper.selectByFieldId(fieldId);
        List<Map<String, Object>> sampleList = samples.stream()
                .map(s -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("text", s.getText());
                    map.put("entity_value", s.getEntityValue());
                    map.put("start_pos", s.getStartPos());
                    map.put("end_pos", s.getEndPos());
                    return map;
                })
                .collect(Collectors.toList());

        try {
            field.setModelStatus("TRAINING");
            fieldMapper.updateById(field);

            Map<String, Object> request = new HashMap<>();
            request.put("field_id", fieldId);
            request.put("department", field.getDepartment());
            request.put("field_code", field.getFieldCode());
            request.put("entity_type", "CUSTOM_" + field.getFieldCode().toUpperCase());
            request.put("samples", sampleList);

            nlpClient.trainCustomNer(request);

            log.info("开始训练自定义NER模型: fieldId={}, fieldCode={}", fieldId, field.getFieldCode());
            return "训练已启动，请稍后查看训练状态";
        } catch (Exception e) {
            log.error("启动训练失败: {}", e.getMessage());
            field.setModelStatus("FAILED");
            fieldMapper.updateById(field);
            throw new BusinessException(ErrorCode.MODEL_TRAIN_FAILED);
        }
    }

    public Map<String, Object> getModelStatus(Long fieldId) {
        DepartmentCustomField field = fieldMapper.selectById(fieldId);
        if (field == null || field.getDeleted() == 1) {
            throw new BusinessException(ErrorCode.FIELD_NOT_FOUND);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("fieldId", fieldId);
        result.put("modelStatus", field.getModelStatus());
        result.put("modelVersion", field.getModelVersion());
        result.put("lastTrainTime", field.getLastTrainTime());
        result.put("sampleCount", field.getSampleCount());
        return result;
    }

    public List<MedicalRecordHomeExt> getHomeExtFields(Long recordId) {
        return homeExtMapper.selectByRecordId(recordId);
    }

    @Transactional(rollbackFor = Exception.class)
    public void saveHomeExtField(Long recordId, Long fieldId, String value, String source, BigDecimal confidence) {
        DepartmentCustomField field = fieldMapper.selectById(fieldId);
        if (field == null || field.getDeleted() == 1) {
            throw new BusinessException(ErrorCode.FIELD_NOT_FOUND);
        }

        MedicalRecordHomeExt existing = homeExtMapper.selectByRecordIdAndFieldId(recordId, fieldId);
        if (existing != null) {
            existing.setFieldValue(value);
            existing.setSource(source);
            existing.setConfidence(confidence);
            homeExtMapper.updateById(existing);
        } else {
            MedicalRecordHomeExt ext = new MedicalRecordHomeExt();
            ext.setRecordId(recordId);
            ext.setFieldId(fieldId);
            ext.setFieldCode(field.getFieldCode());
            ext.setFieldName(field.getFieldName());
            ext.setFieldValue(value);
            ext.setSource(source);
            ext.setConfidence(confidence);
            ext.setVerified(0);
            homeExtMapper.insert(ext);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void verifyHomeExtField(Long extId, Long userId, String userName) {
        MedicalRecordHomeExt ext = homeExtMapper.selectById(extId);
        if (ext == null) {
            throw new BusinessException(ErrorCode.FIELD_NOT_FOUND);
        }
        ext.setVerified(1);
        ext.setVerifiedBy(userId);
        ext.setVerifiedTime(LocalDateTime.now());
        homeExtMapper.updateById(ext);
    }

    public Map<String, Object> extractCustomEntities(String text, String department) {
        List<DepartmentCustomField> nerFields = fieldMapper.selectNerEnabledByDepartment(department);
        if (nerFields.isEmpty()) {
            Map<String, Object> result = new HashMap<>();
            result.put("entities", Collections.emptyList());
            result.put("success", true);
            return result;
        }

        List<String> entityTypes = nerFields.stream()
                .map(f -> "CUSTOM_" + f.getFieldCode().toUpperCase())
                .collect(Collectors.toList());

        try {
            NlpNerRequest request = new NlpNerRequest();
            request.setText(text);
            request.setEntityTypes(entityTypes);
            request.setDepartment(department);
            NlpNerResponse response = nlpClient.extractEntities(request);

            if (response != null && Boolean.TRUE.equals(response.getSuccess())) {
                Map<String, Object> result = new HashMap<>();
                result.put("entities", response.getEntities());
                result.put("success", true);
                return result;
            }
        } catch (Exception e) {
            log.warn("自定义字段NER抽取失败: {}", e.getMessage());
        }

        Map<String, Object> result = new HashMap<>();
        result.put("entities", Collections.emptyList());
        result.put("success", false);
        result.put("error_message", "抽取失败");
        return result;
    }

    private CustomFieldDTO convertToDTO(DepartmentCustomField field) {
        CustomFieldDTO dto = new CustomFieldDTO();
        BeanUtils.copyProperties(field, dto);
        if (StrUtil.isNotBlank(field.getEnumOptions())) {
            try {
                dto.setEnumOptions(objectMapper.readValue(field.getEnumOptions(),
                        new TypeReference<List<String>>() {}));
            } catch (Exception e) {
                log.warn("解析枚举选项失败: {}", e.getMessage());
            }
        }
        if (field.getLastTrainTime() != null) {
            dto.setLastTrainTime(field.getLastTrainTime().toString());
        }
        return dto;
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return null;
        }
    }

    public List<CustomFieldDTO> getNerEnabledFields(String department) {
        List<DepartmentCustomField> fields = fieldMapper.selectNerEnabledByDepartment(department);
        return fields.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    public Map<String, DepartmentCustomField> getFieldCodeMap(String department) {
        List<DepartmentCustomField> fields = fieldMapper.selectEnabledByDepartment(department);
        Map<String, DepartmentCustomField> map = new HashMap<>();
        for (DepartmentCustomField field : fields) {
            map.put(field.getFieldCode(), field);
        }
        return map;
    }
}
