from fastapi import FastAPI, File, UploadFile, Form, HTTPException, Depends, Header
from fastapi.middleware.cors import CORSMiddleware
from loguru import logger
from typing import Optional
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
    HealthResponse,
    Icd10PcsRecommendRequest, Icd10PcsRecommendResponse,
    Icd10PcsConfirmRequest, Icd10PcsConfirmResponse,
    Icd10PcsHistoryResponse, Icd10PcsKnowledgeResponse,
    NerEntity
)
from app.services.ner_extract_service import NerExtractService
from app.services.custom_ner_service import CustomNerService
from app.services.asr_service import AsrService, AsrProcessingError, AsrDependencyError
from app.services.instrument_recognition_service import (
    InstrumentRecognitionService,
    InstrumentProcessingError,
    InstrumentDependencyError,
)
from app.services.multimodal_fusion_service import MultimodalFusionService
from app.services.privacy_guard import get_data_masker, get_audit_logger
from app.services.federated_client import get_federated_client
from app.services.federated_aggregator import get_federated_aggregator
from app.services.differential_privacy import DPConfig
from app.services.icd10pcs_coding import get_icd10pcs_service


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
_custom_ner_service: CustomNerService = None
_asr_service: AsrService = None
_instrument_service: InstrumentRecognitionService = None
_fusion_service: MultimodalFusionService = None


def get_ner_service() -> NerExtractService:
    global _ner_service
    if _ner_service is None:
        _ner_service = NerExtractService()
    return _ner_service


def get_custom_ner_service() -> CustomNerService:
    global _custom_ner_service
    if _custom_ner_service is None:
        _custom_ner_service = CustomNerService()
    return _custom_ner_service


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
    logger.info(f"离线模式: {settings.offline_mode}")
    logger.info(f"BERT本地路径: {settings.bert_local_path}")

    try:
        from app.services.gpu_optimizer import get_gpu_optimizer
        gpu_opt = get_gpu_optimizer()
        gpu_info = gpu_opt.get_gpu_info()
        logger.info(f"GPU状态: {gpu_info}")
    except Exception as e:
        logger.warning(f"GPU优化器初始化异常: {e}")

    try:
        get_ner_service()
        get_custom_ner_service()
        get_asr_service()
        get_instrument_service()
        get_fusion_service()
        get_icd10pcs_service()
        logger.info("NLP服务（含多模态、自定义NER、ICD-10-PCS手术编码）初始化成功")
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
            rerun_ner=request.rerun_ner if request.rerun_ner is not None else True,
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
    logger.info(f"NER抽取请求: record_id={request.record_id}, text_len={len(request.text)}, department={request.department}")
    start = time.time()

    try:
        result = service.extract_entities(
            request.text,
            request.record_id,
            request.department,
            request.entity_types
        )
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


@app.post("/api/v1/ner/custom/train", tags=["实体抽取"])
async def api_custom_ner_train(
    request: dict,
    service: CustomNerService = Depends(get_custom_ner_service)
):
    """训练自定义NER模型（少样本学习）"""
    field_id = request.get("field_id")
    department = request.get("department", "")
    field_code = request.get("field_code", "")
    entity_type = request.get("entity_type", f"CUSTOM_{field_code.upper()}")
    samples = request.get("samples", [])

    logger.info(f"自定义NER训练请求: field_id={field_id}, department={department}, field_code={field_code}, samples={len(samples)}")

    try:
        result = service.train_model(
            field_id=field_id,
            department=department,
            field_code=field_code,
            entity_type=entity_type,
            samples=samples
        )
        return result
    except Exception as e:
        logger.error(f"自定义NER训练异常: {e}", exc_info=True)
        return {
            "success": False,
            "message": f"训练失败: {str(e)}",
            "field_id": field_id
        }


@app.get("/api/v1/ner/custom/status/{field_id}", tags=["实体抽取"])
async def api_custom_ner_status(
    field_id: int,
    service: CustomNerService = Depends(get_custom_ner_service)
):
    """获取自定义NER模型训练状态"""
    try:
        return service.get_model_status(field_id)
    except Exception as e:
        logger.error(f"获取自定义NER状态异常: {e}", exc_info=True)
        return {
            "success": False,
            "field_id": field_id,
            "message": str(e)
        }


