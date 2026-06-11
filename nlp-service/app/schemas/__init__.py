from pydantic import BaseModel, Field
from typing import List, Optional
from datetime import datetime


class NerEntity(BaseModel):
    entity_type: str = Field(..., description="实体类型")
    entity_value: str = Field(..., description="实体值")
    entity_unit: Optional[str] = Field(None, description="单位")
    confidence: Optional[float] = Field(None, ge=0.0, le=1.0, description="置信度")
    source: str = Field("MODEL", description="来源: MODEL, REGEX, RULE, MANUAL")
    start_pos: Optional[int] = Field(None, description="起始位置")
    end_pos: Optional[int] = Field(None, description="结束位置")
    original_text: Optional[str] = Field(None, description="原文片段")


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
