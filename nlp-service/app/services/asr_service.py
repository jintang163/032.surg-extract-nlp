"""
ASR 语音识别服务 - 手术视频/音频旁白转文字

支持:
  - 视频文件提取音频（需ffmpeg）
  - 多种音频格式（wav, mp3, m4a, flac等）
  - 可选ASR引擎: OpenAI Whisper / SpeechRecognition / 百度语音API
  - 失败时抛出明确异常，不返回Mock数据
  - 按时间戳分段转写，保留时间轴信息
"""

import os
import io
import re
import subprocess
import tempfile
from pathlib import Path
from loguru import logger
from typing import List, Dict, Any, Optional, Tuple

from app.config import get_settings


class AsrDependencyError(Exception):
    def __init__(self, message: str, detail: str = ""):
        super().__init__(message)
        self.detail = detail


class AsrProcessingError(Exception):
    def __init__(self, message: str, stage: str = "unknown", detail: str = ""):
        super().__init__(message)
        self.stage = stage
        self.detail = detail


class AsrSegment:
    def __init__(self, start: float, end: float, text: str, confidence: float = 0.0):
        self.start = start
        self.end = end
        self.text = text
        self.confidence = confidence

    def to_dict(self) -> Dict[str, Any]:
        return {
            "start": round(self.start, 2),
            "end": round(self.end, 2),
            "text": self.text,
            "confidence": round(self.confidence, 4) if self.confidence else None,
        }


