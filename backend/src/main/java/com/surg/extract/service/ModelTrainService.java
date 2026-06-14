package com.surg.extract.service;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.surg.extract.common.BusinessException;
import com.surg.extract.common.ErrorCode;
import com.surg.extract.dto.ModelTrainLogDTO;
import com.surg.extract.dto.ModelTrainRequestDTO;
import com.surg.extract.dto.PageResult;
import com.surg.extract.entity.DoctorFeedback;
import com.surg.extract.entity.ModelTrainLog;
import com.surg.extract.feign.NlpServiceClient;
import com.surg.extract.mapper.DoctorFeedbackMapper;
import com.surg.extract.mapper.ModelTrainLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ModelTrainService {

    private final ModelTrainLogMapper trainLogMapper;
    private final DoctorFeedbackMapper feedbackMapper;
    private final NlpServiceClient nlpServiceClient;

    @Transactional
    public ModelTrainLogDTO triggerTraining(ModelTrainRequestDTO req, Long userId, String userName) {
        String batchNo = "TRAIN-" + LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"))
                + "-" + IdUtil.fastSimpleUUID().substring(0, 6).toUpperCase();

        ModelTrainLog trainLog = new ModelTrainLog();
        trainLog.setTrainBatchNo(batchNo);
        trainLog.setModelName("surgery-ner");
        trainLog.setTrainType(req.getTrainType() != null ? req.getTrainType() : "INCREMENTAL");

        ModelTrainLog latest = trainLogMapper.selectLatestSuccess();
        if (latest != null) {
            trainLog.setPreviousVersion(latest.getModelVersion());
            trainLog.setPreviousF1Score(latest.getF1Score());
        }

        int minScore = req.getMinQualityScore() != null ? req.getMinQualityScore() : 60;
        int limit = req.getMaxFeedbackCount() != null ? req.getMaxFeedbackCount() : 5000;
        List<DoctorFeedback> pendingList = feedbackMapper.selectPendingForTraining(limit, minScore);

        trainLog.setFeedbackCount(pendingList.size());
        trainLog.setNewSampleCount(pendingList.size());
        trainLog.setTrainStatus("PENDING");
        trainLog.setTriggeredBy(userId);
        trainLog.setTriggeredByName(userName);
        trainLog.setRemark(req.getRemark());

        Map<String, Object> trainParams = new HashMap<>();
        trainParams.put("epochs", req.getEpochs() != null ? req.getEpochs() : 10);
        trainParams.put("batchSize", req.getBatchSize() != null ? req.getBatchSize() : 16);
        trainParams.put("learningRate", req.getLearningRate() != null ? req.getLearningRate() : 0.001);
        trainParams.put("minQualityScore", minScore);
        trainLog.setTrainParams(com.alibaba.fastjson.JSON.toJSONString(trainParams));

        trainLogMapper.insert(trainLog);

        List<Long> feedbackIds = pendingList.stream().map(DoctorFeedback::getId).collect(Collectors.toList());

        asyncExecuteTraining(trainLog.getId(), pendingList, feedbackIds, trainParams);

        return convertToDTO(trainLog);
    }

    @Async("taskExecutor")
    public void asyncExecuteTraining(Long trainLogId, List<DoctorFeedback> feedbackList,
                                     List<Long> feedbackIds, Map<String, Object> params) {
        try {
            ModelTrainLog log = trainLogMapper.selectById(trainLogId);
            log.setTrainStatus("RUNNING");
            log.setTrainStartTime(LocalDateTime.now());
            trainLogMapper.updateById(log);

            Map<String, Object> trainData = prepareTrainingData(feedbackList);
            Map<String, Object> result = executePythonTraining(trainData, params);

            String batchNo = log.getTrainBatchNo();
            log.setTrainEndTime(LocalDateTime.now());
            log.setTrainDurationSec((int) java.time.Duration.between(log.getTrainStartTime(), log.getTrainEndTime()).getSeconds());
            log.setTrainLoss(result.get("trainLoss") != null ? new BigDecimal(result.get("trainLoss").toString()) : null);
            log.setDevLoss(result.get("devLoss") != null ? new BigDecimal(result.get("devLoss").toString()) : null);
            log.setPrecisionScore(result.get("precision") != null ? new BigDecimal(result.get("precision").toString()) : null);
            log.setRecallScore(result.get("recall") != null ? new BigDecimal(result.get("recall").toString()) : null);
            log.setF1Score(result.get("f1") != null ? new BigDecimal(result.get("f1").toString()) : null);
            log.setModelVersion(result.get("modelVersion") != null ? result.get("modelVersion").toString() : null);
            if (log.getPreviousF1Score() != null && log.getF1Score() != null) {
                log.setF1Improvement(log.getF1Score().subtract(log.getPreviousF1Score()));
            }
            log.setModelPath(result.get("modelPath") != null ? result.get("modelPath").toString() : null);
            log.setEntityTypeBreakdown(result.get("entityBreakdown") != null ?
                    com.alibaba.fastjson.JSON.toJSONString(result.get("entityBreakdown")) : null);

            boolean success = result.get("success") != null && Boolean.TRUE.equals(result.get("success"));
            log.setTrainStatus(success ? "SUCCESS" : "FAILED");
            if (!success) {
                log.setFailReason(result.get("failReason") != null ? result.get("failReason").toString() : "训练脚本返回失败");
            }
            trainLogMapper.updateById(log);

            if (success && feedbackIds != null && !feedbackIds.isEmpty()) {
                feedbackMapper.markAsUsed(feedbackIds, batchNo);
                log.info("训练成功，已标记 {} 条反馈数据为已使用, batchNo={}", feedbackIds.size(), batchNo);
            }

            log.info("模型训练完成: batchNo={}, status={}, F1={}", log.getTrainBatchNo(), log.getTrainStatus(), log.getF1Score());
        } catch (Exception e) {
            log.error("模型训练失败", e);
            ModelTrainLog log = trainLogMapper.selectById(trainLogId);
            if (log != null) {
                log.setTrainStatus("FAILED");
                log.setFailReason(e.getMessage() != null ? e.getMessage().substring(0, 500) : "未知错误");
                log.setTrainEndTime(LocalDateTime.now());
                trainLogMapper.updateById(log);
            }
        }
    }

    private Map<String, Object> prepareTrainingData(List<DoctorFeedback> feedbackList) {
        Map<String, Object> data = new HashMap<>();
        List<Map<String, Object>> samples = feedbackList.stream().map(fb -> {
            Map<String, Object> sample = new HashMap<>();
            sample.put("id", fb.getId());
            sample.put("entityType", fb.getEntityType());
            sample.put("originalValue", fb.getOriginalValue());
            sample.put("correctedValue", fb.getCorrectedValue());
            sample.put("correctionType", fb.getCorrectionType());
            sample.put("originalStartPos", fb.getOriginalStartPos());
            sample.put("originalEndPos", fb.getOriginalEndPos());
            sample.put("originalText", fb.getOriginalText());
            sample.put("qualityScore", fb.getQualityScore());
            return sample;
        }).collect(Collectors.toList());
        data.put("samples", samples);
        data.put("sampleCount", samples.size());
        return data;
    }

    private Map<String, Object> executePythonTraining(Map<String, Object> trainData, Map<String, Object> params) {
        String paramsJson = com.alibaba.fastjson.JSON.toJSONString(params);
        Map<String, Object> result = nlpServiceClient.triggerIncrementalTraining(trainData, paramsJson);
        if (result == null) {
            throw new RuntimeException("NLP训练服务返回空结果");
        }
        if (result.get("success") != null && Boolean.FALSE.equals(result.get("success"))) {
            String msg = result.get("message") != null ? result.get("message").toString() : "训练失败";
            throw new RuntimeException("NLP训练服务返回失败: " + msg);
        }
        return result;
    }

    public ModelTrainLogDTO getTrainLogById(Long id) {
        ModelTrainLog log = trainLogMapper.selectById(id);
        if (log == null) {
            throw new BusinessException(ErrorCode.RECORD_NOT_FOUND);
        }
        return convertToDTO(log);
    }

    public PageResult<ModelTrainLogDTO> getTrainLogPage(
            Integer pageNum, Integer pageSize,
            String trainStatus, String trainType,
            LocalDate startDate, LocalDate endDate) {

        Page<ModelTrainLog> page = new Page<>(pageNum, pageSize);
        com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ModelTrainLog> wrapper =
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>();

        wrapper.eq(ModelTrainLog::getDeleted, 0);
        if (StrUtil.isNotBlank(trainStatus)) wrapper.eq(ModelTrainLog::getTrainStatus, trainStatus);
        if (StrUtil.isNotBlank(trainType)) wrapper.eq(ModelTrainLog::getTrainType, trainType);
        if (startDate != null) wrapper.ge(ModelTrainLog::getTrainStartTime, startDate.atStartOfDay());
        if (endDate != null) wrapper.le(ModelTrainLog::getTrainStartTime, endDate.atTime(23, 59, 59));

        wrapper.orderByDesc(ModelTrainLog::getCreatedTime);

        IPage<ModelTrainLog> resultPage = trainLogMapper.selectPage(page, wrapper);
        List<ModelTrainLogDTO> dtoList = resultPage.getRecords().stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());

        return PageResult.of(dtoList, resultPage.getTotal(), pageNum, pageSize);
    }

    public ModelTrainLogDTO getLatestSuccess() {
        ModelTrainLog log = trainLogMapper.selectLatestSuccess();
        return log != null ? convertToDTO(log) : null;
    }

    public List<ModelTrainLogDTO> getRecentTrainLogs(Integer limit) {
        List<ModelTrainLog> list = trainLogMapper.selectLatestN(limit != null ? limit : 10);
        return list.stream().map(this::convertToDTO).collect(Collectors.toList());
    }

    private ModelTrainLogDTO convertToDTO(ModelTrainLog log) {
        ModelTrainLogDTO dto = new ModelTrainLogDTO();
        BeanUtils.copyProperties(log, dto);
        return dto;
    }
}