@app.get("/api/v1/ner/custom/department/{department}", tags=["实体抽取"])
async def api_custom_ner_by_department(
    department: str,
    service: CustomNerService = Depends(get_custom_ner_service)
):
    """获取某科室的所有自定义NER模型"""
    try:
        models = service.get_fields_by_department(department)
        return {
            "success": True,
            "department": department,
            "models": models,
            "count": len(models)
        }
    except Exception as e:
        logger.error(f"获取科室自定义NER列表异常: {e}", exc_info=True)
        return {
            "success": False,
            "department": department,
            "message": str(e)
        }


@app.post("/api/v1/ner/custom/extract", tags=["实体抽取"])
async def api_custom_ner_extract(
    request: dict,
    service: CustomNerService = Depends(get_custom_ner_service)
):
    """使用自定义NER模型抽取实体"""
    text = request.get("text", "")
    department = request.get("department")
    entity_types = request.get("entity_types")

    logger.info(f"自定义NER抽取请求: department={department}, text_len={len(text)}")
    start = time.time()

    try:
        entities = service.extract_entities(text, department, entity_types)
        return {
            "success": True,
            "entities": entities,
            "processing_time_ms": int((time.time() - start) * 1000)
        }
    except Exception as e:
        logger.error(f"自定义NER抽取异常: {e}", exc_info=True)
        return {
            "success": False,
            "entities": [],
            "error_message": str(e),
            "processing_time_ms": int((time.time() - start) * 1000)
        }


@app.post("/api/v1/ner/incremental-train", tags=["实体抽取", "主动学习"])
async def api_incremental_train(
    train_data: dict,
    x_train_params: str = Header(None, alias="X-Train-Params")
):
    """基于医生反馈数据的增量微调训练接口 - 调用真实训练脚本"""
    import subprocess
    import sys
    import os
    import json
    from datetime import datetime
    import threading

    params = {}
    if x_train_params:
        try:
            params = json.loads(x_train_params)
        except Exception:
            params = {}

    logger.info(f"增量训练请求: 反馈样本数={len(train_data.get('samples', []))}")

    try:
        samples = train_data.get("samples", [])
        sample_count = len(samples)
        epochs = (params or {}).get("epochs", 10)
        batch_size = (params or {}).get("batchSize", 16)
        learning_rate = (params or {}).get("learningRate", 0.001)
        train_type = (params or {}).get("trainType", "INCREMENTAL")
        min_quality = (params or {}).get("minQualityScore", 60)
        max_feedback = (params or {}).get("maxFeedbackCount", 5000)

        batch_no = f"TRAIN-{datetime.now().strftime('%Y%m%d')}-{__import__('random').randint(1000,9999)}"

        feedback_dir = os.path.join(settings.temp_dir, "train_feedback", batch_no)
        os.makedirs(feedback_dir, exist_ok=True)

        feedback_file = os.path.join(feedback_dir, "feedback_samples.json")
        with open(feedback_file, "w", encoding="utf-8") as f:
            json.dump(samples, f, ensure_ascii=False, indent=2)

        cmd = [
            sys.executable,
            settings.train_script_path,
            "--db_host", settings.db_host,
            "--db_port", str(settings.db_port),
            "--db_user", settings.db_user,
            "--db_password", settings.db_password,
            "--db_name", settings.db_name,
            "--model_dir", settings.train_model_dir,
            "--output_dir", settings.train_output_dir,
            "--epochs", str(epochs),
            "--batch_size", str(batch_size),
            "--learning_rate", str(learning_rate),
            "--max_feedback", str(max_feedback),
            "--min_quality", str(min_quality),
            "--train_type", train_type,
            "--feedback_file", feedback_file,
        ]

        if settings.train_base_data:
            cmd.extend(["--train_data", settings.train_base_data])

        logger.info(f"启动训练脚本: {' '.join(cmd)}")

        result_file = os.path.join(feedback_dir, "train_result.json")
        log_file = os.path.join(feedback_dir, "train.log")

        def run_training():
            try:
                with open(log_file, "w", encoding="utf-8") as log_f:
                    process = subprocess.Popen(
                        cmd,
                        stdout=log_f,
                        stderr=subprocess.STDOUT,
                        cwd=os.path.dirname(settings.train_script_path) or ".",
                        env={**os.environ, "PYTHONUNBUFFERED": "1"}
                    )
                    return_code = process.wait()

                result = {
                    "success": return_code == 0,
                    "trainBatchNo": batch_no,
                    "returnCode": return_code,
                }

                output_result = os.path.join(settings.train_output_dir, "train_result.json")
                if os.path.exists(output_result):
                    with open(output_result, "r", encoding="utf-8") as f:
                        script_result = json.load(f)
                    result.update(script_result)

                if return_code != 0 and os.path.exists(log_file):
                    with open(log_file, "r", encoding="utf-8") as f:
                        tail = f.read()[-2000:]
                    result["failReason"] = tail

                with open(result_file, "w", encoding="utf-8") as f:
                    json.dump(result, f, ensure_ascii=False, indent=2)

                logger.info(f"训练脚本完成: batchNo={batch_no}, returnCode={return_code}")
            except Exception as e:
                logger.error(f"训练脚本执行异常: {e}", exc_info=True)
                error_result = {
                    "success": False,
                    "trainBatchNo": batch_no,
                    "failReason": str(e),
                }
                with open(result_file, "w", encoding="utf-8") as f:
                    json.dump(error_result, f, ensure_ascii=False, indent=2)

        train_thread = threading.Thread(target=run_training, daemon=True)
        train_thread.start()

        return {
            "success": True,
            "trainBatchNo": batch_no,
            "message": "训练任务已启动，脚本正在后台执行",
            "feedbackCount": sample_count,
            "logFile": log_file,
            "resultFile": result_file,
        }

    except Exception as e:
        logger.error(f"启动训练异常: {e}", exc_info=True)
        return {
            "success": False,
            "message": f"启动训练失败: {str(e)}"
        }


