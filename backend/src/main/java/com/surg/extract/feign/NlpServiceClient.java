package com.surg.extract.feign;

import com.surg.extract.dto.NlpNerRequest;
import com.surg.extract.dto.NlpNerResponse;
import com.surg.extract.dto.NlpOcrRequest;
import com.surg.extract.dto.NlpOcrResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
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

    @PostMapping("/ner/extract")
    NlpNerResponse extractEntities(@RequestBody NlpNerRequest request);
}
