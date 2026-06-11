from pydantic import BaseModel, Field
from typing import List, Optional, Dict, Any
from datetime import datetime


class NerEntity(BaseModel):
    entity_type: str = Field(..., description="实体类型")
    entity_value: str = Field(..., description="实体值")
    entity_unit: Optional[str] = Field(None, description="单位")
    confidence: Optional[float] = Field(None, ge=0.0, le=1.0, description="置信度")
    source: str = Field("MODEL", description="来源: MODEL, REGEX, RULE, MANUAL, ASR, IMAGE")
    start_pos: Optional[int] = Field(None, description="起始位置")
    end_pos: Optional[int] = Field(None, description="结束位置")
    original_text: Optional[str] = Field(None, description="原文片段")
    category: Optional[str] = Field(None, description="分类（如器械分类）")
    count: Optional[int] = Field(None, description="数量")


class OcrProcessRequest(BaseModel):
    record_id: Optional[int] = Field(None, description="记录ID")
    file_path: Optional[str] = Field(None, description="文件路径")
    file_type: Optional[str] = Field(None, description="文件类型")


class OcrProcessResponse(BaseModel):
    success: bool = Field(..., description="是否成功")
    ocr_text: Optional[str] = Field(None, description="OCR原始文本")
    processed_text: Optional[str] = Field(None, description="预处理后文本")
    error_message: Optional[str] = Field(None, description="错误信息")
    processing_time_ms: Optional[int] = Field(None, description="处理耗时(ms)")


class AsrProcessRequest(BaseModel):
    record_id: Optional[int] = Field(None, description="记录ID")
    file_path: Optional[str] = Field(None, description="音频/视频文件路径")
    file_type: Optional[str] = Field(None, description="文件类型: AUDIO/VIDEO")
    language: Optional[str] = Field("zh", description="语言: zh/en")


class AsrSegment(BaseModel):
    start: float = Field(..., description="开始时间(s)")
    end: float = Field(..., description="结束时间(s)")
    text: str = Field(..., description="转写文本")
    confidence: Optional[float] = Field(None, description="置信度")


class AsrProcessResponse(BaseModel):
    success: bool = Field(..., description="是否成功")
    full_text: Optional[str] = Field(None, description="完整转写文本")
    segments: Optional[List[AsrSegment]] = Field(None, description="分段转写结果")
    duration: Optional[float] = Field(None, description="音频时长(s)")
    language: Optional[str] = Field(None, description="识别到的语言")
    source_type: Optional[str] = Field(None, description="来源类型: audio/video")
    error_message: Optional[str] = Field(None, description="错误信息")
    processing_time_ms: Optional[int] = Field(None, description="处理耗时(ms)")


class InstrumentRecognitionRequest(BaseModel):
    record_id: Optional[int] = Field(None, description="记录ID")
    file_path: Optional[str] = Field(None, description="图片文件路径")
    mode: Optional[str] = Field("hybrid", description="识别模式: detection/classification/text_based/hybrid")
    confidence_threshold: Optional[float] = Field(0.3, description="置信度阈值")


class DetectedInstrument(BaseModel):
    name: str = Field(..., description="器械名称")
    instrument_name: Optional[str] = Field(None, description="器械名称（别名）")
    instrument_code: Optional[str] = Field(None, description="器械编码")
    confidence: float = Field(..., description="置信度", ge=0.0, le=1.0)
    bbox: Optional[List[float]] = Field(None, description="边界框 [x1, y1, x2, y2]")
    position: Optional[Dict[str, float]] = Field(None, description="位置 {x, y, width, height}")
    category: Optional[str] = Field(None, description="器械分类")
    specialty: Optional[str] = Field(None, description="所属专科")
    count: Optional[int] = Field(None, description="数量")


class InstrumentRecognitionResponse(BaseModel):
    success: bool = Field(..., description="是否成功")
    instruments: Optional[List[DetectedInstrument]] = Field([], description="识别到的器械列表")
    mode_used: Optional[str] = Field(None, description="实际使用的识别模式")
    image_size: Optional[Dict[str, int]] = Field(None, description="图片尺寸")
    error_message: Optional[str] = Field(None, description="错误信息")
    processing_time_ms: Optional[int] = Field(None, description="处理耗时(ms)")


class InstrumentCatalogResponse(BaseModel):
    total_count: int = Field(..., description="支持的器械总数")
    categories: Dict[str, List[str]] = Field(..., description="按分类的器械列表")
    specialties: Optional[List[str]] = Field(None, description="专科列表")
    instruments_by_specialty: Optional[Dict[str, List[str]]] = Field(None, description="按专科的器械列表")


class MultimodalFusionRequest(BaseModel):
    record_id: Optional[int] = Field(None, description="记录ID")
    ocr_text: Optional[str] = Field(None, description="OCR识别文本")
    asr_result: Optional[Dict[str, Any]] = Field(None, description="ASR识别结果")
    instrument_result: Optional[Dict[str, Any]] = Field(None, description="器械识别结果")
    ner_entities: Optional[List[NerEntity]] = Field(None, description="NER实体列表")
    regex_entities: Optional[List[NerEntity]] = Field(None, description="正则实体列表")
    rule_entities: Optional[List[NerEntity]] = Field(None, description="规则实体列表")
    rerun_ner: Optional[bool] = Field(True, description="是否用增强文本重跑NER")


class MultimodalFusionResponse(BaseModel):
    success: bool = Field(..., description="是否成功")
    enhanced_text: Optional[str] = Field(None, description="融合后的增强文本")
    entities: Optional[List[NerEntity]] = Field([], description="融合后的实体列表")
    instruments: Optional[List[DetectedInstrument]] = Field([], description="器械列表")
    fusion_stats: Optional[Dict[str, Any]] = Field(None, description="融合统计信息")
    source_breakdown: Optional[Dict[str, int]] = Field(None, description="各来源实体数量")
    error_message: Optional[str] = Field(None, description="错误信息")
    processing_time_ms: Optional[int] = Field(None, description="处理耗时(ms)")


class NerExtractRequest(BaseModel):
    record_id: Optional[int] = Field(None, description="记录ID")
    text: str = Field(..., description="待抽取文本", min_length=1)


class NerExtractResponse(BaseModel):
    success: bool = Field(..., description="是否成功")
    entities: Optional[List[NerEntity]] = Field(None, description="抽取的实体列表")
    error_message: Optional[str] = Field(None, description="错误信息")
    processing_time_ms: Optional[int] = Field(None, description="处理耗时(ms)")


class HealthResponse(BaseModel):
    status: str = "healthy"
    version: str = "1.0.0"
    timestamp: datetime = Field(default_factory=datetime.now)