@app.get("/api/v1/ner/incremental-train/status", tags=["实体抽取", "主动学习"])
async def api_incremental_train_status(batch_no: str):
    """查询增量训练任务状态"""
    import os
    import json

    feedback_dir = os.path.join(settings.temp_dir, "train_feedback", batch_no)
    result_file = os.path.join(feedback_dir, "train_result.json")
    log_file = os.path.join(feedback_dir, "train.log")

    result = {"batchNo": batch_no, "status": "UNKNOWN"}

    if os.path.exists(result_file):
        with open(result_file, "r", encoding="utf-8") as f:
            train_result = json.load(f)
        result["status"] = "COMPLETED" if train_result.get("success") else "FAILED"
        result["result"] = train_result
    elif os.path.exists(log_file):
        result["status"] = "RUNNING"
        with open(log_file, "r", encoding="utf-8") as f:
            content = f.read()
        result["logTail"] = content[-2000:] if content else ""
    else:
        result["status"] = "NOT_FOUND"

    return result


@app.post("/api/v1/ner/weekly-train", tags=["实体抽取", "主动学习"])
async def api_weekly_train():
    """触发周度自动训练（由定时调度或手动触发）"""
    logger.info("周度自动训练触发")

    train_data = {"samples": []}
    params = {
        "trainType": "WEEKLY",
        "epochs": 10,
        "batchSize": 16,
        "learningRate": 0.001,
        "minQualityScore": 60,
        "maxFeedbackCount": 5000,
    }
    return await api_incremental_train(train_data, params)


@app.post("/api/v1/privacy/mask", tags=["隐私保护"])
async def api_privacy_mask(request: dict):
    text = request.get("text", "")
    entities = request.get("entities", [])
    fields = request.get("fields")

    logger.info(f"数据脱敏请求: text_len={len(text)}, entities={len(entities)}")
    start = time.time()

    try:
        masker = get_data_masker()
        result = {}

        if text:
            result["masked_text"] = masker.mask_text(text, fields)

        if entities:
            result["masked_entities"] = masker.mask_entities(entities)

        result["processing_time_ms"] = int((time.time() - start) * 1000)
        result["success"] = True

        return result
    except Exception as e:
        logger.error(f"数据脱敏异常: {e}", exc_info=True)
        return {
            "success": False,
            "error_message": f"脱敏处理异常: {str(e)}",
            "processing_time_ms": int((time.time() - start) * 1000),
        }


