import os
import sys
import json
import time
import hashlib
import base64
import pickle
import zlib
from pathlib import Path
from typing import Optional, Dict, Any, List, Tuple
from datetime import datetime
from loguru import logger

import torch
import torch.nn as nn
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


class FederatedClient:
    """联邦学习客户端 - 负责本地训练和加密参数导出"""

    def __init__(self):
        self.settings = get_settings()
        self.gpu_optimizer = get_gpu_optimizer()
        self.device = self.gpu_optimizer.device
        self.site_id = os.getenv("FEDERATED_SITE_ID", f"site-{hashlib.md5(os.uname()[1].encode()).hexdigest()[:8]}")
        self.site_name = os.getenv("FEDERATED_SITE_NAME", "未知院区")
        self.encryption_key = self._init_encryption()

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

        logger.info(
            f"[联邦学习] 客户端初始化完成 - 院区ID: {self.site_id}, "
            f"院区名称: {self.site_name}"
        )
        logger.info(f"[联邦学习] 配置: {json.dumps(self.fl_config, ensure_ascii=False)}")

    def _init_encryption(self) -> Optional[bytes]:
        """初始化加密密钥"""
        if not CRYPTO_AVAILABLE or not self.fl_config["encrypt_params"]:
            return None

        try:
            key_file = Path(self.settings.model_dir) / ".federated_key"
            if key_file.exists():
                with open(key_file, "rb") as f:
                    return f.read()

            password = os.getenv("FEDERATED_ENCRYPTION_KEY", "surg-extract-2024-federated").encode()
            salt = b"surg-extract-nlp-salt-v1"
            kdf = PBKDF2HMAC(
                algorithm=hashes.SHA256(),
                length=32,
                salt=salt,
                iterations=480000,
            )
            key = base64.urlsafe_b64encode(kdf.derive(password))

            key_file.parent.mkdir(parents=True, exist_ok=True)
            with open(key_file, "wb") as f:
                f.write(key)
            key_file.chmod(0o600)

            logger.info("[联邦学习] 加密密钥已生成")
            return key
        except Exception as e:
            logger.warning(f"[联邦学习] 加密初始化失败: {e}")
            return None

    def _encrypt_data(self, data: bytes) -> bytes:
        """加密数据"""
        if not self.encryption_key or not CRYPTO_AVAILABLE:
            return data

        try:
            f = Fernet(self.encryption_key)
            return f.encrypt(data)
        except Exception as e:
            logger.warning(f"[联邦学习] 数据加密失败，将使用明文: {e}")
            return data

    def _decrypt_data(self, encrypted_data: bytes) -> bytes:
        """解密数据"""
        if not self.encryption_key or not CRYPTO_AVAILABLE:
            return encrypted_data

        try:
            f = Fernet(self.encryption_key)
            return f.decrypt(encrypted_data)
        except Exception as e:
            logger.warning(f"[联邦学习] 数据解密失败: {e}")
            return encrypted_data

    def _compress_data(self, data: bytes) -> bytes:
        """压缩数据"""
        if not self.fl_config["compress_params"]:
            return data
        return zlib.compress(data, level=9)

    def _decompress_data(self, compressed_data: bytes) -> bytes:
        """解压数据"""
        try:
            return zlib.decompress(compressed_data)
        except Exception:
            return compressed_data

    def extract_model_parameters(
        self,
        model: nn.Module,
        train_sample_count: int,
    ) -> Dict[str, Any]:
        """提取模型参数（用于联邦学习上传）"""
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

        if self.fl_config["dp_enabled"]:
            params = self._apply_differential_privacy(params)
            logger.info("[联邦学习] 已应用差分隐私保护")

        param_count = sum(p["values"].numel() if hasattr(p["values"], "numel") else len(p["values"]) for p in params.values())
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

    def _apply_differential_privacy(self, params: Dict[str, Any]) -> Dict[str, Any]:
        """应用差分隐私 - 向参数添加高斯噪声"""
        noise_multiplier = self.fl_config["dp_noise_multiplier"]
        max_norm = self.fl_config["dp_max_grad_norm"]

        for key, param_data in params.items():
            import numpy as np
            values = np.array(param_data["values"])

            sigma = noise_multiplier * max_norm
            noise = np.random.normal(loc=0.0, scale=sigma, size=values.shape)

            param_data["values"] = (values + noise).tolist()
            param_data["dp_noise_std"] = float(sigma)

        return params

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
        encrypted = self._encrypt_data(compressed)

        logger.info(
            f"[联邦学习] 序列化完成 - 原始: {len(pickled)} bytes, "
            f"压缩后: {len(compressed)} bytes, "
            f"加密后: {len(encrypted)} bytes"
        )

        return encrypted

    def deserialize_parameters(self, serialized_data: bytes) -> Optional[Dict[str, Any]]:
        """反序列化参数数据"""
        try:
            decrypted = self._decrypt_data(serialized_data)
            decompressed = self._decompress_data(decrypted)
            return pickle.loads(decompressed)
        except Exception as e:
            logger.error(f"[联邦学习] 参数反序列化失败: {e}")
            return None

    def export_parameters(
        self,
        model: nn.Module,
        train_sample_count: int,
        output_path: str,
    ) -> Tuple[bool, str]:
        """导出参数到文件（用于联邦学习上传）"""
        try:
            param_data = self.extract_model_parameters(model, train_sample_count)
            serialized = self.serialize_parameters(param_data)

            output_file = Path(output_path)
            output_file.parent.mkdir(parents=True, exist_ok=True)

            with open(output_file, "wb") as f:
                f.write(serialized)

            checksum = hashlib.sha256(serialized).hexdigest()

            manifest = {
                "exported_at": datetime.now().isoformat(),
                "site_id": self.site_id,
                "site_name": self.site_name,
                "file_size": len(serialized),
                "checksum": checksum,
                "checksum_algorithm": "SHA256",
                "encrypted": self.encryption_key is not None,
                "compressed": self.fl_config["compress_params"],
                "train_sample_count": train_sample_count,
                "model_version": param_data["model_version"],
            }

            manifest_path = output_file.with_suffix(".manifest.json")
            with open(manifest_path, "w", encoding="utf-8") as f:
                json.dump(manifest, f, ensure_ascii=False, indent=2)

            logger.info(
                f"[联邦学习] 参数导出成功 - 文件: {output_file}, "
                f"大小: {len(serialized)} bytes, 校验和: {checksum[:16]}..."
            )
            return True, str(output_file)

        except Exception as e:
            logger.error(f"[联邦学习] 参数导出失败: {e}", exc_info=True)
            return False, str(e)

    def import_parameters(
        self,
        param_file: str,
        model: nn.Module,
    ) -> Tuple[bool, str]:
        """导入聚合后的参数到模型"""
        try:
            param_path = Path(param_file)
            if not param_path.exists():
                return False, f"参数文件不存在: {param_file}"

            with open(param_file, "rb") as f:
                serialized = f.read()

            param_data = self.deserialize_parameters(serialized)
            if not param_data:
                return False, "参数反序列化失败"

            if param_data.get("site_id") == self.site_id:
                logger.warning("[联邦学习] 导入的是本地上传的参数，跳过更新")
                return True, "跳过自身参数更新"

            if "parameters" not in param_data:
                return False, "参数数据格式错误，缺少parameters字段"

            state_dict = model.state_dict()
            updated_count = 0

            for key, param_info in param_data["parameters"].items():
                if key in state_dict:
                    import numpy as np
                    values = np.array(param_info["values"]).astype(
                        state_dict[key].numpy().dtype
                    )
                    tensor = torch.from_numpy(values).to(state_dict[key].device)
                    state_dict[key] = tensor
                    updated_count += 1

            model.load_state_dict(state_dict)

            logger.info(
                f"[联邦学习] 参数导入成功 - 来自: {param_data.get('site_name', 'unknown')}, "
                f"更新了 {updated_count}/{len(param_data['parameters'])} 个参数张量"
            )
            return True, f"成功导入来自 {param_data.get('site_name')} 的参数"

        except Exception as e:
            logger.error(f"[联邦学习] 参数导入失败: {e}", exc_info=True)
            return False, str(e)

    def local_train(
        self,
        model: nn.Module,
        train_loader: DataLoader,
        optimizer: torch.optim.Optimizer,
        criterion: nn.Module,
        epochs: Optional[int] = None,
    ) -> Dict[str, Any]:
        """执行本地训练（联邦学习的本地训练步骤）"""
        epochs = epochs or self.fl_config["max_local_epochs"]
        train_start = time.time()

        logger.info(
            f"[联邦学习] 开始本地训练 - 轮次: {epochs}, "
            f"批次大小: {self.fl_config['batch_size']}, "
            f"样本数: {len(train_loader.dataset)}"
        )

        model = model.to(self.device)
        model.train()

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

                loss = model(**inputs)
                loss.backward()

                if self.fl_config["dp_enabled"]:
                    torch.nn.utils.clip_grad_norm_(
                        model.parameters(), self.fl_config["dp_max_grad_norm"]
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

        result = {
            "success": True,
            "epochs_trained": epochs,
            "train_duration_ms": train_duration,
            "sample_count": len(train_loader.dataset),
            "batch_count": batch_count * epochs,
            "final_loss": loss_history[-1] if loss_history else 0,
            "loss_history": loss_history,
            "site_id": self.site_id,
            "site_name": self.site_name,
        }

        logger.info(
            f"[联邦学习] 本地训练完成 - 样本: {result['sample_count']}, "
            f"耗时: {train_duration}ms, 最终损失: {result['final_loss']:.6f}"
        )

        return result

    def get_client_info(self) -> Dict[str, Any]:
        """获取客户端信息"""
        return {
            "site_id": self.site_id,
            "site_name": self.site_name,
            "device_type": self.device.type,
            "encryption_available": self.encryption_key is not None,
            "dp_enabled": self.fl_config["dp_enabled"],
            "compress_enabled": self.fl_config["compress_params"],
            "encrypt_enabled": self.fl_config["encrypt_params"],
            "fl_config": self.fl_config,
        }


_federated_client = None


def get_federated_client() -> FederatedClient:
    """获取联邦学习客户端单例"""
    global _federated_client
    if _federated_client is None:
        _federated_client = FederatedClient()
    return _federated_client
