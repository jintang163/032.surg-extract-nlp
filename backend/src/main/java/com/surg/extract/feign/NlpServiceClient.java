package com.surg.extract.feign;

import com.surg.extract.dto.*;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

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
}