@app.get("/api/v1/privacy/mask-fields", tags=["隐私保护"])
async def api_privacy_mask_fields():
    try:
        masker = get_data_masker()
        fields = []
        for name, field in masker.sensitive_fields.items():
            fields.append({
                "name": name,
                "description": name,
                "enabled": field.enabled,
                "mask_type": field.mask_type,
            })

        return {
            "success": True,
            "fields": fields,
            "count": len(fields),
        }
    except Exception as e:
        logger.error(f"获取脱敏字段异常: {e}", exc_info=True)
        return {
            "success": False,
            "error_message": str(e),
        }


@app.get("/api/v1/privacy/audit/logs", tags=["隐私保护"])
async def api_privacy_audit_logs(
    start_time: str = None,
    end_time: str = None,
    event_type: str = None,
    user_id: str = None,
    patient_id: str = None,
    limit: int = 100,
):
    logger.info(f"查询审计日志: event_type={event_type}, user_id={user_id}")

    try:
        audit_logger = get_audit_logger()
        logs = audit_logger.query_logs(
            start_time=start_time,
            end_time=end_time,
            event_type=event_type,
            user_id=user_id,
            patient_id=patient_id,
            limit=limit,
        )

        return {
            "success": True,
            "logs": logs,
            "count": len(logs),
        }
    except Exception as e:
        logger.error(f"查询审计日志异常: {e}", exc_info=True)
        return {
            "success": False,
            "error_message": str(e),
        }


@app.get("/api/v1/privacy/audit/statistics", tags=["隐私保护"])
async def api_privacy_audit_statistics(days: int = 30):
    logger.info(f"获取审计统计: days={days}")

    try:
        audit_logger = get_audit_logger()
        stats = audit_logger.get_statistics(days=days)

        return {
            "success": True,
            "statistics": stats,
        }
    except Exception as e:
        logger.error(f"获取审计统计异常: {e}", exc_info=True)
        return {
            "success": False,
            "error_message": str(e),
        }


@app.get("/api/v1/privacy/audit/verify", tags=["隐私保护"])
async def api_privacy_audit_verify(checksum: str):
    logger.info(f"验证日志完整性: checksum={checksum[:16]}...")

    try:
        audit_logger = get_audit_logger()
        valid, entry = audit_logger.verify_integrity(checksum)

        return {
            "success": True,
            "valid": valid,
            "entry": entry,
        }
    except Exception as e:
        logger.error(f"验证日志完整性异常: {e}", exc_info=True)
        return {
            "success": False,
            "error_message": str(e),
        }


@app.post("/api/v1/federated/client/local-train", tags=["联邦学习"])
async def api_federated_local_train(request: dict):
    """联邦学习客户端 - 执行本地训练（DP在训练循环中生效）"""
    model_path = request.get("model_path", "./models/surgery-ner")
    epochs = request.get("epochs")
    lr = request.get("lr")
    dp_noise_multiplier = request.get("dp_noise_multiplier")
    dp_max_grad_norm = request.get("dp_max_grad_norm")

    logger.info(f"联邦学习本地训练: model_path={model_path}")
    start = time.time()

    try:
        client = get_federated_client()

        model = client.load_model(model_path)
        if model is None:
            return {
                "success": False,
                "error_message": f"无法加载模型: {model_path}",
                "processing_time_ms": int((time.time() - start) * 1000),
            }

        train_samples = request.get("train_samples", [])
        if not train_samples:
            return {
                "success": False,
                "error_message": "训练数据为空，请提供train_samples",
                "processing_time_ms": int((time.time() - start) * 1000),
            }

        from torch.utils.data import DataLoader, Dataset

        class _SimpleNerDataset(Dataset):
            def __init__(self, samples):
                self.samples = samples
            def __len__(self):
                return len(self.samples)
            def __getitem__(self, idx):
                return self.samples[idx]

        dataset = _SimpleNerDataset(train_samples)
        loader = DataLoader(
            dataset,
            batch_size=client.fl_config["batch_size"],
            shuffle=True,
        )

        dp_config = None
        if dp_noise_multiplier is not None or dp_max_grad_norm is not None:
            dp_config = DPConfig(
                enabled=True,
                noise_multiplier=dp_noise_multiplier or client.fl_config["dp_noise_multiplier"],
                max_grad_norm=dp_max_grad_norm or client.fl_config["dp_max_grad_norm"],
            )

        result = client.local_train(
            model=model,
            train_loader=loader,
            epochs=epochs,
            lr=lr,
            dp_config=dp_config,
        )

        client.save_model(model, model_path)

        result["processing_time_ms"] = int((time.time() - start) * 1000)

        get_audit_logger().log_model_training(
            user_id=client.site_id,
            training_type="federated_local",
            sample_count=result["sample_count"],
            uses_patient_data=True,
            dp_enabled=client.fl_config["dp_enabled"],
        )

        return result
    except Exception as e:
        logger.error(f"联邦学习本地训练异常: {e}", exc_info=True)
        return {
            "success": False,
            "error_message": f"本地训练异常: {str(e)}",
            "processing_time_ms": int((time.time() - start) * 1000),
        }


