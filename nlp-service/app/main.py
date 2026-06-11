from fastapi import FastAPI, File, UploadFile, Form, HTTPException, Depends
from fastapi.middleware.cors import CORSMiddleware
from loguru import logger
import sys
import time

from app.config import get_settings
from app.schemas import (
    OcrProcessRequest, OcrProcessResponse,
    NerExtractRequest, NerExtractResponse,
    HealthResponse
)
from app.services.ner_extract_service import NerExtractService


settings = get_settings()

logger.remove()
logger.add(sys.stdout, level="DEBUG" if settings.debug else "INFO")
logger.add("./logs/nlp-service.log", rotation="50 MB", retention="30 days", level="INFO")


app = FastAPI(
    title=settings.app_name,
    version=settings.app_version,
    description="手术记录结构化提取 NLP 微服务 - OCR识别 + 实体抽取 + 规则引擎"
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

_ner_service: NerExtractService = None


def get_ner_service() -> NerExtractService:
    global _ner_service
    if _ner_service is None:
        _ner_service = NerExtractService()
    return _ner_service


@app.on_event("startup")
async def startup_event():
    logger.info(f"启动NLP服务: {settings.app_name} v{settings.app_version}")
    logger.info(f"监听地址: {settings.host}:{settings.port}")
    try:
        get_ner_service()
        logger.info("NLP服务初始化成功")
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
        raise HTTPException(status_code=500, detail=str(e))


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
            error_message="文件路径为空",
            processing_time_ms=int((time.time() - start) * 1000)
        )

    try:
        import os
        if os.path.exists(file_path):
            with open(file_path, "rb") as f:
                content = f.read()
            filename = os.path.basename(file_path)
            result = service.process_ocr(content, filename, request.file_type)
        else:
            result = {
                "success": False,
                "ocr_text": None,
                "processed_text": None,
                "error_message": f"文件不存在: {file_path}"
            }
        result["processing_time_ms"] = int((time.time() - start) * 1000)
        return OcrProcessResponse(**result)
    except Exception as e:
        logger.error(f"OCR文本处理异常: {e}", exc_info=True)
        raise HTTPException(status_code=500, detail=str(e))


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
