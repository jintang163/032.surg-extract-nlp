import re
import json
import hashlib
from typing import Optional, Dict, Any, List, Tuple, Union
from datetime import datetime
from pathlib import Path
from loguru import logger
from dataclasses import dataclass, asdict, field


@dataclass
class SensitiveField:
    """敏感字段定义"""
    name: str
    pattern: str
    replacement: str = "***"
    mask_type: str = "full"
    enabled: bool = True


@dataclass
class AuditLogEntry:
    """审计日志条目"""
    timestamp: str
    event_type: str
    user_id: Optional[str] = None
    patient_id: Optional[str] = None
    record_id: Optional[int] = None
    operation: str = ""
    fields_accessed: List[str] = field(default_factory=list)
    fields_modified: List[str] = field(default_factory=list)
    success: bool = True
    ip_address: Optional[str] = None
    user_agent: Optional[str] = None
    details: Dict[str, Any] = field(default_factory=dict)
    checksum: str = ""


class DataMasker:
    """数据脱敏器"""

    def __init__(self):
        self.sensitive_fields = self._init_sensitive_fields()
        logger.info("[隐私保护] 数据脱敏器已初始化")

    def _init_sensitive_fields(self) -> Dict[str, SensitiveField]:
        """初始化敏感字段规则"""
        fields = {
            "patient_name": SensitiveField(
                name="patient_name",
                pattern=r'[\u4e00-\u9fa5]{2,4}(?:·[\u4e00-\u9fa5]{2,4})?',
                replacement="***",
                mask_type="name",
            ),
            "patient_id": SensitiveField(
                name="patient_id",
                pattern=r'(?:ZY|住院号|病历号)[:：]?\s*[A-Za-z0-9\-]{6,20}',
                replacement="***",
                mask_type="full",
            ),
            "id_card": SensitiveField(
                name="id_card",
                pattern=r'\d{17}[\dXx]|\d{15}',
                replacement="************",
                mask_type="partial",
            ),
            "phone": SensitiveField(
                name="phone",
                pattern=r'1[3-9]\d{9}',
                replacement="1*********",
                mask_type="partial",
            ),
            "address": SensitiveField(
                name="address",
                pattern=r'(?:地址|住址|家庭住址)[:：]?\s*[\u4e00-\u9fa50-9\-]{5,50}',
                replacement="***",
                mask_type="full",
            ),
            "email": SensitiveField(
                name="email",
                pattern=r'[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}',
                replacement="***@***.com",
                mask_type="full",
            ),
        }
        return fields

    def mask_text(self, text: str, fields: Optional[List[str]] = None) -> str:
        """对文本进行脱敏处理"""
        if not text:
            return text

        result = text
        fields_to_mask = fields or self.sensitive_fields.keys()

        for field_name in fields_to_mask:
            if field_name not in self.sensitive_fields:
                continue

            field = self.sensitive_fields[field_name]
            if not field.enabled:
                continue

            result = self._apply_mask(result, field)

        return result

    def _apply_mask(self, text: str, field: SensitiveField) -> str:
        """应用脱敏规则"""
        try:
            pattern = re.compile(field.pattern, re.IGNORECASE)

            if field.mask_type == "name":
                return pattern.sub(self._mask_name, text)
            elif field.mask_type == "partial":
                return pattern.sub(self._mask_partial, text)
            else:
                return pattern.sub(field.replacement, text)
        except Exception as e:
            logger.warning(f"[隐私保护] 脱敏失败 {field.name}: {e}")
            return text

    @staticmethod
    def _mask_name(match: re.Match) -> str:
        """姓名脱敏：保留首字，其余用*替代"""
        name = match.group(0)
        if len(name) <= 1:
            return name
        return name[0] + "*" * (len(name) - 1)

    @staticmethod
    def _mask_partial(match: re.Match) -> str:
        """部分脱敏：保留首尾，中间用*替代"""
        value = match.group(0)
        if len(value) <= 4:
            return "*" * len(value)
        keep_start = 2
        keep_end = 2
        mask_len = len(value) - keep_start - keep_end
        return value[:keep_start] + "*" * mask_len + value[-keep_end:]

    def mask_entities(self, entities: List[Dict[str, Any]]) -> List[Dict[str, Any]]:
        """对NLP识别出的实体进行脱敏"""
        sensitive_entity_types = {
            "PATIENT_NAME": "name",
            "PATIENT_ID": "full",
            "HOSPITAL_NO": "full",
            "ID_CARD": "partial",
            "PHONE": "partial",
            "EMAIL": "full",
            "ADDRESS": "full",
        }

        masked_entities = []
        for entity in entities:
            entity_copy = entity.copy()
            entity_type = entity_copy.get("entity_type", "")

            if entity_type in sensitive_entity_types:
                original_value = entity_copy.get("entity_value", "")
                mask_type = sensitive_entity_types[entity_type]

                if mask_type == "name" and len(original_value) > 1:
                    masked = original_value[0] + "*" * (len(original_value) - 1)
                elif mask_type == "partial" and len(original_value) > 4:
                    masked = original_value[:2] + "*" * (len(original_value) - 4) + original_value[-2:]
                else:
                    masked = "*" * len(original_value)

                entity_copy["entity_value"] = masked
                entity_copy["original_value"] = original_value
                entity_copy["masked"] = True
                entity_copy["mask_type"] = mask_type

            masked_entities.append(entity_copy)

        return masked_entities

    def mask_dict(self, data: Dict[str, Any], fields: Optional[List[str]] = None) -> Dict[str, Any]:
        """对字典数据进行脱敏"""
        result = data.copy()

        for key, value in result.items():
            if fields and key not in fields:
                continue

            if isinstance(value, str):
                result[key] = self.mask_text(value)
            elif isinstance(value, dict):
                result[key] = self.mask_dict(value, fields)
            elif isinstance(value, list):
                result[key] = [
                    self.mask_dict(item, fields) if isinstance(item, dict)
                    else self.mask_text(str(item), fields) if isinstance(item, str)
                    else item
                    for item in value
                ]

        return result