@app.post("/api/v1/federated/client/export-params", tags=["联邦学习"])
async def api_federated_export_params(request: dict):
    """联邦学习客户端 - 导出模型参数（训练中已应用DP，导出不再加噪）"""
    model_path = request.get("model_path", "./models/surgery-ner")
    output_path = request.get("output_path", "")
    train_sample_count = request.get("train_sample_count", 0)
    encryption_key = request.get("encryption_key")

    logger.info(
        f"联邦学习导出参数: model_path={model_path}, samples={train_sample_count}"
    )
    start = time.time()

    try:
        client = get_federated_client()
        result = client.export_parameters(
            model_path=model_path,
            train_sample_count=train_sample_count,
            output_path=output_path,
            encryption_key_hint=encryption_key,
        )

        result["processing_time_ms"] = int((time.time() - start) * 1000)

        get_audit_logger().log_federated_update(
            site_id=client.site_id,
            site_name=client.site_name,
            round_num=request.get("round_num", 0),
            sample_count=train_sample_count,
            operation="parameter_export",
            dp_applied=client.fl_config["dp_enabled"],
            encrypted=encryption_key is not None,
        )

        return result
    except Exception as e:
        logger.error(f"联邦学习导出参数异常: {e}", exc_info=True)
        return {
            "success": False,
            "error_message": f"导出参数异常: {str(e)}",
            "processing_time_ms": int((time.time() - start) * 1000),
        }


@app.post("/api/v1/federated/client/import-params", tags=["联邦学习"])
async def api_federated_import_params(request: dict):
    """联邦学习客户端 - 导入聚合后的全局模型参数（回灌）"""
    param_file = request.get("param_file", "")
    model_path = request.get("model_path", "./models/surgery-ner")
    output_model_path = request.get("output_model_path", "")
    encryption_key = request.get("encryption_key")

    logger.info(f"联邦学习导入参数: param_file={param_file}")
    start = time.time()

    try:
        client = get_federated_client()
        result = client.import_parameters(
            param_file=param_file,
            model_path=model_path,
            output_model_path=output_model_path,
            encryption_key_hint=encryption_key,
        )

        result["processing_time_ms"] = int((time.time() - start) * 1000)

        get_audit_logger().log_federated_update(
            site_id=client.site_id,
            site_name=client.site_name,
            round_num=request.get("round_num", 0),
            sample_count=0,
            operation="parameter_import",
            dp_applied=False,
            encrypted=encryption_key is not None,
        )

        return result
    except Exception as e:
        logger.error(f"联邦学习导入参数异常: {e}", exc_info=True)
        return {
            "success": False,
            "error_message": f"导入参数异常: {str(e)}",
            "processing_time_ms": int((time.time() - start) * 1000),
        }


