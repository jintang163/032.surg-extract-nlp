package com.surg.extract.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.surg.extract.common.BusinessException;
import com.surg.extract.common.ErrorCode;
import com.surg.extract.dto.*;
import com.surg.extract.entity.Icd10PcsConfirmation;
import com.surg.extract.entity.MedicalRecordHome;
import com.surg.extract.feign.NlpServiceClient;
import com.surg.extract.mapper.Icd10PcsConfirmationMapper;
import com.surg.extract.mapper.MedicalRecordHomeMapper;
import com.surg.extract.mapper.SurgeryEntityMapper;
import com.surg.extract.types.EntityType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class Icd10PcsCodingService {

    private final NlpServiceClient nlpServiceClient;
    private final Icd10PcsConfirmationMapper confirmationMapper;
    private final MedicalRecordHomeMapper homePageMapper;
    private final SurgeryEntityMapper surgeryEntityMapper;
    private final ObjectMapper objectMapper;

    public Icd10PcsRecommendResultDTO recommendCodes(Long recordId, String text, Integer topK) {
        try {
            if (StringUtils.hasText(text)) {
                Map<String, Object> resp = nlpServiceClient.recommendIcd10PcsFromText(
                        text,
                        recordId != null ? recordId.toString() : null,
                        topK != null ? topK : 5
                );
                return convertToResultDTO(resp);
            }

            Icd10PcsRecommendRequestDTO req = new Icd10PcsRecommendRequestDTO();
            req.setRecordId(recordId);
            req.setTopK(topK != null ? topK : 5);
            if (recordId != null) {
                List<com.surg.extract.entity.SurgeryEntity> entityList = surgeryEntityMapper.selectByRecordId(recordId);
                List<NerEntityDTO> nerEntities = entityList.stream()
                        .map(this::toNerDTO)
                        .collect(Collectors.toList());
                req.setEntities(nerEntities);
            }

            Map<String, Object> resp = nlpServiceClient.recommendIcd10PcsCodes(req);
            return convertToResultDTO(resp);
        } catch (Exception e) {
            log.error("推荐ICD-10-PCS编码失败: recordId={}", recordId, e);
            Icd10PcsRecommendResultDTO result = new Icd10PcsRecommendResultDTO();
            result.setSuccess(false);
            result.setErrorMessage(e.getMessage());
            return result;
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> confirmCode(Long recordId, Icd10PcsConfirmRequestDTO dto, Long userId, String userName) {
        if (recordId == null || !StringUtils.hasText(dto.getPcsCode())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), "recordId和pcsCode不能为空");
        }

        Icd10PcsConfirmation conf = new Icd10PcsConfirmation();
        conf.setRecordId(recordId);
        conf.setPcsCode(dto.getPcsCode().toUpperCase());
        conf.setDescription(dto.getDescription());
        if (dto.getCodeComponents() != null) {
            Map<String, String> c = dto.getCodeComponents();
            conf.setSectionCode(c.get("section"));
            conf.setBodySystemCode(c.get("body_system"));
            conf.setRootOperationCode(c.get("root_operation"));
            conf.setBodyPartCode(c.get("body_part"));
            conf.setApproachCode(c.get("approach"));
            conf.setDeviceCode(c.get("device"));
            conf.setQualifierCode(c.get("qualifier"));
        }
        conf.setRecommendedConfidence(dto.getRecommendationConfidence());
        conf.setSource(StringUtils.hasText(dto.getSource()) ? dto.getSource() : "manual_confirm");
        if (dto.getAdditionalData() != null) {
            try {
                conf.setAdditionalData(objectMapper.writeValueAsString(dto.getAdditionalData()));
            } catch (JsonProcessingException e) {
                log.warn("序列化additional_data失败", e);
            }
        }
        conf.setConfirmUserId(userId != null ? userId : 1L);
        conf.setConfirmUserName(StringUtils.hasText(userName) ? userName : "系统用户");
        conf.setConfirmTime(LocalDateTime.now());

        if (StringUtils.hasText(dto.getRecommendedCode())) {
            conf.setRecommendedCode(dto.getRecommendedCode());
            conf.setIsMatched(dto.getPcsCode().equalsIgnoreCase(dto.getRecommendedCode()) ? 1 : 0);
        } else {
            conf.setIsMatched(null);
        }
        confirmationMapper.insert(conf);

        MedicalRecordHome home = homePageMapper.selectByRecordId(recordId);
        if (home == null) {
            home = new MedicalRecordHome();
            home.setRecordId(recordId);
            home.setStatus("DRAFT");
            home.setSurgeryCode(conf.getPcsCode());
            homePageMapper.insert(home);
        } else {
            home.setSurgeryCode(conf.getPcsCode());
            homePageMapper.updateById(home);
        }

        try {
            Map<String, Object> feignReq = new LinkedHashMap<>();
            feignReq.put("record_id", recordId);
            feignReq.put("pcs_code", conf.getPcsCode());
            feignReq.put("user_id", conf.getConfirmUserId().toString());
            feignReq.put("user_name", conf.getConfirmUserName());
            feignReq.put("source", conf.getSource());
            feignReq.put("additional_data", dto.getAdditionalData());
            nlpServiceClient.confirmIcd10PcsCode(feignReq);
        } catch (Exception e) {
            log.warn("同步ICD-10-PCS确认到NLP服务失败", e);
        }

        log.info("ICD-10-PCS编码确认成功: recordId={}, pcsCode={}, user={}", recordId, conf.getPcsCode(), conf.getConfirmUserName());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("confirmationId", conf.getId());
        result.put("recordId", conf.getRecordId());
        result.put("pcsCode", conf.getPcsCode());
        result.put("description", conf.getDescription());
        result.put("confirmedAt", conf.getConfirmTime());
        result.put("confirmedBy", conf.getConfirmUserName());
        result.put("homePageUpdated", true);
        return result;
    }

    public Map<String, Object> getHomePageWithCodes(Long recordId) {
        Map<String, Object> result = new LinkedHashMap<>();

        MedicalRecordHome home = homePageMapper.selectByRecordId(recordId);
        if (home != null) {
            try {
                result = objectMapper.convertValue(home, new com.fasterxml.jackson.core.type.TypeReference<>() {});
                if (StringUtils.hasText(home.getComplications())) {
                    result.put("complications", objectMapper.readValue(home.getComplications(),
                            new com.fasterxml.jackson.core.type.TypeReference<List<String>>() {}));
                } else {
                    result.put("complications", new ArrayList<>());
                }
            } catch (Exception e) {
                log.warn("转换病案首页失败", e);
            }
        }

        Icd10PcsRecommendationDTO topRec = null;
        List<Icd10PcsRecommendationDTO> recommendations = Collections.emptyList();
        try {
            Icd10PcsRecommendResultDTO recResult = recommendCodes(recordId, null, 5);
            if (Boolean.TRUE.equals(recResult.getSuccess())) {
                recommendations = recResult.getRecommendations() != null ? recResult.getRecommendations() : Collections.emptyList();
                topRec = recResult.getTopCode();
            }
        } catch (Exception e) {
            log.warn("获取ICD-10-PCS推荐失败: recordId={}", recordId, e);
        }
        result.put("pcs_recommendations", recommendations);
        result.put("pcs_top_recommendation", topRec);

        Icd10PcsConfirmation latest = confirmationMapper.selectLatestByRecordId(recordId);
        Map<String, Object> latestConfMap = null;
        if (latest != null) {
            latestConfMap = new LinkedHashMap<>();
            latestConfMap.put("id", latest.getId());
            latestConfMap.put("pcs_code", latest.getPcsCode());
            latestConfMap.put("description", latest.getDescription());
            latestConfMap.put("confirmed_at", latest.getConfirmTime());
            latestConfMap.put("confirmed_by", latest.getConfirmUserName());
            latestConfMap.put("source", latest.getSource());
            latestConfMap.put("recommended_confidence", latest.getRecommendedConfidence());
        }
        result.put("pcs_confirmed_code", latestConfMap);

        if (latestConfMap == null && topRec != null) {
            result.put("pcs_suggested_code", topRec.getPcsCode());
            result.put("pcs_suggested_confidence", topRec.getConfidence());
            result.put("pcs_suggested_description", topRec.getDescription());
        } else if (latestConfMap != null) {
            result.put("pcs_suggested_code", latestConfMap.get("pcs_code"));
            result.put("pcs_suggested_confidence", null);
            result.put("pcs_suggested_description", latestConfMap.get("description"));
        }

        return result;
    }

    public List<Icd10PcsConfirmation> getConfirmationHistory(Long recordId) {
        if (recordId != null) {
            return confirmationMapper.selectByRecordId(recordId);
        }
        LambdaQueryWrapper<Icd10PcsConfirmation> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(Icd10PcsConfirmation::getConfirmTime);
        wrapper.last("LIMIT 500");
        return confirmationMapper.selectList(wrapper);
    }

    private Icd10PcsRecommendResultDTO convertToResultDTO(Map<String, Object> resp) {
        Icd10PcsRecommendResultDTO result = new Icd10PcsRecommendResultDTO();
        if (resp == null) {
            result.setSuccess(false);
            result.setErrorMessage("NLP服务无响应");
            return result;
        }
        result.setSuccess(Boolean.TRUE.equals(resp.get("success")));
        result.setErrorMessage((String) resp.get("error_message"));
        if (resp.get("processing_time_ms") != null) {
            result.setProcessingTimeMs(((Number) resp.get("processing_time_ms")).intValue());
        }
        if (resp.get("parsed_entities") instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> pe = (Map<String, Object>) resp.get("parsed_entities");
            result.setParsedEntities(pe);
        }
        if (resp.get("recommendations") instanceof List) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> recList = (List<Map<String, Object>>) resp.get("recommendations");
            result.setRecommendations(recList.stream().map(this::toRecDTO).collect(Collectors.toList()));
        }
        if (resp.get("top_code") instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> tc = (Map<String, Object>) resp.get("top_code");
            result.setTopCode(toRecDTO(tc));
        }
        return result;
    }

    private Icd10PcsRecommendationDTO toRecDTO(Map<String, Object> m) {
        Icd10PcsRecommendationDTO dto = new Icd10PcsRecommendationDTO();
        dto.setPcsCode((String) m.get("pcs_code"));
        dto.setDescription((String) m.get("description"));
        if (m.get("confidence") != null) {
            dto.setConfidence(((Number) m.get("confidence")).doubleValue());
        }
        if (m.get("is_complete") != null) {
            dto.setIsComplete(Boolean.TRUE.equals(m.get("is_complete")));
        }
        if (m.get("code_components") instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, String> cc = (Map<String, String>) m.get("code_components");
            dto.setCodeComponents(cc);
        }
        if (m.get("match_path") instanceof List) {
            @SuppressWarnings("unchecked")
            List<String> p = (List<String>) m.get("match_path");
            dto.setMatchPath(p);
        }
        if (m.get("matched_rules") instanceof List) {
            @SuppressWarnings("unchecked")
            List<String> r = (List<String>) m.get("matched_rules");
            dto.setMatchedRules(r);
        }
        if (m.get("missing_fields") instanceof List) {
            @SuppressWarnings("unchecked")
            List<String> f = (List<String>) m.get("missing_fields");
            dto.setMissingFields(f);
        }
        return dto;
    }

    private NerEntityDTO toNerDTO(com.surg.extract.entity.SurgeryEntity e) {
        NerEntityDTO dto = new NerEntityDTO();
        dto.setEntityType(e.getEntityType());
        dto.setEntityValue(e.getEntityValue());
        dto.setConfidence(e.getConfidence() != null ? e.getConfidence().doubleValue() : null);
        dto.setStartPos(e.getStartPos());
        dto.setEndPos(e.getEndPos());
        dto.setOriginalText(e.getOriginalText());
        return dto;
    }
}
