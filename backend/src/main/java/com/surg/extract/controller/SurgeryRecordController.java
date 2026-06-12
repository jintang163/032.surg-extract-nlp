package com.surg.extract.controller;

import com.surg.extract.common.Result;
import com.surg.extract.dto.*;
import com.surg.extract.entity.SurgeryEntity;
import com.surg.extract.service.SurgeryRecordService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/records")
@RequiredArgsConstructor
@Tag(name = "手术记录管理", description = "手术记录上传、查询、实体抽取等接口")
public class SurgeryRecordController {

    private final SurgeryRecordService surgeryRecordService;

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "上传手术记录", description = "支持上传手术记录文件（txt/word/pdf/图片），系统自动进行OCR和NLP处理")
    public Result<RecordQueryDTO> uploadRecord(
            @RequestPart("file") MultipartFile file,
            @RequestParam(value = "patientName", required = false) String patientName,
            @RequestParam(value = "hospitalNo", required = false) String hospitalNo,
            @RequestParam(value = "patientId", required = false) String patientId,
            @RequestParam(value = "department", required = false) String department) {

        RecordUploadDTO uploadDTO = new RecordUploadDTO();
        uploadDTO.setFileName(file.getOriginalFilename());
        uploadDTO.setPatientName(patientName);
        uploadDTO.setHospitalNo(hospitalNo);
        uploadDTO.setPatientId(patientId);
        uploadDTO.setDepartment(department);

        RecordQueryDTO result = surgeryRecordService.uploadRecord(file, uploadDTO);
        return Result.success("上传成功，正在后台处理", result);
    }

    @PostMapping(value = "/upload-multi", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "上传手术记录（多附件）", description = "支持上传主文档+语音旁白/器械图谱等多个附件，系统自动进行OCR/ASR/器械识别和多模态融合")
    public Result<RecordQueryDTO> uploadRecordMulti(
            @RequestPart("mainFile") MultipartFile mainFile,
            @RequestPart(value = "attachments", required = false) List<MultipartFile> attachments,
            @RequestParam(value = "patientName", required = false) String patientName,
            @RequestParam(value = "hospitalNo", required = false) String hospitalNo,
            @RequestParam(value = "patientId", required = false) String patientId,
            @RequestParam(value = "department", required = false) String department) {

        RecordUploadDTO uploadDTO = new RecordUploadDTO();
        uploadDTO.setFileName(mainFile.getOriginalFilename());
        uploadDTO.setPatientName(patientName);
        uploadDTO.setHospitalNo(hospitalNo);
        uploadDTO.setPatientId(patientId);
        uploadDTO.setDepartment(department);

        RecordQueryDTO result = surgeryRecordService.uploadRecordWithAttachments(mainFile, attachments, uploadDTO);
        return Result.success("上传成功，正在后台处理", result);
    }

    @GetMapping("/{id}/attachments")
    @Operation(summary = "获取记录附件列表", description = "获取手术记录的所有附件列表")
    public Result<List<RecordAttachmentDTO>> getAttachments(@PathVariable Long id) {
        return Result.success(surgeryRecordService.getRecordAttachments(id));
    }

    @GetMapping("/list")
    @Operation(summary = "手术记录列表", description = "分页查询手术记录列表")
    public Result<PageResult<RecordQueryDTO>> list(
            @Parameter(description = "患者姓名（模糊搜索） @RequestParam(required = false) String patientName,
            @Parameter(description = "住院号（精确搜索） @RequestParam(required = false) String hospitalNo,
            @Parameter(description = "处理状态") @RequestParam(required = false) String status,
            @Parameter(description = "开始日期") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @Parameter(description = "结束日期") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @Parameter(description = "页码，从1开始") @RequestParam(defaultValue = "1") Integer pageNum,
            @Parameter(description = "每页数量") @RequestParam(defaultValue = "20") Integer pageSize) {

        PageResult<RecordQueryDTO> result = surgeryRecordService.queryRecords(
                patientName, hospitalNo, status, startDate, endDate, pageNum, pageSize);
        return Result.success(result);
    }

    @GetMapping("/{id}")
    @Operation(summary = "手术记录详情", description = "获取单条手术记录详情")
    public Result<RecordQueryDTO> getDetail(@PathVariable Long id) {
        return Result.success(surgeryRecordService.getRecordDetail(id));
    }

    @GetMapping("/{id}/ocr-text")
    @Operation(summary = "获取OCR文本", description = "获取手术记录的OCR识别文本")
    public Result<String> getOcrText(@PathVariable Long id) {
        return Result.success(surgeryRecordService.getRecordOcrText(id));
    }

    @PutMapping("/{id}/ocr-text")
    @Operation(summary = "更新OCR文本", description = "手动修正OCR识别的文本内容")
    public Result<Void> updateOcrText(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        String ocrText = body.get("ocrText");
        surgeryRecordService.updateOcrText(id, ocrText);
        return Result.success("更新成功");
    }

    @GetMapping("/{id}/entities")
    @Operation(summary = "获取抽取实体列表", description = "获取手术记录的NLP抽取结果")
    public Result<List<SurgeryEntity>> getEntities(@PathVariable Long id) {
        return Result.success(surgeryRecordService.getRecordEntities(id));
    }

    @PutMapping("/{id}/entities")
    @Operation(summary = "更新抽取实体", description = "批量更新/确认抽取的实体")
    public Result<Void> updateEntities(
            @PathVariable Long id,
            @RequestBody List<EntityUpdateDTO> entities) {
        surgeryRecordService.updateEntity(id, entities);
        return Result.success("更新成功");
    }

    @GetMapping("/{id}/processing-time")
    @Operation(summary = "获取处理时长", description = "获取记录处理时长（秒）")
    public Result<Long> getProcessingTime(@PathVariable Long id) {
        return Result.success(surgeryRecordService.getRecordProcessingTime(id));
    }

    @PutMapping("/{id}/template-draft")
    @Operation(summary = "保存模板草稿", description = "保存模板生成的手术记录草稿内容")
    public Result<Void> saveTemplateDraft(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body) {
        Long templateId = body.get("templateId") != null ? Long.valueOf(body.get("templateId").toString()) : null;
        String draftContent = body.get("draftContent") != null ? body.get("draftContent").toString() : null;
        surgeryRecordService.saveTemplateDraft(id, templateId, draftContent);
        return Result.success("草稿已保存");
    }
}
