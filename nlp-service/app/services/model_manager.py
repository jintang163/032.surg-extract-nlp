import os
import json
import hashlib
import shutil
from pathlib import Path
from loguru import logger
from typing import Optional, Dict, Any
from datetime import datetime
from app.config import get_settings


class ModelManager:
    """模型管理器，负责本地模型加载、版本管理、参数校验"""

    def __init__(self):
        self.settings = get_settings()
        self.model_dir = Path(self.settings.model_dir)
        self.model_dir.mkdir(parents=True, exist_ok=True)
        self.active_model: Optional[Dict[str, Any]] = None
        self._scan_models()

    def _scan_models(self) -> None:
        """扫描本地模型目录，识别可用模型"""
        logger.info(f"[模型管理] 扫描模型目录: {self.model_dir}")
        models = []

        for model_path in self.model_dir.iterdir():
            if model_path.is_dir():
                model_info = self._read_model_info(model_path)
                if model_info:
                    models.append(model_info)
                    logger.info(f"  发现模型: {model_path.name} v{model_info.get('version', 'unknown')}")

        self.available_models = models
        logger.info(f"[模型管理] 共发现 {len(models)} 个可用模型")

    def _read_model_info(self, model_path: Path) -> Optional[Dict[str, Any]]:
        """读取模型元信息"""
        info_path = model_path / "model_info.json"
        config_path = model_path / "config.json"
        weight_path = model_path / "pytorch_model.bin"

        if not weight_path.exists():
            return None

        info = {
            "name": model_path.name,
            "path": str(model_path),
            "weight_path": str(weight_path),
            "last_modified": datetime.fromtimestamp(
                weight_path.stat().st_mtime
            ).isoformat(),
            "size_bytes": weight_path.stat().st_size,
            "size_mb": round(weight_path.stat().st_size / 1024 / 1024, 2),
        }

        if info_path.exists():
            try:
                with open(info_path, "r", encoding="utf-8") as f:
                    extra_info = json.load(f)
                info.update(extra_info)
            except Exception as e:
                logger.warning(f"[模型管理] 读取模型信息失败: {e}")

        if config_path.exists():
            try:
                with open(config_path, "r", encoding="utf-8") as f:
                    config = json.load(f)
                info["architecture"] = config.get("architecture", {})
            except Exception:
                pass

        return info

    def verify_model_integrity(self, model_path: Path) -> tuple[bool, str]:
        """验证模型文件完整性"""
        weight_path = model_path / "pytorch_model.bin"
        label_path = model_path / "label2id.json"

        required_files = [weight_path, label_path]
        for f in required_files:
            if not f.exists():
                return False, f"缺少必要文件: {f.name}"

        try:
            file_hash = self._calculate_file_hash(weight_path)
            info_path = model_path / "model_info.json"
            if info_path.exists():
                with open(info_path, "r", encoding="utf-8") as f:
                    info = json.load(f)
                expected_hash = info.get("file_hash")
                if expected_hash and expected_hash != file_hash:
                    return False, "模型文件哈希校验失败，文件可能已损坏"
        except Exception as e:
            return False, f"校验过程出错: {e}"

        return True, "模型文件完整"

    def _calculate_file_hash(self, file_path: Path, chunk_size: int = 8192) -> str:
        """计算文件SHA256哈希"""
        sha256 = hashlib.sha256()
        with open(file_path, "rb") as f:
            while chunk := f.read(chunk_size):
                sha256.update(chunk)
        return sha256.hexdigest()

    def load_model(self, model_name: str = "surgery-ner") -> Optional[Dict[str, Any]]:
        """加载指定模型"""
        model_path = self.model_dir / model_name

        if not model_path.exists():
            logger.error(f"[模型管理] 模型不存在: {model_name}")
            return None

        is_valid, msg = self.verify_model_integrity(model_path)
        if not is_valid:
            logger.error(f"[模型管理] 模型校验失败: {msg}")
            return None

        model_info = self._read_model_info(model_path)
        self.active_model = model_info
        logger.info(
            f"[模型管理] 模型加载成功: {model_name} "
            f"v{model_info.get('version', 'unknown')} "
            f"({model_info.get('size_mb', 0):.1f}MB)"
        )

        return model_info

    def switch_model(self, model_name: str) -> tuple[bool, str]:
        """切换当前使用的模型"""
        model_info = self.load_model(model_name)
        if model_info:
            return True, f"已切换到模型: {model_name}"
        return False, "模型切换失败"

    def import_model(
        self, source_path: str, model_name: str, version: str = None
    ) -> tuple[bool, str]:
        """导入外部模型到本地模型目录"""
        source = Path(source_path)
        if not source.exists():
            return False, f"源路径不存在: {source_path}"

        target_path = self.model_dir / model_name
        if target_path.exists():
            backup_path = self.model_dir / f"{model_name}.backup.{datetime.now().strftime('%Y%m%d%H%M%S')}"
            logger.warning(f"[模型管理] 模型已存在，备份到: {backup_path}")
            shutil.move(target_path, backup_path)

        try:
            shutil.copytree(source, target_path)

            weight_path = target_path / "pytorch_model.bin"
            file_hash = self._calculate_file_hash(weight_path)

            info = {
                "name": model_name,
                "version": version or f"imported-{datetime.now().strftime('%Y%m%d')}",
                "imported_at": datetime.now().isoformat(),
                "file_hash": file_hash,
            }

            with open(target_path / "model_info.json", "w", encoding="utf-8") as f:
                json.dump(info, f, ensure_ascii=False, indent=2)

            self._scan_models()
            logger.info(f"[模型管理] 模型导入成功: {model_name}")
            return True, f"模型导入成功: {model_name}"

        except Exception as e:
            logger.error(f"[模型管理] 模型导入失败: {e}")
            if target_path.exists():
                shutil.rmtree(target_path)
            return False, f"模型导入失败: {e}"

    def export_model(
        self, model_name: str, export_path: str, include_weights: bool = True
    ) -> tuple[bool, str]:
        """导出模型（用于联邦学习参数交换）"""
        model_path = self.model_dir / model_name
        if not model_path.exists():
            return False, f"模型不存在: {model_name}"

        target = Path(export_path)
        try:
            target.mkdir(parents=True, exist_ok=True)

            files_to_copy = ["config.json", "label2id.json", "model_info.json"]
            if include_weights:
                files_to_copy.append("pytorch_model.bin")

            for f in files_to_copy:
                src = model_path / f
                if src.exists():
                    shutil.copy2(src, target / f)

            manifest = {
                "exported_at": datetime.now().isoformat(),
                "model_name": model_name,
                "files": files_to_copy,
            }
            with open(target / "export_manifest.json", "w", encoding="utf-8") as f:
                json.dump(manifest, f, ensure_ascii=False, indent=2)

            logger.info(f"[模型管理] 模型导出成功: {target}")
            return True, f"模型导出成功: {target}"

        except Exception as e:
            logger.error(f"[模型管理] 模型导出失败: {e}")
            return False, f"模型导出失败: {e}"

    def list_models(self) -> list:
        """列出所有可用模型"""
        return self.available_models

    def get_model_status(self) -> Dict[str, Any]:
        """获取当前模型状态"""
        return {
            "active_model": self.active_model,
            "available_models": self.available_models,
            "model_dir": str(self.model_dir),
        }


_model_manager = None


def get_model_manager() -> ModelManager:
    """获取模型管理器单例"""
    global _model_manager
    if _model_manager is None:
        _model_manager = ModelManager()
    return _model_manager