@app.get("/api/v1/federated/client/status", tags=["联邦学习"])
async def api_federated_client_status():
    try:
        client = get_federated_client()
        info = client.get_client_info()
        return {"success": True, **info}
    except Exception as e:
        logger.error(f"获取客户端状态异常: {e}", exc_info=True)
        return {"success": False, "error_message": str(e)}


@app.post("/api/v1/federated/aggregator/receive", tags=["联邦学习"])
async def api_federated_receive(request: dict):
    """联邦学习聚合器 - 接收客户端参数更新"""
    client_id = request.get("client_id", "")
    client_name = request.get("client_name", "")
    round_num = request.get("round_num", 0)
    param_data = request.get("param_data", "")
    encryption_key = request.get("encryption_key")

    logger.info(f"联邦学习接收参数: client_id={client_id}, round={round_num}")
    start = time.time()

    try:
        aggregator = get_federated_aggregator()
        result = aggregator.receive_client_update(
            client_id=client_id,
            client_name=client_name,
            round_num=round_num,
            param_data=param_data,
            encryption_key_hint=encryption_key,
        )

        result["processing_time_ms"] = int((time.time() - start) * 1000)

        return result
    except Exception as e:
        logger.error(f"联邦学习接收参数异常: {e}", exc_info=True)
        return {
            "success": False,
            "error_message": f"接收参数异常: {str(e)}",
            "processing_time_ms": int((time.time() - start) * 1000),
        }


@app.post("/api/v1/federated/aggregator/aggregate", tags=["联邦学习"])
async def api_federated_aggregate(request: dict):
    """联邦学习聚合器 - 执行参数聚合"""
    round_num = request.get("round_num")
    algorithm = request.get("algorithm", "fedavg")
    min_clients = request.get("min_clients", 2)
    encryption_key = request.get("encryption_key")

    logger.info(
        f"联邦学习参数聚合: round={round_num}, algorithm={algorithm}, min_clients={min_clients}"
    )
    start = time.time()

    try:
        aggregator = get_federated_aggregator()
        result = aggregator.aggregate(
            round_num=round_num,
            algorithm=algorithm,
            min_clients=min_clients,
            encryption_key_hint=encryption_key,
        )

        result["processing_time_ms"] = int((time.time() - start) * 1000)

        return result
    except Exception as e:
        logger.error(f"联邦学习聚合异常: {e}", exc_info=True)
        return {
            "success": False,
            "error_message": f"聚合异常: {str(e)}",
            "processing_time_ms": int((time.time() - start) * 1000),
        }


@app.get("/api/v1/federated/aggregator/status", tags=["联邦学习"])
async def api_federated_aggregator_status():
    try:
        aggregator = get_federated_aggregator()
        status = aggregator.get_aggregator_status()
        return {
            "success": True,
            **status,
            "supported_algorithms": ["fedavg", "median", "trimmed_mean"],
        }
    except Exception as e:
        logger.error(f"获取聚合器状态异常: {e}", exc_info=True)
        return {"success": False, "error_message": str(e)}


@app.get("/api/v1/federated/aggregator/result/{round_num}", tags=["联邦学习"])
async def api_federated_aggregator_result(round_num: int):
    try:
        aggregator = get_federated_aggregator()
        result = aggregator.get_aggregated_result(round_num)
        if result is None:
            return {
                "success": False,
                "error_message": f"第 {round_num} 轮聚合结果不存在",
            }
        return {
            "success": True,
            "round_num": round_num,
            "result": result,
        }
    except Exception as e:
        logger.error(f"获取聚合结果异常: {e}", exc_info=True)
        return {"success": False, "error_message": str(e)}


@app.get("/api/v1/dp/status", tags=["差分隐私"])
async def api_dp_status():
    try:
        client = get_federated_client()
        if client._dp_engine:
            return {
                "success": True,
                **client._dp_engine.get_privacy_report(),
            }
        return {
            "success": True,
            "enabled": client.fl_config["dp_enabled"],
            "noise_multiplier": client.fl_config["dp_noise_multiplier"],
            "max_grad_norm": client.fl_config["dp_max_grad_norm"],
            "message": "差分隐私引擎将在训练时初始化",
        }
    except Exception as e:
        logger.error(f"获取DP状态异常: {e}", exc_info=True)
        return {"success": False, "error_message": str(e)}


