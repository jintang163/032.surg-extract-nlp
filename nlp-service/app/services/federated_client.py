import os
import sys
import json
import time
import hashlib
import base64
import pickle
import zlib
import platform
from pathlib import Path
from typing import Optional, Dict, Any, List, Tuple
from datetime import datetime
from loguru import logger

import torch
import torch.nn as nn
import numpy as np
from torch.utils.data import DataLoader

try:
    from cryptography.fernet import Fernet
    from cryptography.hazmat.primitives import hashes
    from cryptography.hazmat.primitives.kdf.pbkdf2 import PBKDF2HMAC
    CRYPTO_AVAILABLE = True
except ImportError:
    CRYPTO_AVAILABLE = False
    logger.warning("[联邦学习] cryptography库未安装，加密功能将不可用")

from app.config import get_settings
from app.services.gpu_optimizer import get_gpu_optimizer
from app.services.differential_privacy import (
    DifferentialPrivacyEngine,
    DPConfig,
    PrivacyBudget,
)


def _get_hostname() -> str:
    try:
        return platform.node()
    except Exception:
        return "unknown"


class FederatedClient:
    """联邦学习客户端 - 负责本地训练和加密参数导出"""

    def __init__(self):
        self.settings = get_settings()
        self.gpu_optimizer = get_gpu_optimizer()
        self.device = self.gpu_optimizer.device
        self.site_id = os.getenv("FEDERATED_SITE_ID", f"site-{hashlib.md5(_get_hostname().encode()).hexdigest()[:8]}")
        self.site_name = os.getenv("FEDERATED_SITE_NAME", "未知院区")

        self.fl_config = {
            "max_local_epochs": int(os.getenv("FL_MAX_LOCAL_EPOCHS", "5")),
            "batch_size": int(os.getenv("FL_BATCH_SIZE", "16")),
            "learning_rate": float(os.getenv("FL_LEARNING_RATE", "0.001")),
            "dp_enabled": os.getenv("FL_DP_ENABLED", "true").lower() == "true",
            "dp_noise_multiplier": float(os.getenv("FL_DP_NOISE", "1.0")),
            "dp_max_grad_norm": float(os.getenv("FL_DP_MAX_GRAD_NORM", "1.0")),
            "compress_params": os.getenv("FL_COMPRESS", "true").lower() == "true",
            "encrypt_params": os.getenv("FL_ENCRYPT", "true").lower() == "true",
        }

        self._default_encryption_key = self._derive_key(
            os.getenv("FEDERATED_ENCRYPTION_KEY", "surg-extract-2024-federated")
        )

        self._dp_engine: Optional[DifferentialPrivacyEngine] = None

        logger.info(
            f"[联邦学习] 客户端初始化完成 - 院区ID: {self.site_id}, "
            f"院区名称: {self.site_name}"
        )
        logger.info(f"[联邦学习] 配置: {json.dumps(self.fl_config, ensure_ascii=False)}")

    @property
    def dp_enabled(self) -> bool:
        return self.fl_config["dp_enabled"]

    @property
    def dp_noise_multiplier(self) -> float:
        return self.fl_config["dp_noise_multiplier"]

    def _derive_key(self, password: str) -> Optional[bytes]:
        if not CRYPTO_AVAILABLE:
            return None
        try:
            salt = b"surg-extract-nlp-salt-v1"
            kdf = PBKDF2HMAC(
                algorithm=hashes.SHA256(),
                length=32,
                salt=salt,
                iterations=480000,
            )
            return base64.urlsafe_b64encode(kdf.derive(password.encode()))
        except Exception as e:
            logger.warning(f"[联邦学习] 密钥派生失败: {e}")
            return None

    def _resolve_encryption_key(self, key_hint: Optional[str] = None) -> Optional[bytes]:
        if not self.fl_config["encrypt_params"]:
            return None
        if key_hint:
            derived = self._derive_key(key_hint)
            if derived:
                return derived
        return self._default_encryption_key

    def _encrypt_data(self, data: bytes, key: Optional[bytes] = None) -> bytes:
        if not key or not CRYPTO_AVAILABLE:
            return data
        try:
            f = Fernet(key)
            return f.encrypt(data)
        except Exception as e:
            logger.warning(f"[联邦学习] 数据加密失败，将使用明文: {e}")
            return data

    def _decrypt_data(self, encrypted_data: bytes, key: Optional[bytes] = None) -> bytes:
        if not key or not CRYPTO_AVAILABLE:
            return encrypted_data
        try:
            f = Fernet(key)
            return f.decrypt(encrypted_data)
        except Exception as e:
            logger.warning(f"[联邦学习] 数据解密失败: {e}")
            return encrypted_data

    def _compress_data(self, data: bytes) -> bytes:
        if not self.fl_config["compress_params"]:
            return data
        return zlib.compress(data, level=9)

    def _decompress_data(self, compressed_data: bytes) -> bytes:
        try:
            return zlib.decompress(compressed_data)
        except Exception:
            return compressed_data

    def load_model(self, model_path: str) -> Optional[nn.Module]:
        """从磁盘加载模型用于联邦学习"""
        try:
            model_dir = Path(model_path)
            if not model_dir.exists():
                logger.error(f"[联邦学习] 模型目录不存在: {model_path}")
                return None

            config_path = model_dir / "config.json"
            label_path = model_dir / "label2id.json"

            if not label_path.exists():
                logger.error(f"[联邦学习] 标签文件不存在: {label_path}")
                return None

            with open(label_path, "r", encoding="utf-8") as f:
                label2id = json.load(f)
            num_labels = len(label2id)

            arch = {}
            if config_path.exists():
                with open(config_path, "r", encoding="utf-8") as f:
                    cfg = json.load(f)
                arch = cfg.get("architecture", {})

            scripts_path = os.path.join(
                os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
                "scripts"
            )
            if scripts_path not in sys.path:
                sys.path.insert(0, scripts_path)
            from infer_bilstm_crf import BertBiLSTMCRF

            bert_path = self.settings.bert_local_path if (
                self.settings.offline_mode and os.path.exists(self.settings.bert_local_path)
            ) else self.settings.bert_model_name

            model = BertBiLSTMCRF(
                bert_model_name=bert_path,
                num_labels=num_labels,
                hidden_dim=arch.get("bilstm_hidden_dim", 256),
                lstm_layers=arch.get("bilstm_layers", 2),
                lstm_dropout=arch.get("bilstm_dropout", 0.3),
                offline_mode=self.settings.offline_mode,
            )

            model_file = model_dir / "pytorch_model.bin"
            if model_file.exists():
                state_dict = torch.load(model_file, map_location=self.device)
                model.load_state_dict(state_dict, strict=False)
                logger.info(f"[联邦学习] 模型权重已加载: {model_file}")
            else:
                logger.warning(f"[联邦学习] 模型权重不存在: {model_file}，将使用随机初始化")

            model = model.to(self.device)
            return model

        except Exception as e:
            logger.error(f"[联邦学习] 加载模型失败: {e}", exc_info=True)
            return None

    def save_model(self, model: nn.Module, output_path: str) -> bool:
        """保存模型到磁盘"""
        try:
            out_dir = Path(output_path)
            out_dir.mkdir(parents=True, exist_ok=True)

            torch.save(model.state_dict(), out_dir / "pytorch_model.bin")
            logger.info(f"[联邦学习] 模型已保存: {out_dir}")
            return True
        except Exception as e:
            logger.error(f"[联邦学习] 保存模型失败: {e}", exc_info=True)
            return False

    def local_train(
        self,
        model: nn.Module,
        train_loader: DataLoader,
        epochs: Optional[int] = None,
        lr: Optional[float] = None,
        dp_config: Optional[DPConfig] = None,
    ) -> Dict[str, Any]:
        """执行本地训练（联邦学习的本地训练步骤，DP在训练循环内生效）"""
        epochs = epochs or self.fl_config["max_local_epochs"]
        lr = lr or self.fl_config["learning_rate"]
        train_start = time.time()

        logger.info(
            f"[联邦学习] 开始本地训练 - 轮次: {epochs}, "
            f"批次大小: {self.fl_config['batch_size']}, "
            f"样本数: {len(train_loader.dataset)}, "
            f"DP: {self.fl_config['dp_enabled']}"
        )

        model = model.to(self.device)
        model.train()

        optimizer = torch.optim.Adam(filter(lambda p: p.requires_grad, model.parameters()), lr=lr)

        dp_engine = None
        if self.fl_config["dp_enabled"]:
            effective_config = dp_config or DPConfig(
                enabled=True,
                noise_multiplier=self.fl_config["dp_noise_multiplier"],
                max_grad_norm=self.fl_config["dp_max_grad_norm"],
            )
            dp_engine = DifferentialPrivacyEngine(
                model=model,
                optimizer=optimizer,
                config=effective_config,
            )
            self._dp_engine = dp_engine
            logger.info(
                f"[差分隐私] 已接入训练循环 - 噪声乘数: {effective_config.noise_multiplier}, "
                f"最大梯度范数: {effective_config.max_grad_norm}"
            )

        dataset_size = len(train_loader.dataset)
        batch_size = train_loader.batch_size or self.fl_config["batch_size"]
        global_step = 0
        loss_history = []

        for epoch in range(epochs):
            epoch_loss = 0.0
            batch_count = 0

            for batch in train_loader:
                inputs = {
                    k: v.to(self.device) if isinstance(v, torch.Tensor) else v
                    for k, v in batch.items()
                }

                optimizer.zero_grad()

                if hasattr(model, "forward_train"):
                    loss = model.forward_train(**inputs)
                else:
                    loss = model(**inputs)
                    if isinstance(loss, tuple):
                        loss = loss[0]

                if not isinstance(loss, torch.Tensor):
                    loss = torch.tensor(loss, requires_grad=True)

                loss.backward()

                if dp_engine is not None:
                    grad_norm = dp_engine.clipper.clip_grads(model)
                    dp_engine.noise_injector.inject_noise(model, batch_size)
                    global_step += 1
                    eps, _ = dp_engine.compute_privacy_spent(global_step, batch_size, dataset_size)
                    if epoch == 0 and batch_count == 0:
                        logger.info(
                            f"[差分隐私] 训练中 - 梯度范数: {grad_norm:.4f}, "
                            f"累计ε: {eps:.4f}"
                        )

                optimizer.step()

                epoch_loss += loss.item()
                batch_count += 1

            avg_loss = epoch_loss / max(batch_count, 1)
            loss_history.append(avg_loss)
            logger.info(
                f"[联邦学习] 本地训练 Epoch {epoch+1}/{epochs} - 平均损失: {avg_loss:.6f}"
            )

        train_duration = int((time.time() - train_start) * 1000)

        dp_report = {}
        if dp_engine is not None:
            dp_report = dp_engine.get_privacy_report()

        result = {
            "success": True,
            "epochs_trained": epochs,
            "train_duration_ms": train_duration,
            "sample_count": dataset_size,
            "batch_count": global_step,
            "final_loss": loss_history[-1] if loss_history else 0,
            "loss_history": loss_history,
            "site_id": self.site_id,
            "site_name": self.site_name,
            "dp_report": dp_report,
        }

        logger.info(
            f"[联邦学习] 本地训练完成 - 样本: {result['sample_count']}, "
            f"耗时: {train_duration}ms, 最终损失: {result['final_loss']:.6f}"
        )

        return result

    def extract_model_parameters(
        self,
        model: nn.Module,
        train_sample_count: int,
    ) -> Dict[str, Any]:
        """提取模型参数（用于联邦学习上传）- 不再在导出后加噪"""
        logger.info("[联邦学习] 开始提取模型参数...")

        state_dict = model.state_dict()
        params = {}

        for key, value in state_dict.items():
            if "bert" in key.lower() and key not in ["classifier.weight", "classifier.bias"]:
                continue
            params[key] = {
                "shape": list(value.shape),
                "dtype": str(value.dtype),
                "values": value.cpu().numpy().tolist(),
            }

        param_count = sum(
            np.prod(p["shape"]) if isinstance(p["shape"], list) else 0
            for p in params.values()
        )
        logger.info(f"[联邦学习] 参数提取完成，共 {len(params)} 个张量，{param_count} 个参数")

        return {
            "site_id": self.site_id,
            "site_name": self.site_name,
            "timestamp": datetime.now().isoformat(),
            "train_sample_count": train_sample_count,
            "model_version": self._get_model_version(),
            "parameters": params,
            "dp_applied": self.fl_config["dp_enabled"],
            "metadata": {
                "fl_config": self.fl_config,
                "device_type": self.device.type,
                "train_duration_ms": 0,
            },
        }

    def _get_model_version(self) -> str:
        """获取当前模型版本"""
        try:
            model_info_path = Path(self.settings.model_dir) / "surgery-ner" / "model_info.json"
            if model_info_path.exists():
                with open(model_info_path, "r", encoding="utf-8") as f:
                    info = json.load(f)
                return info.get("version", "unknown")
        except Exception:
            pass
        return f"local-{datetime.now().strftime('%Y%m%d')}"

    def serialize_parameters(self, param_data: Dict[str, Any]) -> bytes:
        """序列化参数数据"""
        logger.info("[联邦学习] 开始序列化参数...")

        pickled = pickle.dumps(param_data)
        compressed = self._compress_data(pickled)

        logger.info(
            f"[联邦学习] 序列化完成 - 原始: {len(pickled)} bytes, "
            f"压缩后: {len(compressed)} bytes"
        )

        return compressed

    def deserialize_parameters(
        self,
        serialized_data: bytes,
        encryption_key: Optional[bytes] = None,
    ) -> Optional[Dict[str, Any]]:
        """反序列化参数数据"""
        try:
            decrypted = self._decrypt_data(serialized_data, encryption_key)
            decompressed = self._decompress_data(decrypted)
            return pickle.loads(decompressed)
        except Exception as e:
            logger.warning(f"[联邦学习] 反序列化失败，尝试直接解压: {e}")
            try:
                decompressed = self._decompress_data(serialized_data)
                return pickle.loads(decompressed)
            except Exception as e2:
                logger.error(f"[联邦学习] 参数反序列化彻底失败: {e2}")
                return None

    def export_parameters(
        self,
        model_path: str,
        train_sample_count: int = 0,
        output_path: str = "",
        encryption_key_hint: Optional[str] = None,
    ) -> Dict[str, Any]:
        """导出参数到文件（用于联邦学习上传）"""
        try:
            model = self.load_model(model_path)
            if model is None:
                return {"success": False, "error_message": f"无法加载模型: {model_path}"}

            param_data = self.extract_model_parameters(model, train_sample_count)

            encryption_key = self._resolve_encryption_key(encryption_key_hint)
            serialized = self.serialize_parameters(param_data)
            encrypted = self._encrypt_data(serialized, encryption_key)

            if not output_path:
                output_path = str(
                    Path(self.settings.model_dir) / "federated" /
                    f"client_{self.site_id}_params.pkl"
                )

            output_file = Path(output_path)
            output_file.parent.mkdir(parents=True, exist_ok=True)

            with open(output_file, "wb") as f:
                f.write(encrypted)

            checksum = hashlib.sha256(encrypted).hexdigest()

            logger.info(
                f"[联邦学习] 参数导出成功 - 文件: {output_file}, "
                f"大小: {len(encrypted)} bytes, 校验和: {checksum[:16]}..."
            )

            return {
                "success": True,
                "output_file": str(output_file),
                "file_size": len(encrypted),
                "checksum": checksum,
                "site_id": self.site_id,
                "site_name": self.site_name,
                "train_sample_count": train_sample_count,
                "dp_applied": self.fl_config["dp_enabled"],
                "encrypted": encryption_key is not None,
            }

        except Exception as e:
            logger.error(f"[联邦学习] 参数导出失败: {e}", exc_info=True)
            return {"success": False, "error_message": str(e)}

    def import_parameters(
        self,
        param_file: str,
        model_path: str,
        output_model_path: str = "",
        encryption_key_hint: Optional[str] = None,
    ) -> Dict[str, Any]:
        """导入聚合后的参数到模型"""
        try:
            param_path = Path(param_file)
            if not param_path.exists():
                return {"success": False, "error_message": f"参数文件不存在: {param_file}"}

            with open(param_file, "rb") as f:
                serialized = f.read()

            encryption_key = self._resolve_encryption_key(encryption_key_hint)
            param_data = self.deserialize_parameters(serialized, encryption_key)
            if not param_data:
                return {"success": False, "error_message": "参数反序列化失败"}

            if "parameters" not in param_data:
                return {"success": False, "error_message": "参数数据格式错误，缺少parameters字段"}

            model = self.load_model(model_path)
            if model is None:
                return {"success": False, "error_message": f"无法加载模型: {model_path}"}

            state_dict = model.state_dict()
            updated_count = 0

            for key, param_info in param_data["parameters"].items():
                if key in state_dict:
                    values = np.array(param_info["values"]).astype(
                        state_dict[key].numpy().dtype
                    )
                    tensor = torch.from_numpy(values).to(state_dict[key].device)
                    state_dict[key] = tensor
                    updated_count += 1

            model.load_state_dict(state_dict)

            save_path = output_model_path or model_path
            self.save_model(model, save_path)

            logger.info(
                f"[联邦学习] 参数导入成功 - 更新了 {updated_count}/{len(param_data['parameters'])} 个参数张量"
            )

            return {
                "success": True,
                "updated_params": updated_count,
                "total_params": len(param_data["parameters"]),
                "source_site": param_data.get("site_name", "aggregated"),
                "model_saved_to": save_path,
            }

        except Exception as e:
            logger.error(f"[联邦学习] 参数导入失败: {e}", exc_info=True)
            return {"success": False, "error_message": str(e)}

    def get_client_info(self) -> Dict[str, Any]:
        """获取客户端信息"""
        return {
            "site_id": self.site_id,
            "site_name": self.site_name,
            "device_type": self.device.type,
            "encryption_available": self._default_encryption_key is not None,
            "dp_enabled": self.fl_config["dp_enabled"],
            "dp_noise_multiplier": self.fl_config["dp_noise_multiplier"],
            "compress_enabled": self.fl_config["compress_params"],
            "encrypt_enabled": self.fl_config["encrypt_params"],
            "offline_mode": self.settings.offline_mode,
            "bert_local_path": self.settings.bert_local_path,
        }


_federated_client = None


def get_federated_client() -> FederatedClient:
    """获取联邦学习客户端单例"""
    global _federated_client
    if _federated_client is None:
        _federated_client = FederatedClient()
    return _federated_client