class PrivacyAuditLogger:
    """隐私审计日志记录器"""

    def __init__(self, log_dir: str = "./logs/privacy"):
        self.log_dir = Path(log_dir)
        self.log_dir.mkdir(parents=True, exist_ok=True)
        self.current_log_file = self._get_current_log_file()
        logger.info(f"[隐私保护] 审计日志记录器已初始化，日志目录: {self.log_dir}")

    def _get_current_log_file(self) -> Path:
        """获取当前日志文件路径（按天分割）"""
        date_str = datetime.now().strftime("%Y%m%d")
        return self.log_dir / f"privacy_audit_{date_str}.jsonl"

    def _compute_checksum(self, entry: AuditLogEntry) -> str:
        """计算日志条目的校验和，防止篡改"""
        entry_dict = asdict(entry)
        entry_dict.pop("checksum", None)
        data_str = json.dumps(entry_dict, sort_keys=True, ensure_ascii=False)
        return hashlib.sha256(data_str.encode("utf-8")).hexdigest()

    def log_access(
        self,
        user_id: Optional[str] = None,
        patient_id: Optional[str] = None,
        record_id: Optional[int] = None,
        operation: str = "read",
        fields_accessed: Optional[List[str]] = None,
        ip_address: Optional[str] = None,
        user_agent: Optional[str] = None,
        details: Optional[Dict[str, Any]] = None,
        success: bool = True,
    ) -> str:
        """记录数据访问日志"""
        entry = AuditLogEntry(
            timestamp=datetime.now().isoformat(),
            event_type="DATA_ACCESS",
            user_id=user_id,
            patient_id=patient_id,
            record_id=record_id,
            operation=operation,
            fields_accessed=fields_accessed or [],
            ip_address=ip_address,
            user_agent=user_agent,
            details=details or {},
            success=success,
        )
        entry.checksum = self._compute_checksum(entry)

        self._write_entry(entry)
        return entry.checksum

    def log_modification(
        self,
        user_id: Optional[str] = None,
        patient_id: Optional[str] = None,
        record_id: Optional[int] = None,
        operation: str = "update",
        fields_modified: Optional[List[str]] = None,
        ip_address: Optional[str] = None,
        user_agent: Optional[str] = None,
        details: Optional[Dict[str, Any]] = None,
        success: bool = True,
    ) -> str:
        """记录数据修改日志"""
        entry = AuditLogEntry(
            timestamp=datetime.now().isoformat(),
            event_type="DATA_MODIFICATION",
            user_id=user_id,
            patient_id=patient_id,
            record_id=record_id,
            operation=operation,
            fields_modified=fields_modified or [],
            ip_address=ip_address,
            user_agent=user_agent,
            details=details or {},
            success=success,
        )
        entry.checksum = self._compute_checksum(entry)

        self._write_entry(entry)
        return entry.checksum

    def log_export(
        self,
        user_id: Optional[str] = None,
        record_count: int = 0,
        export_format: str = "json",
        ip_address: Optional[str] = None,
        details: Optional[Dict[str, Any]] = None,
        success: bool = True,
    ) -> str:
        """记录数据导出日志"""
        entry = AuditLogEntry(
            timestamp=datetime.now().isoformat(),
            event_type="DATA_EXPORT",
            user_id=user_id,
            operation=f"export_{export_format}",
            details={
                **(details or {}),
                "record_count": record_count,
                "export_format": export_format,
            },
            ip_address=ip_address,
            success=success,
        )
        entry.checksum = self._compute_checksum(entry)

        self._write_entry(entry)
        return entry.checksum

    def log_model_training(
        self,
        user_id: Optional[str] = None,
        training_type: str = "incremental",
        sample_count: int = 0,
        uses_patient_data: bool = True,
        dp_enabled: bool = False,
        details: Optional[Dict[str, Any]] = None,
        success: bool = True,
    ) -> str:
        """记录模型训练日志"""
        entry = AuditLogEntry(
            timestamp=datetime.now().isoformat(),
            event_type="MODEL_TRAINING",
            user_id=user_id,
            operation=f"train_{training_type}",
            details={
                **(details or {}),
                "sample_count": sample_count,
                "uses_patient_data": uses_patient_data,
                "differential_privacy": dp_enabled,
            },
            success=success,
        )
        entry.checksum = self._compute_checksum(entry)

        self._write_entry(entry)
        return entry.checksum

    def log_federated_update(
        self,
        site_id: str,
        site_name: str,
        round_num: int,
        sample_count: int = 0,
        operation: str = "parameter_upload",
        dp_applied: bool = False,
        encrypted: bool = True,
        details: Optional[Dict[str, Any]] = None,
        success: bool = True,
    ) -> str:
        """记录联邦学习参数交换日志"""
        entry = AuditLogEntry(
            timestamp=datetime.now().isoformat(),
            event_type="FEDERATED_LEARNING",
            user_id=site_id,
            operation=operation,
            details={
                **(details or {}),
                "site_id": site_id,
                "site_name": site_name,
                "round": round_num,
                "sample_count": sample_count,
                "dp_applied": dp_applied,
                "encrypted": encrypted,
            },
            success=success,
        )
        entry.checksum = self._compute_checksum(entry)

        self._write_entry(entry)
        return entry.checksum

    def _write_entry(self, entry: AuditLogEntry):
        """写入日志条目"""
        try:
            self.current_log_file = self._get_current_log_file()
            with open(self.current_log_file, "a", encoding="utf-8") as f:
                f.write(json.dumps(asdict(entry), ensure_ascii=False) + "\n")
        except Exception as e:
            logger.error(f"[隐私保护] 写入审计日志失败: {e}")

    def query_logs(
        self,
        start_time: Optional[str] = None,
        end_time: Optional[str] = None,
        event_type: Optional[str] = None,
        user_id: Optional[str] = None,
        patient_id: Optional[str] = None,
        limit: int = 1000,
    ) -> List[Dict[str, Any]]:
        """查询审计日志"""
        results = []

        start_dt = datetime.fromisoformat(start_time) if start_time else None
        end_dt = datetime.fromisoformat(end_time) if end_time else None

        for log_file in sorted(self.log_dir.glob("privacy_audit_*.jsonl")):
            try:
                with open(log_file, "r", encoding="utf-8") as f:
                    for line in f:
                        line = line.strip()
                        if not line:
                            continue

                        entry = json.loads(line)

                        ts = datetime.fromisoformat(entry["timestamp"])
                        if start_dt and ts < start_dt:
                            continue
                        if end_dt and ts > end_dt:
                            continue
                        if event_type and entry["event_type"] != event_type:
                            continue
                        if user_id and entry["user_id"] != user_id:
                            continue
                        if patient_id and entry["patient_id"] != patient_id:
                            continue

                        results.append(entry)

                        if len(results) >= limit:
                            return results
            except Exception as e:
                logger.warning(f"[隐私保护] 读取日志文件失败 {log_file}: {e}")

        return results

    def verify_integrity(self, checksum: str) -> Tuple[bool, Optional[Dict[str, Any]]]:
        """验证日志条目的完整性"""
        for log_file in sorted(self.log_dir.glob("privacy_audit_*.jsonl")):
            try:
                with open(log_file, "r", encoding="utf-8") as f:
                    for line in f:
                        line = line.strip()
                        if not line:
                            continue

                        entry = json.loads(line)
                        if entry.get("checksum") == checksum:
                            stored_checksum = entry.pop("checksum")
                            recomputed = hashlib.sha256(
                                json.dumps(entry, sort_keys=True, ensure_ascii=False).encode("utf-8")
                            ).hexdigest()
                            entry["checksum"] = stored_checksum
                            return stored_checksum == recomputed, entry
            except Exception:
                continue

        return False, None

    def get_statistics(self, days: int = 30) -> Dict[str, Any]:
        """获取审计统计信息"""
        stats = {
            "total_logs": 0,
            "by_event_type": {},
            "by_user": {},
            "by_operation": {},
            "success_rate": 0.0,
            "data_access_count": 0,
            "data_modification_count": 0,
            "data_export_count": 0,
            "model_training_count": 0,
            "federated_learning_count": 0,
        }

        total_success = 0

        for log_file in sorted(self.log_dir.glob("privacy_audit_*.jsonl")):
            try:
                with open(log_file, "r", encoding="utf-8") as f:
                    for line in f:
                        line = line.strip()
                        if not line:
                            continue

                        entry = json.loads(line)
                        stats["total_logs"] += 1

                        event_type = entry["event_type"]
                        stats["by_event_type"][event_type] = stats["by_event_type"].get(event_type, 0) + 1

                        user_id = entry.get("user_id") or "unknown"
                        stats["by_user"][user_id] = stats["by_user"].get(user_id, 0) + 1

                        operation = entry.get("operation") or "unknown"
                        stats["by_operation"][operation] = stats["by_operation"].get(operation, 0) + 1

                        if entry.get("success", True):
                            total_success += 1

                        if event_type == "DATA_ACCESS":
                            stats["data_access_count"] += 1
                        elif event_type == "DATA_MODIFICATION":
                            stats["data_modification_count"] += 1
                        elif event_type == "DATA_EXPORT":
                            stats["data_export_count"] += 1
                        elif event_type == "MODEL_TRAINING":
                            stats["model_training_count"] += 1
                        elif event_type == "FEDERATED_LEARNING":
                            stats["federated_learning_count"] += 1

            except Exception:
                continue

        if stats["total_logs"] > 0:
            stats["success_rate"] = round(total_success / stats["total_logs"], 4)

        return stats


_data_masker = None
_audit_logger = None


def get_data_masker() -> DataMasker:
    """获取数据脱敏器单例"""
    global _data_masker
    if _data_masker is None:
        _data_masker = DataMasker()
    return _data_masker


def get_audit_logger() -> PrivacyAuditLogger:
    """获取隐私审计日志记录器单例"""
    global _audit_logger
    if _audit_logger is None:
        _audit_logger = PrivacyAuditLogger()
    return _audit_logger