@app.post("/api/v1/dp/configure", tags=["差分隐私"])
async def api_dp_configure(request: dict):
    enabled = request.get("enabled", True)
    noise_multiplier = request.get("noise_multiplier", 1.0)
    max_grad_norm = request.get("max_grad_norm", 1.0)
    target_epsilon = request.get("target_epsilon", 1.0)
    max_epsilon = request.get("max_epsilon", 10.0)

    logger.info(
        f"配置差分隐私: enabled={enabled}, noise_multiplier={noise_multiplier}, "
        f"max_grad_norm={max_grad_norm}, target_epsilon={target_epsilon}"
    )

    try:
        client = get_federated_client()
        client.fl_config["dp_enabled"] = enabled
        client.fl_config["dp_noise_multiplier"] = noise_multiplier
        client.fl_config["dp_max_grad_norm"] = max_grad_norm

        return {
            "success": True,
            "message": "差分隐私配置已更新，将在下次训练时生效",
            "config": {
                "enabled": enabled,
                "noise_multiplier": noise_multiplier,
                "max_grad_norm": max_grad_norm,
                "target_epsilon": target_epsilon,
                "max_epsilon": max_epsilon,
            },
        }
    except Exception as e:
        logger.error(f"配置DP异常: {e}", exc_info=True)
        return {"success": False, "error_message": str(e)}


@app.post("/api/v1/icd10-pcs/recommend",
          response_model=Icd10PcsRecommendResponse,
          tags=["ICD-10-PCS手术编码"])
async def api_icd10pcs_recommend(
    request: Icd10PcsRecommendRequest,
):
    logger.info(f"ICD-10-PCS编码推荐请求: record_id={request.record_id}, entities={len(request.entities)}")
    start = time.time()

    try:
        service = get_icd10pcs_service()
        entities_dict = [e.model_dump() for e in request.entities]
        result = service.recommend_codes(
            entities=entities_dict,
            record_id=request.record_id,
            top_k=request.top_k or 5,
        )
        result["processing_time_ms"] = int((time.time() - start) * 1000)
        return Icd10PcsRecommendResponse(**result)
    except Exception as e:
        logger.error(f"ICD-10-PCS编码推荐异常: {e}", exc_info=True)
        return Icd10PcsRecommendResponse(
            success=False,
            parsed_entities={},
            recommendations=[],
            top_code=None,
            error_message=f"编码推荐服务异常: {str(e)}",
            processing_time_ms=int((time.time() - start) * 1000),
        )


@app.post("/api/v1/icd10-pcs/confirm",
          response_model=Icd10PcsConfirmResponse,
          tags=["ICD-10-PCS手术编码"])
async def api_icd10pcs_confirm(
    request: Icd10PcsConfirmRequest,
):
    logger.info(f"ICD-10-PCS编码确认请求: record_id={request.record_id}, pcs_code={request.pcs_code}")
    try:
        service = get_icd10pcs_service()
        result = service.confirm_code(
            record_id=request.record_id,
            pcs_code=request.pcs_code.upper(),
            user_id=request.user_id,
        )
        return Icd10PcsConfirmResponse(**result)
    except Exception as e:
        logger.error(f"ICD-10-PCS编码确认异常: {e}", exc_info=True)
        return Icd10PcsConfirmResponse(
            success=False,
            confirmation=None,
            error_message=f"编码确认异常: {str(e)}",
        )


@app.get("/api/v1/icd10-pcs/history",
         response_model=Icd10PcsHistoryResponse,
         tags=["ICD-10-PCS手术编码"])
async def api_icd10pcs_history(
    record_id: Optional[int] = None,
    limit: int = 100,
):
    logger.info(f"ICD-10-PCS编码历史查询: record_id={record_id}, limit={limit}")
    try:
        service = get_icd10pcs_service()
        result = service.get_coding_history(record_id=record_id, limit=limit)
        return Icd10PcsHistoryResponse(**result)
    except Exception as e:
        logger.error(f"ICD-10-PCS编码历史查询异常: {e}", exc_info=True)
        return Icd10PcsHistoryResponse(
            success=False,
            history=[],
            total=0,
            error_message=f"历史查询异常: {str(e)}",
        )


