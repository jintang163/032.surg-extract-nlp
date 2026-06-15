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
    department: Optional[str] = Field(None, description="科室名称，用于加载科室自定义NER模型")
    entity_types: Optional[List[str]] = Field(None, description="指定要抽取的实体类型列表")
    domain: Optional[str] = Field("surgery", description="领域")
    include_confidence: Optional[bool] = Field(True, description="是否返回置信度")


class NerExtractResponse(BaseModel):
    success: bool = Field(..., description="是否成功")
    entities: Optional[List[NerEntity]] = Field(None, description="抽取的实体列表")
    error_message: Optional[str] = Field(None, description="错误信息")
    processing_time_ms: Optional[int] = Field(None, description="处理耗时(ms)")


class HealthResponse(BaseModel):
    status: str = "healthy"
    version: str = "1.0.0"
    timestamp: datetime = Field(default_factory=datetime.now)


class PcsCodeComponents(BaseModel):
    section: str = Field(..., description="ICD-10-PCS第1位: 章节")
    body_system: str = Field(..., description="ICD-10-PCS第2位: 身体系统")
    root_operation: str = Field(..., description="ICD-10-PCS第3位: 根操作")
    body_part: str = Field(..., description="ICD-10-PCS第4位: 身体部位")
    approach: str = Field(..., description="ICD-10-PCS第5位: 入路")
    device: str = Field(..., description="ICD-10-PCS第6位: 器械/装置")
    qualifier: str = Field(..., description="ICD-10-PCS第7位: 修饰符")


class PcsCodeRecommendationItem(BaseModel):
    pcs_code: str = Field(..., description="ICD-10-PCS 7位编码")
    code_components: PcsCodeComponents = Field(..., description="编码7个组成部分")
    description: str = Field(..., description="编码描述")
    confidence: float = Field(..., ge=0.0, le=1.0, description="置信度")
    match_path: List[str] = Field(default_factory=list, description="决策树匹配路径")
    matched_rules: List[str] = Field(default_factory=list, description="命中的规则名称")
    missing_fields: List[str] = Field(default_factory=list, description="缺失的编码字段")
    is_complete: bool = Field(False, description="编码7位是否全部确定")


class Icd10PcsRecommendRequest(BaseModel):
    record_id: Optional[int] = Field(None, description="手术记录ID")
    entities: List[NerEntity] = Field(..., description="已抽取的实体列表")
    top_k: Optional[int] = Field(5, ge=1, le=20, description="返回Top-K推荐编码")


class Icd10PcsRecommendResponse(BaseModel):
    success: bool = Field(..., description="是否成功")
    parsed_entities: Optional[Dict[str, Any]] = Field(None, description="解析后的结构化实体")
    recommendations: Optional[List[PcsCodeRecommendationItem]] = Field([], description="推荐编码列表")
    top_code: Optional[PcsCodeRecommendationItem] = Field(None, description="最佳推荐编码")
    processing_time_ms: Optional[int] = Field(None, description="处理耗时(ms)")
    error_message: Optional[str] = Field(None, description="错误信息")


class Icd10PcsConfirmRequest(BaseModel):
    record_id: int = Field(..., description="手术记录ID")
    pcs_code: str = Field(..., description="医生确认的ICD-10-PCS编码", min_length=7, max_length=7)
    user_id: Optional[str] = Field(None, description="确认医生ID")
    user_name: Optional[str] = Field(None, description="确认医生姓名")
    source: Optional[str] = Field("manual_confirm", description="确认来源:manual_confirm/ai_adopt/auto_fill")
    recommended_code: Optional[str] = Field(None, description="系统推荐的编码(用于对比)")
    recommendation_confidence: Optional[float] = Field(None, description="系统推荐的置信度")
    additional_data: Optional[Dict[str, Any]] = Field(None, description="附加信息")


class Icd10PcsConfirmResponse(BaseModel):
    success: bool = Field(..., description="是否成功")
    confirmation: Optional[Dict[str, Any]] = Field(None, description="确认记录")
    error_message: Optional[str] = Field(None, description="错误信息")


class Icd10PcsHistoryResponse(BaseModel):
    success: bool = Field(..., description="是否成功")
    history: Optional[List[Dict[str, Any]]] = Field([], description="编码确认历史")
    total: Optional[int] = Field(0, description="历史总数")
    error_message: Optional[str] = Field(None, description="错误信息")


class Icd10PcsKnowledgeResponse(BaseModel):
    success: bool = Field(..., description="是否成功")
    sections: Optional[Dict[str, str]] = Field(None, description="章节编码表")
    root_operations: Optional[Dict[str, str]] = Field(None, description="根操作编码表")
    body_parts: Optional[Dict[str, str]] = Field(None, description="身体部位编码表")
    approaches: Optional[Dict[str, str]] = Field(None, description="入路编码表")
    devices: Optional[Dict[str, str]] = Field(None, description="器械/装置编码表")
    qualifiers: Optional[Dict[str, str]] = Field(None, description="修饰符编码表")
    operation_synonyms: Optional[Dict[str, List[str]]] = Field(None, description="手术方式同义词表")
    body_part_keywords: Optional[Dict[str, List[str]]] = Field(None, description="部位关键词表")

