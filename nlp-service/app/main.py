from fastapi import FastAPI, File, UploadFile, Form, HTTPException, Depends
from fastapi.middleware.cors import CORSMiddleware
from loguru import logger
import sys
import os
import tempfile
import time

from app.config import get_settings
from app.schemas import (
    OcrProcessRequest, OcrProcessResponse,
    NerExtractRequest, NerExtractResponse,
    AsrProcessRequest, AsrProcessResponse,
    InstrumentRecognitionRequest, InstrumentRecognitionResponse,
    InstrumentCatalogResponse,
    MultimodalFusionRequest, MultimodalFusionResponse,
    HealthResponse
)
from app.services.ner_extract_service import NerExtractService
from app.services.asr_service import AsrService, AsrProcessingError, AsrDependencyError
from app.services.instrument_recognition_service import (
    InstrumentRecognitionService,
    InstrumentProcessingError,
    InstrumentDependencyError,
)
from app.services.multimodal_fusion_service import MultimodalFusionService


settings = get_settings()

logger.remove()
logger.add(sys.stdout, level="DEBUG" if settings.debug else "INFO")
logger.add("./logs/nlp-service.log", rotation="50 MB", retention="30 days", level="INFO")


app = FastAPI(
    title=settings.app_name,
    version=settings.app_version,
    description="手术记录结构化提取 NLP 微服务 - OCR识别 + ASR语音转写 + 器械识别 + 实体抽取 + 多模态融合"
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

_ner_service: NerExtractService = None
_asr_service: AsrService = None
_instrument_service: InstrumentRecognitionService = None
_fusion_service: MultimodalFusionService = None


def get_ner_service() -> NerExtractService:
    global _ner_service
    if _ner_service is None:
        _ner_service = NerExtractService()
    return _ner_service


def get_asr_service() -> AsrService:
    global _asr_service
    if _asr_service is None:
        _asr_service = AsrService()
    return _asr_service


def get_instrument_service() -> InstrumentRecognitionService:
    global _instrument_service
    if _instrument_service is None:
        _instrument_service = InstrumentRecognitionService()
    return _instrument_service


def get_fusion_service() -> MultimodalFusionService:
    global _fusion_service
    if _fusion_service is None:
        _fusion_service = MultimodalFusionService()
    return _fusion_service


@app.on_event("startup")
async def startup_event():
    logger.info(f"启动NLP服务: {settings.app_name} v{settings.app_version}")
    logger.info(f"监听地址: {settings.host}:{settings.port}")
    try:
        get_ner_service()
        get_asr_service()
        get_instrument_service()
        get_fusion_service()
        logger.info("NLP服务（含多模态）初始化成功")
    except Exception as e:
        logger.error(f"NLP服务初始化失败: {e}", exc_info=True)


@app.on_event("shutdown")
async def shutdown_event():
    logger.info("NLP服务关闭")


@app.get("/health", response_model=HealthResponse, tags=["系统"])
async def health_check():
    return HealthResponse(status="healthy", version=settings.app_version)


@app.get("/api/v1/health", response_model=HealthResponse, tags=["系统"])
async def api_health_check():
    return HealthResponse(status="healthy", version=settings.app_version)


@app.post("/api/v1/ocr/process", response_model=OcrProcessResponse, tags=["OCR"])
async def api_ocr_process(
    file: UploadFile = File(..., description="上传文件"),
    fileType: str = Form(None, description="文件类型: TEXT/WORD/PDF/IMAGE"),
    service: NerExtractService = Depends(get_ner_service)
):
    logger.info(f"OCR请求: filename={file.filename}, type={fileType}, size={file.size}")
    start = time.time()

    try:
        content = await file.read()
        result = service.process_ocr(content, file.filename, fileType)
        result["processing_time_ms"] = int((time.time() - start) * 1000)
        return OcrProcessResponse(**result)
    except Exception as e:
        logger.error(f"OCR处理异常: {e}", exc_info=True)
        return OcrProcessResponse(
            success=False,
            ocr_text=None,
            processed_text=None,
            error_message=f"OCR服务异常: {str(e)}",
            processing_time_ms=int((time.time() - start) * 1000)
        )


@app.post("/api/v1/ocr/process-text", response_model=OcrProcessResponse, tags=["OCR"])
async def api_ocr_process_text(
    request: OcrProcessRequest,
    service: NerExtractService = Depends(get_ner_service)
):
    logger.info(f"OCR文本处理请求: record_id={request.record_id}, file_path={request.file_path}")
    start = time.time()

    file_path = request.file_path
    if not file_path:
        return OcrProcessResponse(
            success=False,
            ocr_text=None,
            processed_text=None,
            error_message="文件路径为空，请提供有效的文件路径",
            processing_time_ms=int((time.time() - start) * 1000)
        )

    try:
        result = service.process_ocr_by_path(file_path, request.file_type)
        result["processing_time_ms"] = int((time.time() - start) * 1000)
        return OcrProcessResponse(**result)
    except Exception as e:
        logger.error(f"OCR文本处理异常: {e}", exc_info=True)
        return OcrProcessResponse(
            success=False,
            ocr_text=None,
            processed_text=None,
            error_message=f"OCR服务异常: {str(e)}",
            processing_time_ms=int((time.time() - start) * 1000)
        )


@app.post("/api/v1/asr/process", response_model=AsrProcessResponse, tags=["ASR语音识别"])
async def api_asr_process(
    file: UploadFile = File(..., description="音频或视频文件"),
    language: str = Form("zh", description="语言: zh/en"),
    service: AsrService = Depends(get_asr_service)
):
    logger.info(f"ASR请求: filename={file.filename}, language={language}, size={file.size}")
    start = time.time()

    try:
        content = await file.read()
        result = service.transcribe_bytes(content, file.filename, language)
        result["processing_time_ms"] = int((time.time() - start) * 1000)
        return AsrProcessResponse(**result)
    except (AsrProcessingError, AsrDependencyError) as e:
        logger.error(f"ASR处理失败: {e}")
        return AsrProcessResponse(
            success=False,
            full_text=None,
            segments=None,
            error_message=str(e),
            processing_time_ms=int((time.time() - start) * 1000)
        )
    except Exception as e:
        logger.error(f"ASR处理异常: {e}", exc_info=True)
        return AsrProcessResponse(
            success=False,
            full_text=None,
            segments=None,
            error_message=f"ASR服务异常: {str(e)}",
            processing_time_ms=int((time.time() - start) * 1000)
        )


@app.post("/api/v1/asr/process-path", response_model=AsrProcessResponse, tags=["ASR语音识别"])
async def api_asr_process_path(
    request: AsrProcessRequest,
    service: AsrService = Depends(get_asr_service)
):
    logger.info(f"ASR路径处理请求: record_id={request.record_id}, file_path={request.file_path}")
    start = time.time()

    if not request.file_path:
        return AsrProcessResponse(
            success=False,
            full_text=None,
            segments=None,
            error_message="文件路径为空，请提供有效的文件路径",
            processing_time_ms=int((time.time() - start) * 1000)
        )

    try:
        result = service.transcribe_file(
            request.file_path,
            filename=None,
            language=request.language or "zh",
        )
        result["processing_time_ms"] = int((time.time() - start) * 1000)
        return AsrProcessResponse(**result)
    except (AsrProcessingError, AsrDependencyError) as e:
        logger.error(f"ASR路径处理失败: {e}")
        return AsrProcessResponse(
            success=False,
            full_text=None,
            segments=None,
            error_message=str(e),
            processing_time_ms=int((time.time() - start) * 1000)
        )
    except Exception as e:
        logger.error(f"ASR路径处理异常: {e}", exc_info=True)
        return AsrProcessResponse(
            success=False,
            full_text=None,
            segments=None,
            error_message=f"ASR服务异常: {str(e)}",
            processing_time_ms=int((time.time() - start) * 1000)
        )


@app.post(
    "/api/v1/instrument/recognize",
    response_model=InstrumentRecognitionResponse,
    tags=["手术器械识别"]
)
async def api_instrument_recognize(
    file: UploadFile = File(..., description="器械图片"),
    mode: str = Form("hybrid", description="识别模式: detection/classification/text_based/hybrid"),
    confidence_threshold: float = Form(0.3, description="置信度阈值"),
    service: InstrumentRecognitionService = Depends(get_instrument_service)
):
    logger.info(f"器械识别请求: filename={file.filename}, mode={mode}, size={file.size}")
    start = time.time()

    try:
        content = await file.read()
        result = service.recognize_instruments(
            content, file.filename, mode, confidence_threshold
        )
        result["processing_time_ms"] = int((time.time() - start) * 1000)
        return InstrumentRecognitionResponse(**result)
    except (InstrumentProcessingError, InstrumentDependencyError) as e:
        logger.error(f"器械识别失败: {e}")
        return InstrumentRecognitionResponse(
            success=False,
            instruments=[],
            error_message=str(e),
            processing_time_ms=int((time.time() - start) * 1000)
        )
    except Exception as e:
        logger.error(f"器械识别异常: {e}", exc_info=True)
        return InstrumentRecognitionResponse(
            success=False,
            instruments=[],
            error_message=f"器械识别服务异常: {str(e)}",
            processing_time_ms=int((time.time() - start) * 1000)
        )


@app.post(
    "/api/v1/instrument/recognize-path",
    response_model=InstrumentRecognitionResponse,
    tags=["手术器械识别"]
)
async def api_instrument_recognize_path(
    request: InstrumentRecognitionRequest,
    service: InstrumentRecognitionService = Depends(get_instrument_service)
):
    logger.info(f"器械识别路径请求: record_id={request.record_id}, file_path={request.file_path}")
    start = time.time()

    if not request.file_path:
        return InstrumentRecognitionResponse(
            success=False,
            instruments=[],
            error_message="文件路径为空，请提供有效的文件路径",
            processing_time_ms=int((time.time() - start) * 1000)
        )

    try:
        result = service.recognize_from_file_path(
            request.file_path,
            mode=request.mode or "hybrid",
            confidence_threshold=request.confidence_threshold or 0.3,
        )
        result["processing_time_ms"] = int((time.time() - start) * 1000)
        return InstrumentRecognitionResponse(**result)
    except (InstrumentProcessingError, InstrumentDependencyError) as e:
        logger.error(f"器械识别路径处理失败: {e}")
        return InstrumentRecognitionResponse(
            success=False,
            instruments=[],
            error_message=str(e),
            processing_time_ms=int((time.time() - start) * 1000)
        )
    except Exception as e:
        logger.error(f"器械识别路径处理异常: {e}", exc_info=True)
        return InstrumentRecognitionResponse(
            success=False,
            instruments=[],
            error_message=f"器械识别服务异常: {str(e)}",
            processing_time_ms=int((time.time() - start) * 1000)
        )


@app.get(
    "/api/v1/instrument/catalog",
    response_model=InstrumentCatalogResponse,
    tags=["手术器械识别"]
)
async def api_instrument_catalog(
    service: InstrumentRecognitionService = Depends(get_instrument_service)
):
    logger.info("获取器械目录")
    catalog = service.get_instrument_catalog()
    return InstrumentCatalogResponse(**catalog)


@app.post(
    "/api/v1/multimodal/fuse",
    response_model=MultimodalFusionResponse,
    tags=["多模态融合"]
)
async def api_multimodal_fuse(
    request: MultimodalFusionRequest,
    service: MultimodalFusionService = Depends(get_fusion_service)
):
    logger.info(
        f"多模态融合请求: record_id={request.record_id}, "
        f"ocr_text_len={len(request.ocr_text or '')}, "
        f"has_asr={request.asr_result is not None}, "
        f"has_instrument={request.instrument_result is not None}"
    )
    start = time.time()

    try:
        ner_entities = [e.model_dump() for e in (request.ner_entities or [])]
        regex_entities = [e.model_dump() for e in (request.regex_entities or [])]
        rule_entities = [e.model_dump() for e in (request.rule_entities or [])]

        fusion_result = service.full_fusion_pipeline(
            ocr_text=request.ocr_text,
            asr_result=request.asr_result,
            instrument_result=request.instrument_result,
            ner_entities=ner_entities,
            regex_entities=regex_entities,
            rule_entities=rule_entities,
        )

        response = {
            "success": True,
            "enhanced_text": fusion_result.enhanced_text,
            "entities": fusion_result.entities,
            "instruments": fusion_result.instruments,
            "fusion_stats": fusion_result.fusion_stats,
            "source_breakdown": fusion_result.source_breakdown,
            "error_message": None,
            "processing_time_ms": int((time.time() - start) * 1000),
        }
        return MultimodalFusionResponse(**response)
    except Exception as e:
        logger.error(f"多模态融合异常: {e}", exc_info=True)
        return MultimodalFusionResponse(
            success=False,
            enhanced_text=None,
            entities=[],
            instruments=[],
            error_message=f"多模态融合服务异常: {str(e)}",
            processing_time_ms=int((time.time() - start) * 1000)
        )


@app.post("/api/v1/ner/extract", response_model=NerExtractResponse, tags=["实体抽取"])
async def api_ner_extract(
    request: NerExtractRequest,
    service: NerExtractService = Depends(get_ner_service)
):
    logger.info(f"NER抽取请求: record_id={request.record_id}, text_len={len(request.text)}")
    start = time.time()

    try:
        result = service.extract_entities(request.text, request.record_id)
        result["processing_time_ms"] = int((time.time() - start) * 1000)
        return NerExtractResponse(**result)
    except Exception as e:
        logger.error(f"NER抽取异常: {e}", exc_info=True)
        raise HTTPException(status_code=500, detail=str(e))


@app.post("/api/v1/pipeline/process", tags=["完整流程"])
async def api_pipeline_process(
    file: UploadFile = File(..., description="上传文件"),
    fileType: str = Form(None, description="文件类型"),
    service: NerExtractService = Depends(get_ner_service)
):
    logger.info(f"完整流程请求: filename={file.filename}, type={fileType}")
    start = time.time()

    try:
        content = await file.read()

        ocr_result = service.process_ocr(content, file.filename, fileType)
        if not ocr_result.get("success"):
            return {
                "success": False,
                "error_message": f"OCR失败: {ocr_result.get('error_message')}",
                "processing_time_ms": int((time.time() - start) * 1000)
            }

        processed_text = ocr_result.get("processed_text", "")
        ner_result = service.extract_entities(processed_text)

        return {
            "success": True,
            "ocr": ocr_result,
            "ner": ner_result,
            "processing_time_ms": int((time.time() - start) * 1000)
        }
    except Exception as e:
        logger.error(f"完整流程异常: {e}", exc_info=True)
        raise HTTPException(status_code=500, detail=str(e))


if __name__ == "__main__":
    import uvicorn
    uvicorn.run(
        "app.main:app",
        host=settings.host,
        port=settings.port,
        reload=settings.debug,
        log_level="debug" if settings.debug else "info"
    )