@app.get("/api/v1/icd10-pcs/knowledge",
         response_model=Icd10PcsKnowledgeResponse,
         tags=["ICD-10-PCS手术编码"])
async def api_icd10pcs_knowledge():
    try:
        service = get_icd10pcs_service()
        result = service.get_coding_knowledge()
        return Icd10PcsKnowledgeResponse(**result)
    except Exception as e:
        logger.error(f"ICD-10-PCS编码知识库查询异常: {e}", exc_info=True)
        return Icd10PcsKnowledgeResponse(
            success=False,
            error_message=f"知识库查询异常: {str(e)}",
        )


@app.post("/api/v1/icd10-pcs/recommend-from-text",
          response_model=Icd10PcsRecommendResponse,
          tags=["ICD-10-PCS手术编码"])
async def api_icd10pcs_recommend_from_text(
    text: str = Form(..., description="手术记录文本", min_length=1),
    record_id: Optional[int] = Form(None, description="手术记录ID"),
    top_k: int = Form(5, ge=1, le=20, description="返回Top-K推荐编码"),
    department: Optional[str] = Form(None, description="科室名称"),
    ner_service: NerExtractService = Depends(get_ner_service),
):
    logger.info(f"ICD-10-PCS文本直接编码: record_id={record_id}, text_len={len(text)}")
    start = time.time()

    try:
        extract_result = ner_service.extract_entities(
            text=text,
            record_id=record_id,
            department=department,
        )
        if not extract_result.get("success"):
            return Icd10PcsRecommendResponse(
                success=False,
                parsed_entities={},
                recommendations=[],
                top_code=None,
                error_message=extract_result.get("error_message", "实体抽取失败"),
                processing_time_ms=int((time.time() - start) * 1000),
            )

        entities = extract_result.get("entities", [])
        coding_service = get_icd10pcs_service()
        result = coding_service.recommend_codes(
            entities=entities,
            record_id=record_id,
            top_k=top_k,
        )
        result["processing_time_ms"] = int((time.time() - start) * 1000)
        return Icd10PcsRecommendResponse(**result)
    except Exception as e:
        logger.error(f"ICD-10-PCS文本直接编码异常: {e}", exc_info=True)
        return Icd10PcsRecommendResponse(
            success=False,
            parsed_entities={},
            recommendations=[],
            top_code=None,
            error_message=f"文本编码服务异常: {str(e)}",
            processing_time_ms=int((time.time() - start) * 1000),
        )


@app.get("/api/v1/system/offline-status", tags=["系统"])
async def api_offline_status():
    try:
        import os
        from app.services.gpu_optimizer import get_gpu_optimizer

        gpu_opt = get_gpu_optimizer()
        gpu_info = gpu_opt.get_gpu_info()

        bert_path = settings.bert_local_path
        bert_exists = os.path.isdir(bert_path) and os.path.exists(os.path.join(bert_path, "pytorch_model.bin"))

        ner_model_path = os.path.join(settings.model_dir, "surgery-ner")
        ner_model_exists = os.path.isdir(ner_model_path) and os.path.exists(os.path.join(ner_model_path, "pytorch_model.bin"))

        return {
            "success": True,
            "offline_mode": settings.offline_mode,
            "bert_local_path": bert_path,
            "bert_model_ready": bert_exists,
            "ner_model_path": ner_model_path,
            "ner_model_ready": ner_model_exists,
            "gpu_available": gpu_info.get("gpu_available", False),
            "gpu_info": gpu_info,
            "privacy_protection": {
                "data_masking": True,
                "differential_privacy": True,
                "federated_learning": True,
                "audit_logging": True,
            },
        }
    except Exception as e:
        logger.error(f"获取离线状态异常: {e}", exc_info=True)
        return {"success": False, "error_message": str(e)}


if __name__ == "__main__":
    import uvicorn
    uvicorn.run(
        "app.main:app",
        host=settings.host,
        port=settings.port,
        reload=settings.debug,
        log_level="debug" if settings.debug else "info"
    )
