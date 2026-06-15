package com.surg.extract.feign;

import com.surg.extract.dto.*;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@FeignClient(
    name = "nlp-service",
    url = "${nlp.service.url:http://localhost:8000}",
    path = "/api/v1"
)
public interface NlpServiceClient {

    @PostMapping(value = "/ocr/process", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    NlpOcrResponse processOcr(
            @RequestPart("file") MultipartFile file,
            @RequestParam("fileType") String fileType
    );

    @PostMapping("/ocr/process-text")
    NlpOcrResponse processOcrByFilePath(@RequestBody NlpOcrRequest request);

    @PostMapping(value = "/asr/process", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    AsrProcessResponseDTO processAsr(
            @RequestPart("file") MultipartFile file,
            @RequestParam("language") String language
    );

    @PostMapping("/asr/process-path")
    AsrProcessResponseDTO processAsrByFilePath(@RequestBody AsrProcessRequestDTO request);

    @PostMapping(value = "/instrument/recognize", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    InstrumentRecognitionResponseDTO recognizeInstrument(
            @RequestPart("file") MultipartFile file,
            @RequestParam("mode") String mode,
            @RequestParam("confidence_threshold") Double confidenceThreshold
    );

    @PostMapping("/instrument/recognize-path")
    InstrumentRecognitionResponseDTO recognizeInstrumentByFilePath(
            @RequestBody InstrumentRecognitionRequestDTO request
    );

    @GetMapping("/instrument/catalog")
    InstrumentCatalogDTO getInstrumentCatalog();

    @PostMapping("/multimodal/fuse")
    MultimodalFusionResponseDTO fuseMultimodal(@RequestBody MultimodalFusionRequestDTO request);

    @PostMapping("/ner/extract")
    NlpNerResponse extractEntities(@RequestBody NlpNerRequest request);

    @PostMapping("/ner/custom/train")
    Map<String, Object> trainCustomNer(@RequestBody Map<String, Object> request);

    @GetMapping("/ner/custom/status/{field_id}")
    Map<String, Object> getCustomNerStatus(@PathVariable("field_id") Long fieldId);

    @PostMapping("/ner/incremental-train")
    Map<String, Object> triggerIncrementalTraining(
            @RequestBody Map<String, Object> trainData,
            @RequestHeader("X-Train-Params") String paramsJson
    );

    @PostMapping("/icd10-pcs/recommend")
    Map<String, Object> recommendIcd10PcsCodes(@RequestBody Map<String, Object> request);

    @PostMapping(value = "/icd10-pcs/recommend-from-text", consumes = "application/x-www-form-urlencoded")
    Map<String, Object> recommendIcd10PcsFromText(
            @RequestParam("text") String text,
            @RequestParam(value = "record_id", required = false) String recordId,
            @RequestParam(value = "top_k", defaultValue = "5") Integer topK
    );

    @PostMapping("/icd10-pcs/confirm")
    Map<String, Object> confirmIcd10PcsCode(@RequestBody Map<String, Object> request);

    @GetMapping("/icd10-pcs/history")
    Map<String, Object> getIcd10PcsHistory(
            @RequestParam(value = "record_id", required = false) Long recordId,
            @RequestParam(value = "limit", defaultValue = "100") Integer limit
    );

    @GetMapping("/icd10-pcs/knowledge")
    Map<String, Object> getIcd10PcsKnowledge();
}