class AsrService:
    def __init__(self):
        self.settings = get_settings()
        self._ensure_dirs()
        self._check_dependencies()
        self._whisper_model = None
        self._recognizer = None

    def _ensure_dirs(self):
        for d in [self.settings.temp_dir, self.settings.upload_dir]:
            Path(d).mkdir(parents=True, exist_ok=True)

    def _check_dependencies(self):
        missing = []
        self._has_ffmpeg = self._check_ffmpeg()
        if not self._has_ffmpeg:
            logger.warning(
                "[ASR] 未检测到ffmpeg，视频文件音频提取功能不可用。"
                "请安装: https://ffmpeg.org/download.html"
            )
        try:
            import whisper
            self._has_whisper = True
        except ImportError:
            self._has_whisper = False
            logger.warning(
                "[ASR] 未安装openai-whisper，Whisper ASR引擎不可用。"
                "请执行: pip install openai-whisper"
            )
        try:
            import speech_recognition
            self._has_sr = True
        except ImportError:
            self._has_sr = False
            logger.info("[ASR] 未安装SpeechRecognition（可选）")

        if not self._has_whisper and not self._has_sr:
            logger.warning(
                "[ASR] 没有可用的ASR引擎，语音识别功能不可用。"
                "推荐安装: pip install openai-whisper"
            )

    @staticmethod
    def _check_ffmpeg() -> bool:
        try:
            result = subprocess.run(
                ["ffmpeg", "-version"],
                capture_output=True,
                timeout=5,
                text=True
            )
            return result.returncode == 0
        except (FileNotFoundError, subprocess.TimeoutExpired, Exception):
            return False

    def _get_whisper_model(self):
        if not self._has_whisper:
            raise AsrDependencyError(
                "openai-whisper 未安装",
                detail="请执行: pip install openai-whisper"
            )
        if self._whisper_model is None:
            import whisper
            model_name = getattr(self.settings, 'whisper_model', 'base')
            device = self.settings.device
            logger.info(f"[ASR] 加载Whisper模型: {model_name} (device={device})")
            try:
                self._whisper_model = whisper.load_model(model_name, device=device)
            except Exception as e:
                raise AsrProcessingError(
                    f"加载Whisper模型失败: {e}",
                    stage="model_load",
                    detail=str(e)
                )
        return self._whisper_model

    def is_video_file(self, filename: str) -> bool:
        video_exts = {".mp4", ".avi", ".mov", ".mkv", ".flv", ".wmv", ".webm", ".m4v", ".3gp"}
        ext = os.path.splitext(filename)[1].lower()
        return ext in video_exts

    def is_audio_file(self, filename: str) -> bool:
        audio_exts = {".wav", ".mp3", ".m4a", ".flac", ".aac", ".ogg", ".wma", ".aiff"}
        ext = os.path.splitext(filename)[1].lower()
        return ext in audio_exts

    def _extract_audio_from_video(self, video_path: str) -> str:
        if not self._has_ffmpeg:
            raise AsrDependencyError(
                "ffmpeg 未安装，无法从视频中提取音频",
                detail="请安装 ffmpeg 并确保在系统PATH中"
            )

        temp_audio = os.path.join(
            self.settings.temp_dir,
            f"asr_audio_{os.path.basename(video_path)}_{os.getpid()}.wav"
        )

        try:
            cmd = [
                "ffmpeg",
                "-i", video_path,
                "-vn",
                "-ac", "1",
                "-ar", "16000",
                "-y",
                temp_audio
            ]
            logger.info(f"[ASR] 提取视频音频: {' '.join(cmd)}")
            result = subprocess.run(
                cmd,
                capture_output=True,
                timeout=600,
                text=True
            )
            if result.returncode != 0:
                raise AsrProcessingError(
                    f"视频音频提取失败: ffmpeg返回码 {result.returncode}",
                    stage="video_to_audio",
                    detail=result.stderr[:500] if result.stderr else ""
                )
            if not os.path.exists(temp_audio) or os.path.getsize(temp_audio) < 100:
                raise AsrProcessingError(
                    "视频音频提取失败：输出文件为空",
                    stage="video_to_audio"
                )
            logger.info(f"[ASR] 音频提取完成: {temp_audio}")
            return temp_audio
        except subprocess.TimeoutExpired:
            raise AsrProcessingError(
                "视频音频提取超时",
                stage="video_to_audio",
                detail="视频文件过大或ffmpeg处理时间过长"
            )

    def transcribe_file(
        self,
        file_path: str,
        filename: Optional[str] = None,
        language: str = "zh",
    ) -> Dict[str, Any]:
        """
        对音频/视频文件进行语音转文字。

        Returns:
            {
                "success": bool,
                "full_text": str,
                "segments": [{"start": float, "end": float, "text": str, "confidence": float}],
                "duration": float,
                "language": str,
                "source_type": str,  # "audio" | "video"
                "processing_time_ms": int
            }
        """
        import time
        start_time = time.time()

        if not file_path or not os.path.exists(file_path):
            raise AsrProcessingError(
                f"文件不存在: {file_path}",
                stage="file_validation"
            )

        if filename is None:
            filename = os.path.basename(file_path)

        source_type = "unknown"
        audio_path = file_path
        temp_audio_path = None

        try:
            if self.is_video_file(filename):
                source_type = "video"
                temp_audio_path = self._extract_audio_from_video(file_path)
                audio_path = temp_audio_path
            elif self.is_audio_file(filename):
                source_type = "audio"
            else:
                raise AsrProcessingError(
                    f"不支持的文件类型: {filename}",
                    stage="file_type_check",
                    detail="支持的格式: mp4, avi, mov, mkv, wav, mp3, m4a, flac 等"
                )

            if self._has_whisper:
                return self._transcribe_with_whisper(
                    audio_path, source_type, language, start_time
                )
            else:
                raise AsrDependencyError(
                    "没有可用的ASR引擎",
                    detail="请安装 openai-whisper: pip install openai-whisper"
                )
        finally:
            if temp_audio_path and os.path.exists(temp_audio_path):
                try:
                    os.unlink(temp_audio_path)
                except Exception:
                    pass

    def transcribe_bytes(
        self,
        file_content: bytes,
        filename: str,
        language: str = "zh",
    ) -> Dict[str, Any]:
        """从内存字节进行语音转文字"""
        if not file_content or len(file_content) == 0:
            raise AsrProcessingError("文件内容为空", stage="input_validation")

        ext = os.path.splitext(filename)[1] or ".wav"
        temp_file = os.path.join(
            self.settings.temp_dir,
            f"asr_temp_{os.getpid()}_{id(file_content)}{ext}"
        )
        try:
            with open(temp_file, "wb") as f:
                f.write(file_content)
            return self.transcribe_file(temp_file, filename, language)
        finally:
            if os.path.exists(temp_file):
                try:
                    os.unlink(temp_file)
                except Exception:
                    pass

    def _transcribe_with_whisper(
        self,
        audio_path: str,
        source_type: str,
        language: str,
        start_time: float,
    ) -> Dict[str, Any]:
        import time
        model = self._get_whisper_model()

        try:
            logger.info(f"[ASR] Whisper开始转写: {audio_path} (lang={language})")
            result = model.transcribe(
                audio_path,
                language=language,
                verbose=False,
                fp16=False,
            )

            full_text = result.get("text", "").strip()
            raw_segments = result.get("segments", [])
            segments = []
            for seg in raw_segments:
                segments.append(AsrSegment(
                    start=seg.get("start", 0),
                    end=seg.get("end", 0),
                    text=seg.get("text", "").strip(),
                    confidence=float(seg.get("avg_logprob", -1.0)),
                ).to_dict())

            duration = 0.0
            if segments:
                duration = segments[-1]["end"]

            if not full_text:
                raise AsrProcessingError(
                    "未识别到有效语音内容",
                    stage="transcription",
                    detail="音频中可能没有语音信号，或语音质量较差"
                )

            processing_time_ms = int((time.time() - start_time) * 1000)
            logger.info(
                f"[ASR] 转写完成: 时长≈{duration:.1f}s, 文本长度={len(full_text)}, "
                f"耗时={processing_time_ms}ms"
            )

            return {
                "success": True,
                "full_text": full_text,
                "segments": segments,
                "duration": round(duration, 2),
                "language": result.get("language", language),
                "source_type": source_type,
                "processing_time_ms": processing_time_ms,
            }
        except AsrProcessingError:
            raise
        except Exception as e:
            raise AsrProcessingError(
                f"Whisper语音识别失败: {e}",
                stage="whisper_transcribe",
                detail=str(e)
            )

    def clean_transcript(self, text: str) -> str:
        """清洗语音转写文本，去除口语化噪音"""
        if not text:
            return ""

        lines = text.split("\n")
        cleaned = []
        for line in lines:
            line = line.strip()
            if not line:
                continue

            filler_patterns = [
                r"^[嗯啊呃哦呃呢吧]+[，。,.]*$",
                r"嗯嗯+",
                r"啊啊+",
                r"呃呃+",
                r"[嗯啊呢吧啦哦]$",
            ]
            for pat in filler_patterns:
                line = re.sub(pat, "", line)

            line = re.sub(r"[ \t]+", " ", line)

            if len(line) > 1:
                cleaned.append(line)

        return "\n".join(cleaned).strip()

    def extract_medical_keywords(self, text: str) -> List[Dict[str, Any]]:
        """从语音文本中提取手术相关关键词（辅助实体抽取）"""
        if not text:
            return []

        keywords = []
        patterns = [
            (r"(?:主刀|术者|手术医生)[:：\s]*([\u4e00-\u9fa5]{2,4})", "SURGEON", 0.7),
            (r"(?:麻醉|麻醉方式)[:：\s]*([\u4e00-\u9fa5]{2,10}?麻醉)", "ANESTHESIA_METHOD", 0.75),
            (r"(?:出血|出血量)[：:约\s]*(\d+\s*(?:ml|mL|毫升))", "BLOOD_LOSS", 0.8),
            (r"(?:输血|输血量)[：:约\s]*(\d+\s*(?:ml|mL|毫升)[\u4e00-\u9fa5]*)", "BLOOD_TRANSFUSION", 0.75),
            (r"(?:手术\s*名称|术式)[:：\s]*([\u4e00-\u9fa5A-Za-z0-9\-]{4,20})", "SURGERY_NAME", 0.65),
            (r"行[\s]*([\u4e00-\u9fa5A-Za-z0-9\-]{4,20}(?:术|切除术|吻合术|修补术))", "SURGERY_NAME", 0.7),
            (r"(?:切口|切口等级)[:：\s]*([ⅠⅡⅢ一二三I{I}]\s*类?\s*切?口?)", "INCISION_LEVEL", 0.7),
            (r"(?:器械|手术器械)[:：\s]*([\u4e00-\u9fa5、,，]+)", "SURGICAL_INSTRUMENT", 0.6),
            (r"(?:并发症|术中并发症)[:：\s]*([\u4e00-\u9fa5，,。、\s]{4,60})", "INTRAOP_COMPLICATION", 0.6),
        ]

        seen = set()
        for pattern, etype, conf in patterns:
            for match in re.finditer(pattern, text):
                value = match.group(1).strip()
                if not value or len(value) < 2:
                    continue
                key = (etype, value)
                if key in seen:
                    continue
                seen.add(key)
                keywords.append({
                    "entity_type": etype,
                    "entity_value": value,
                    "confidence": conf,
                    "source": "ASR_KEYWORD",
                    "start_pos": match.start(1),
                    "end_pos": match.end(1),
                    "original_text": value,
                })

        return keywords
