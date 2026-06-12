package com.surg.extract.service;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.surg.extract.common.BusinessException;
import com.surg.extract.common.ErrorCode;
import com.surg.extract.dto.*;
import com.surg.extract.entity.SurgeryEntity;
import com.surg.extract.entity.SurgeryTemplate;
import com.surg.extract.entity.SurgeryTemplateVersion;
import com.surg.extract.mapper.SurgeryEntityMapper;
import com.surg.extract.mapper.SurgeryTemplateMapper;
import com.surg.extract.mapper.SurgeryTemplateVersionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SurgeryTemplateService {

    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\$\\{([^}]+)}");

    private final SurgeryTemplateMapper templateMapper;
    private final SurgeryTemplateVersionMapper versionMapper;
    private final SurgeryEntityMapper entityMapper;
    private final ObjectMapper objectMapper;

    public PageResult<SurgeryTemplateDTO> queryTemplates(String templateName, String surgeryType,
                                                         String department, String status,
                                                         Integer pageNum, Integer pageSize) {
        int offset = (pageNum - 1) * pageSize;
        List<SurgeryTemplate> templates = templateMapper.selectByConditions(
                templateName, surgeryType, department, status, offset, pageSize);
        Long total = templateMapper.countByConditions(templateName, surgeryType, department, status);

        List<SurgeryTemplateDTO> dtoList = templates.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());

        return PageResult.of(dtoList, total, pageNum, pageSize);
    }

    public SurgeryTemplateDTO getTemplate(Long id) {
        SurgeryTemplate template = templateMapper.selectById(id);
        if (template == null || template.getDeleted() == 1) {
            throw new BusinessException(ErrorCode.TEMPLATE_NOT_FOUND);
        }
        return convertToDTO(template);
    }

    public List<SurgeryTemplateDTO> getAvailableTemplates(String surgeryType, String department) {
        List<SurgeryTemplate> templates = templateMapper.selectAvailableTemplates(surgeryType, department);
        return templates.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    @Transactional(rollbackFor = Exception.class)
    public SurgeryTemplateDTO createTemplate(SurgeryTemplateCreateDTO dto) {
        SurgeryTemplate existing = templateMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SurgeryTemplate>()
                        .eq(SurgeryTemplate::getTemplateCode, dto.getTemplateCode())
                        .eq(SurgeryTemplate::getDeleted, 0));
        if (existing != null) {
            throw new BusinessException(ErrorCode.TEMPLATE_CODE_DUPLICATE);
        }

        SurgeryTemplate template = new SurgeryTemplate();
        BeanUtils.copyProperties(dto, template);
        template.setPlaceholders(toJson(dto.getPlaceholders()));
        template.setCurrentVersion(1);
        if (dto.getStatus() != null && !dto.getStatus().isEmpty()) {
            template.setStatus(dto.getStatus());
        } else {
            template.setStatus("DRAFT");
        }
        template.setIsDefault(0);
        template.setSortOrder(dto.getSortOrder() != null ? dto.getSortOrder() : 0);
        template.setUseCount(0);
        template.setCreatedUserId(1L);
        template.setCreatedUserName("当前用户");
        template.setUpdatedUserId(1L);
        template.setUpdatedUserName("当前用户");
        template.setDeleted(0);

        templateMapper.insert(template);

        SurgeryTemplateVersion version = new SurgeryTemplateVersion();
        version.setTemplateId(template.getId());
        version.setVersionNo(1);
        version.setTemplateContent(template.getTemplateContent());
        version.setPlaceholders(template.getPlaceholders());
        version.setChangeLog(dto.getChangeLog() != null ? dto.getChangeLog() : "初始版本");
        version.setIsCurrent(1);
        version.setCreatedUserId(1L);
        version.setCreatedUserName("当前用户");
        versionMapper.insert(version);

        log.info("创建手术模板: id={}, code={}", template.getId(), template.getTemplateCode());
        return convertToDTO(template);
    }

    @Transactional(rollbackFor = Exception.class)
    public SurgeryTemplateDTO updateTemplate(Long id, SurgeryTemplateUpdateDTO dto) {
        SurgeryTemplate template = templateMapper.selectById(id);
        if (template == null || template.getDeleted() == 1) {
            throw new BusinessException(ErrorCode.TEMPLATE_NOT_FOUND);
        }

        boolean contentChanged = !Objects.equals(template.getTemplateContent(), dto.getTemplateContent())
                || !Objects.equals(template.getPlaceholders(), toJson(dto.getPlaceholders()));

        if (dto.getTemplateName() != null) template.setTemplateName(dto.getTemplateName());
        if (dto.getSurgeryType() != null) template.setSurgeryType(dto.getSurgeryType());
        if (dto.getSurgeryCode() != null) template.setSurgeryCode(dto.getSurgeryCode());
        if (dto.getDepartment() != null) template.setDepartment(dto.getDepartment());
        if (dto.getTemplateContent() != null) template.setTemplateContent(dto.getTemplateContent());
        if (dto.getPlaceholders() != null) template.setPlaceholders(toJson(dto.getPlaceholders()));
        if (dto.getStatus() != null) template.setStatus(dto.getStatus());
        if (dto.getIsDefault() != null) template.setIsDefault(dto.getIsDefault());
        if (dto.getDescription() != null) template.setDescription(dto.getDescription());
        if (dto.getTags() != null) template.setTags(dto.getTags());
        if (dto.getSortOrder() != null) template.setSortOrder(dto.getSortOrder());
        template.setUpdatedUserId(1L);
        template.setUpdatedUserName("当前用户");

        templateMapper.updateById(template);

        if (contentChanged) {
            SurgeryTemplateVersion latest = versionMapper.selectLatestVersion(id);
            int newVersionNo = (latest != null ? latest.getVersionNo() : 0) + 1;

            versionMapper.clearCurrentVersion(id);

            SurgeryTemplateVersion newVersion = new SurgeryTemplateVersion();
            newVersion.setTemplateId(id);
            newVersion.setVersionNo(newVersionNo);
            newVersion.setTemplateContent(template.getTemplateContent());
            newVersion.setPlaceholders(template.getPlaceholders());
            newVersion.setChangeLog(dto.getChangeLog() != null ? dto.getChangeLog() : "更新模板内容");
            newVersion.setIsCurrent(1);
            newVersion.setCreatedUserId(1L);
            newVersion.setCreatedUserName("当前用户");
            versionMapper.insert(newVersion);

            template.setCurrentVersion(newVersionNo);
            templateMapper.updateById(template);
        }

        log.info("更新手术模板: id={}", id);
        return convertToDTO(template);
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteTemplate(Long id) {
        SurgeryTemplate template = templateMapper.selectById(id);
        if (template == null || template.getDeleted() == 1) {
            throw new BusinessException(ErrorCode.TEMPLATE_NOT_FOUND);
        }
        template.setDeleted(1);
        templateMapper.updateById(template);
        log.info("删除手术模板: id={}", id);
    }

    public List<SurgeryTemplateVersionDTO> getTemplateVersions(Long templateId) {
        List<SurgeryTemplateVersion> versions = versionMapper.selectByTemplateId(templateId);
        return versions.stream()
                .map(this::convertVersionToDTO)
                .collect(Collectors.toList());
    }

    public SurgeryTemplateVersionDTO getTemplateVersion(Long templateId, Integer versionNo) {
        List<SurgeryTemplateVersion> versions = versionMapper.selectByTemplateId(templateId);
        return versions.stream()
                .filter(v -> v.getVersionNo().equals(versionNo))
                .findFirst()
                .map(this::convertVersionToDTO)
                .orElseThrow(() -> new BusinessException(ErrorCode.TEMPLATE_VERSION_NOT_FOUND));
    }

    @Transactional(rollbackFor = Exception.class)
    public SurgeryTemplateDTO revertToVersion(Long id, Integer versionNo, String changeLog) {
        SurgeryTemplate template = templateMapper.selectById(id);
        if (template == null || template.getDeleted() == 1) {
            throw new BusinessException(ErrorCode.TEMPLATE_NOT_FOUND);
        }

        List<SurgeryTemplateVersion> versions = versionMapper.selectByTemplateId(id);
        SurgeryTemplateVersion targetVersion = versions.stream()
                .filter(v -> v.getVersionNo().equals(versionNo))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.TEMPLATE_VERSION_NOT_FOUND));

        SurgeryTemplateVersion latest = versionMapper.selectLatestVersion(id);
        int newVersionNo = (latest != null ? latest.getVersionNo() : 0) + 1;

        versionMapper.clearCurrentVersion(id);

        SurgeryTemplateVersion newVersion = new SurgeryTemplateVersion();
        newVersion.setTemplateId(id);
        newVersion.setVersionNo(newVersionNo);
        newVersion.setTemplateContent(targetVersion.getTemplateContent());
        newVersion.setPlaceholders(targetVersion.getPlaceholders());
        newVersion.setChangeLog(changeLog != null ? changeLog : "回退到版本 v" + versionNo);
        newVersion.setIsCurrent(1);
        newVersion.setCreatedUserId(1L);
        newVersion.setCreatedUserName("当前用户");
        versionMapper.insert(newVersion);

        template.setTemplateContent(targetVersion.getTemplateContent());
        template.setPlaceholders(targetVersion.getPlaceholders());
        template.setCurrentVersion(newVersionNo);
        template.setUpdatedUserId(1L);
        template.setUpdatedUserName("当前用户");
        templateMapper.updateById(template);

        log.info("回退模板版本: id={}, targetVersion={}, newVersion={}", id, versionNo, newVersionNo);
        return convertToDTO(template);
    }

    public String fillTemplate(Long templateId, Map<String, String> values) {
        SurgeryTemplate template = templateMapper.selectById(templateId);
        if (template == null || template.getDeleted() == 1) {
            throw new BusinessException(ErrorCode.TEMPLATE_NOT_FOUND);
        }

        templateMapper.incrementUseCount(templateId);

        String content = template.getTemplateContent();
        if (values != null) {
            for (Map.Entry<String, String> entry : values.entrySet()) {
                String placeholder = "${" + entry.getKey() + "}";
                String value = entry.getValue() != null ? entry.getValue() : "";
                content = content.replace(placeholder, value);
            }
        }
        return content;
    }

    public String fillTemplateFromRecord(Long templateId, Long recordId) {
        SurgeryTemplate template = templateMapper.selectById(templateId);
        if (template == null || template.getDeleted() == 1) {
            throw new BusinessException(ErrorCode.TEMPLATE_NOT_FOUND);
        }

        templateMapper.incrementUseCount(templateId);

        List<SurgeryEntity> entities = entityMapper.selectByRecordId(recordId);
        Map<String, String> entityMap = entities.stream()
                .collect(Collectors.toMap(
                        SurgeryEntity::getEntityType,
                        e -> e.getEntityValue() != null ? e.getEntityValue() : "",
                        (a, b) -> a
                ));

        List<PlaceholderDTO> placeholders = parsePlaceholders(template.getPlaceholders());
        Map<String, String> fillValues = new HashMap<>();

        for (PlaceholderDTO ph : placeholders) {
            String value = null;
            if (ph.getEntityType() != null) {
                value = entityMap.get(ph.getEntityType());
            }
            if (value == null || value.isEmpty()) {
                value = entityMap.get(ph.getName());
            }
            if ((value == null || value.isEmpty()) && ph.getDefaultValue() != null) {
                value = ph.getDefaultValue();
            }
            if (value == null) {
                value = "";
            }
            fillValues.put(ph.getName(), value);
        }

        String content = template.getTemplateContent();
        for (Map.Entry<String, String> entry : fillValues.entrySet()) {
            String placeholder = "${" + entry.getKey() + "}";
            content = content.replace(placeholder, entry.getValue());
        }

        return content;
    }

    public List<String> extractPlaceholders(String content) {
        if (StrUtil.isBlank(content)) {
            return Collections.emptyList();
        }
        Set<String> placeholders = new LinkedHashSet<>();
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(content);
        while (matcher.find()) {
            placeholders.add(matcher.group(1));
        }
        return new ArrayList<>(placeholders);
    }

    public Map<String, Object> exportTemplate(Long id) {
        SurgeryTemplate template = templateMapper.selectById(id);
        if (template == null || template.getDeleted() == 1) {
            throw new BusinessException(ErrorCode.TEMPLATE_NOT_FOUND);
        }

        Map<String, Object> exportMap = new LinkedHashMap<>();
        exportMap.put("templateCode", template.getTemplateCode());
        exportMap.put("templateName", template.getTemplateName());
        exportMap.put("surgeryType", template.getSurgeryType());
        exportMap.put("surgeryCode", template.getSurgeryCode());
        exportMap.put("department", template.getDepartment());
        exportMap.put("templateContent", template.getTemplateContent());
        exportMap.put("placeholders", parsePlaceholders(template.getPlaceholders()));
        exportMap.put("description", template.getDescription());
        exportMap.put("tags", template.getTags());
        exportMap.put("sortOrder", template.getSortOrder());
        exportMap.put("exportTime", DateUtil.now());
        exportMap.put("version", template.getCurrentVersion());

        return exportMap;
    }

    @Transactional(rollbackFor = Exception.class)
    public SurgeryTemplateDTO importTemplate(TemplateImportDTO dto) {
        SurgeryTemplate existing = templateMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SurgeryTemplate>()
                        .eq(SurgeryTemplate::getTemplateCode, dto.getTemplateCode())
                        .eq(SurgeryTemplate::getDeleted, 0));

        if (existing != null) {
            SurgeryTemplateUpdateDTO updateDTO = new SurgeryTemplateUpdateDTO();
            updateDTO.setTemplateName(dto.getTemplateName());
            updateDTO.setSurgeryType(dto.getSurgeryType());
            updateDTO.setSurgeryCode(dto.getSurgeryCode());
            updateDTO.setDepartment(dto.getDepartment());
            updateDTO.setTemplateContent(dto.getTemplateContent());
            updateDTO.setPlaceholders(dto.getPlaceholders());
            updateDTO.setDescription(dto.getDescription());
            updateDTO.setTags(dto.getTags());
            updateDTO.setSortOrder(dto.getSortOrder());
            updateDTO.setChangeLog("导入覆盖更新");
            return updateTemplate(existing.getId(), updateDTO);
        } else {
            SurgeryTemplateCreateDTO createDTO = new SurgeryTemplateCreateDTO();
            BeanUtils.copyProperties(dto, createDTO);
            createDTO.setChangeLog("导入创建");
            return createTemplate(createDTO);
        }
    }

    private SurgeryTemplateDTO convertToDTO(SurgeryTemplate template) {
        SurgeryTemplateDTO dto = new SurgeryTemplateDTO();
        BeanUtils.copyProperties(template, dto);
        dto.setPlaceholders(parsePlaceholders(template.getPlaceholders()));
        return dto;
    }

    private SurgeryTemplateVersionDTO convertVersionToDTO(SurgeryTemplateVersion version) {
        SurgeryTemplateVersionDTO dto = new SurgeryTemplateVersionDTO();
        BeanUtils.copyProperties(version, dto);
        dto.setPlaceholders(parsePlaceholders(version.getPlaceholders()));
        return dto;
    }

    private List<PlaceholderDTO> parsePlaceholders(String json) {
        if (StrUtil.isBlank(json)) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<PlaceholderDTO>>() {});
        } catch (JsonProcessingException e) {
            log.warn("解析占位符JSON失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private String toJson(Object obj) {
        if (obj == null) return null;
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            return null;
        }
    }
}
