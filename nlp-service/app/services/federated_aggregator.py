import os
import json
import hashlib
import base64
import pickle
import zlib
from pathlib import Path
from typing import Optional, Dict, Any, List, Tuple
from datetime import datetime
from loguru import logger

import torch
import numpy as np

try:
    from cryptography.fernet import Fernet
    from cryptography.hazmat.primitives import hashes
    from cryptography.hazmat.primitives.kdf.pbkdf2 import PBKDF2HMAC
    CRYPTO_AVAILABLE = True
except ImportError:
    CRYPTO_AVAILABLE = False

from app.config import get_settings


class FederatedAggregator:
    """联邦学习参数聚合服务 - 负责多院区模型参数聚合"""

    def __init__(self):
        self.settings = get_settings()
        self.aggregation_dir = Path(self.settings.model_dir) / "federated"
        self.aggregation_dir.mkdir(parents=True, exist_ok=True)

        self.encryption_key = self._init_encryption()

        self.agg_config = {
            "algorithm": os.getenv("FL_AGG_ALGORITHM", "fedavg"),
            "min_clients": int(os.getenv("FL_MIN_CLIENTS", "2")),
            "max_wait_hours": int(os.getenv("FL_MAX_WAIT_HOURS", "72")),
            "weighted_average": os.getenv("FL_WEIGHTED_AVG", "true").lower() == "true",
            "secure_aggregation": os.getenv("FL_SECURE_AGG", "false").lower() == "true",
            "retention_rounds": int(os.getenv("FL_RETENTION_ROUNDS", "10")),
        }

        self.current_round = self._load_current_round()

        logger.info(
            f"[联邦聚合] 服务初始化完成 - 当前轮次: {self.current_round}, "
            f"算法: {self.agg_config['algorithm']}"
        )

    def _init_encryption(self) -> Optional[bytes]:
        """初始化加密密钥"""
        if not CRYPTO_AVAILABLE:
            return None

        try:
            key_file = self.aggregation_dir / ".aggregator_key"
            if key_file.exists():
                with open(key_file, "rb") as f:
                    return f.read()

            password = os.getenv("FL_AGGREGATOR_KEY", "surg-extract-aggregator-2024").encode()
            salt = b"surg-extract-agg-salt-v1"
            kdf = PBKDF2HMAC(
                algorithm=hashes.SHA256(),
                length=32,
                salt=salt,
                iterations=480000,
            )
            key = base64.urlsafe_b64encode(kdf.derive(password))

            with open(key_file, "wb") as f:
                f.write(key)
            key_file.chmod(0o600)

            return key
        except Exception as e:
            logger.warning(f"[联邦聚合] 加密初始化失败: {e}")
            return None

    def _encrypt_data(self, data: bytes) -> bytes:
        """加密数据"""
        if not self.encryption_key or not CRYPTO_AVAILABLE:
            return data
        try:
            f = Fernet(self.encryption_key)
            return f.encrypt(data)
        except Exception:
            return data

    def _decrypt_data(self, encrypted_data: bytes) -> bytes:
        """解密数据"""
        if not self.encryption_key or not CRYPTO_AVAILABLE:
            return encrypted_data
        try:
            f = Fernet(self.encryption_key)
            return f.decrypt(encrypted_data)
        except Exception:
            return encrypted_data

    def _load_current_round(self) -> int:
        """加载当前聚合轮次"""
        round_file = self.aggregation_dir / "current_round.json"
        if round_file.exists():
            try:
                with open(round_file, "r", encoding="utf-8") as f:
                    data = json.load(f)
                return data.get("round", 0)
            except Exception:
                pass
        return 0

    def _save_current_round(self):
        """保存当前聚合轮次"""
        round_file = self.aggregation_dir / "current_round.json"
        with open(round_file, "w", encoding="utf-8") as f:
            json.dump({"round": self.current_round}, f, ensure_ascii=False, indent=2)

    def receive_client_update(
        self,
        client_id: str,
        param_data: Dict[str, Any],
    ) -> Tuple[bool, str]:
        """接收客户端的参数更新"""
        try:
            round_dir = self.aggregation_dir / f"round_{self.current_round}"
            round_dir.mkdir(parents=True, exist_ok=True)

            client_file = round_dir / f"{client_id}_params.pkl"
            checksum = hashlib.sha256(
                pickle.dumps(param_data["parameters"])
            ).hexdigest()

            metadata = {
                "client_id": client_id,
                "client_name": param_data.get("site_name", "unknown"),
                "received_at": datetime.now().isoformat(),
                "train_sample_count": param_data.get("train_sample_count", 0),
                "model_version": param_data.get("model_version", "unknown"),
                "dp_applied": param_data.get("dp_applied", False),
                "checksum": checksum,
                "parameter_count": len(param_data.get("parameters", {})),
            }

            with open(client_file, "wb") as f:
                pickle.dump({"param_data": param_data, "metadata": metadata}, f)

            meta_file = round_dir / f"{client_id}_meta.json"
            with open(meta_file, "w", encoding="utf-8") as f:
                json.dump(metadata, f, ensure_ascii=False, indent=2)

            logger.info(
                f"[联邦聚合] 接收到客户端更新 - 客户端: {client_id}, "
                f"样本数: {metadata['train_sample_count']}, "
                f"参数数量: {metadata['parameter_count']}"
            )

            return True, "更新已接收"

        except Exception as e:
            logger.error(f"[联邦聚合] 接收客户端更新失败: {e}", exc_info=True)
            return False, str(e)

    def receive_client_file(
        self,
        client_id: str,
        param_file_path: str,
    ) -> Tuple[bool, str]:
        """从文件接收客户端的参数更新"""
        try:
            param_path = Path(param_file_path)
            if not param_path.exists():
                return False, f"参数文件不存在: {param_file_path}"

            with open(param_path, "rb") as f:
                serialized = f.read()

            param_data = self._deserialize_params(serialized)
            if not param_data:
                return False, "参数反序列化失败"

            return self.receive_client_update(client_id, param_data)

        except Exception as e:
            logger.error(f"[联邦聚合] 从文件接收更新失败: {e}", exc_info=True)
            return False, str(e)

    def _deserialize_params(self, serialized_data: bytes) -> Optional[Dict[str, Any]]:
        """反序列化参数数据"""
        try:
            decrypted = self._decrypt_data(serialized_data)
            try:
                decompressed = zlib.decompress(decrypted)
            except Exception:
                decompressed = decrypted
            return pickle.loads(decompressed)
        except Exception as e:
            logger.warning(f"[联邦聚合] 参数反序列化失败，尝试直接加载: {e}")
            try:
                return pickle.loads(serialized_data)
            except Exception:
                return None

    def get_pending_clients(self, round_num: Optional[int] = None) -> List[Dict[str, Any]]:
        """获取当前轮次已提交更新的客户端列表"""
        round_num = round_num or self.current_round
        round_dir = self.aggregation_dir / f"round_{round_num}"

        if not round_dir.exists():
            return []

        clients = []
        for meta_file in round_dir.glob("*_meta.json"):
            try:
                with open(meta_file, "r", encoding="utf-8") as f:
                    meta = json.load(f)
                clients.append(meta)
            except Exception:
                continue

        return clients

    def can_aggregate(self, round_num: Optional[int] = None) -> Tuple[bool, str]:
        """检查是否满足聚合条件"""
        clients = self.get_pending_clients(round_num)
        client_count = len(clients)

        if client_count < self.agg_config["min_clients"]:
            return (
                False,
                f"客户端数量不足: 当前 {client_count}，需要至少 {self.agg_config['min_clients']}",
            )

        return True, f"满足聚合条件，共 {client_count} 个客户端"

    def aggregate(
        self,
        round_num: Optional[int] = None,
        algorithm: Optional[str] = None,
    ) -> Tuple[bool, str, Optional[Dict[str, Any]]]:
        """执行参数聚合"""
        round_num = round_num or self.current_round
        algorithm = algorithm or self.agg_config["algorithm"]

        can_agg, msg = self.can_aggregate(round_num)
        if not can_agg:
            return False, msg, None

        logger.info(f"[联邦聚合] 开始第 {round_num} 轮聚合 - 算法: {algorithm}")

        try:
            clients = self.get_pending_clients(round_num)
            round_dir = self.aggregation_dir / f"round_{round_num}"

            all_params = []
            all_weights = []

            for client_meta in clients:
                client_id = client_meta["client_id"]
                client_file = round_dir / f"{client_id}_params.pkl"

                with open(client_file, "rb") as f:
                    data = pickle.load(f)
                    param_data = data["param_data"]

                all_params.append(param_data["parameters"])
                if self.agg_config["weighted_average"]:
                    all_weights.append(client_meta["train_sample_count"])
                else:
                    all_weights.append(1)

            total_weight = sum(all_weights)
            normalized_weights = [w / total_weight for w in all_weights]

            if algorithm == "fedavg":
                aggregated_params = self._fedavg_aggregate(all_params, normalized_weights)
            elif algorithm == "median":
                aggregated_params = self._median_aggregate(all_params)
            elif algorithm == "trimmed_mean":
                aggregated_params = self._trimmed_mean_aggregate(all_params)
            else:
                logger.warning(f"[联邦聚合] 未知算法 {algorithm}，使用FedAvg")
                aggregated_params = self._fedavg_aggregate(all_params, normalized_weights)

            result = {
                "aggregated_at": datetime.now().isoformat(),
                "round": round_num,
                "algorithm": algorithm,
                "client_count": len(clients),
                "client_ids": [c["client_id"] for c in clients],
                "client_weights": normalized_weights,
                "total_samples": sum(c["train_sample_count"] for c in clients),
                "parameters": aggregated_params,
                "model_version": f"aggregated-r{round_num}-{datetime.now().strftime('%Y%m%d%H%M')}",
                "metadata": {
                    "agg_config": self.agg_config,
                    "client_details": clients,
                },
            }

            output_path = self._save_aggregated_result(result)

            logger.info(
                f"[联邦聚合] 第 {round_num} 轮聚合完成 - 客户端: {len(clients)}, "
                f"总样本: {result['total_samples']}, 输出: {output_path}"
            )

            return True, "聚合成功", result

        except Exception as e:
            logger.error(f"[联邦聚合] 聚合失败: {e}", exc_info=True)
            return False, str(e), None

    def _fedavg_aggregate(
        self,
        all_params: List[Dict[str, Any]],
        weights: List[float],
    ) -> Dict[str, Any]:
        """FedAvg算法 - 加权平均"""
        logger.info("[联邦聚合] 使用FedAvg算法进行参数聚合")

        aggregated = {}
        param_keys = all_params[0].keys()

        for key in param_keys:
            weight_sum = 0.0
            shape = all_params[0][key]["shape"]
            dtype = all_params[0][key]["dtype"]

            weighted_sum = np.zeros(shape, dtype=np.float32)

            for client_params, weight in zip(all_params, weights):
                values = np.array(client_params[key]["values"], dtype=np.float32)
                weighted_sum += values * weight
                weight_sum += weight

            aggregated[key] = {
                "shape": shape,
                "dtype": dtype,
                "values": weighted_sum.tolist(),
            }

        return aggregated

    def _median_aggregate(
        self,
        all_params: List[Dict[str, Any]],
    ) -> Dict[str, Any]:
        """中位数聚合 - 对异常值更鲁棒"""
        logger.info("[联邦聚合] 使用中位数算法进行参数聚合")

        aggregated = {}
        param_keys = all_params[0].keys()

        for key in param_keys:
            shape = all_params[0][key]["shape"]
            dtype = all_params[0][key]["dtype"]

            all_values = np.array(
                [np.array(p[key]["values"], dtype=np.float32) for p in all_params]
            )
            median_values = np.median(all_values, axis=0)

            aggregated[key] = {
                "shape": shape,
                "dtype": dtype,
                "values": median_values.tolist(),
            }

        return aggregated

    def _trimmed_mean_aggregate(
        self,
        all_params: List[Dict[str, Any]],
        trim_ratio: float = 0.1,
    ) -> Dict[str, Any]:
        """裁剪均值聚合 - 去除最大值和最小值后取平均"""
        logger.info("[联邦聚合] 使用裁剪均值算法进行参数聚合")

        aggregated = {}
        param_keys = all_params[0].keys()
        n_clients = len(all_params)
        trim_count = int(n_clients * trim_ratio)

        for key in param_keys:
            shape = all_params[0][key]["shape"]
            dtype = all_params[0][key]["dtype"]

            all_values = np.array(
                [np.array(p[key]["values"], dtype=np.float32) for p in all_params]
            )

            all_values.sort(axis=0)
            if trim_count > 0:
                trimmed = all_values[trim_count:-trim_count]
            else:
                trimmed = all_values

            mean_values = np.mean(trimmed, axis=0)

            aggregated[key] = {
                "shape": shape,
                "dtype": dtype,
                "values": mean_values.tolist(),
            }

        return aggregated

    def _save_aggregated_result(self, result: Dict[str, Any]) -> str:
        """保存聚合结果"""
        round_dir = self.aggregation_dir / f"round_{self.current_round}"
        output_file = round_dir / "aggregated_params.pkl"

        serialized = pickle.dumps(result)
        compressed = zlib.compress(serialized, level=9)
        encrypted = self._encrypt_data(compressed)

        with open(output_file, "wb") as f:
            f.write(encrypted)

        checksum = hashlib.sha256(serialized).hexdigest()

        manifest = {
            "created_at": datetime.now().isoformat(),
            "round": self.current_round,
            "file_size": len(encrypted),
            "checksum": checksum,
            "client_count": result["client_count"],
            "total_samples": result["total_samples"],
            "model_version": result["model_version"],
        }

        manifest_file = round_dir / "aggregated_manifest.json"
        with open(manifest_file, "w", encoding="utf-8") as f:
            json.dump(manifest, f, ensure_ascii=False, indent=2)

        global_file = self.aggregation_dir / "latest_aggregated.pkl"
        with open(global_file, "wb") as f:
            f.write(encrypted)

        global_manifest = self.aggregation_dir / "latest_manifest.json"
        with open(global_manifest, "w", encoding="utf-8") as f:
            json.dump(manifest, f, ensure_ascii=False, indent=2)

        return str(output_file)

    def get_aggregated_result(
        self,
        round_num: Optional[int] = None,
    ) -> Optional[Dict[str, Any]]:
        """获取聚合结果"""
        try:
            if round_num is not None:
                result_file = (
                    self.aggregation_dir / f"round_{round_num}" / "aggregated_params.pkl"
                )
            else:
                result_file = self.aggregation_dir / "latest_aggregated.pkl"

            if not result_file.exists():
                return None

            with open(result_file, "rb") as f:
                encrypted = f.read()

            return self._deserialize_params(encrypted)

        except Exception as e:
            logger.error(f"[联邦聚合] 获取聚合结果失败: {e}")
            return None

    def export_aggregated_for_clients(
        self,
        output_dir: str,
        round_num: Optional[int] = None,
    ) -> Tuple[bool, str]:
        """导出聚合结果供客户端下载"""
        try:
            result = self.get_aggregated_result(round_num)
            if not result:
                return False, "没有可用的聚合结果"

            output_path = Path(output_dir)
            output_path.mkdir(parents=True, exist_ok=True)

            serialized = pickle.dumps(result)
            compressed = zlib.compress(serialized, level=9)

            output_file = output_path / f"aggregated_r{result['round']}.pkl"
            with open(output_file, "wb") as f:
                f.write(compressed)

            checksum = hashlib.sha256(serialized).hexdigest()

            manifest = {
                "created_at": datetime.now().isoformat(),
                "round": result["round"],
                "client_count": result["client_count"],
                "total_samples": result["total_samples"],
                "model_version": result["model_version"],
                "checksum": checksum,
                "file_size": len(compressed),
            }

            manifest_file = output_path / f"aggregated_r{result['round']}_manifest.json"
            with open(manifest_file, "w", encoding="utf-8") as f:
                json.dump(manifest, f, ensure_ascii=False, indent=2)

            logger.info(f"[联邦聚合] 聚合结果已导出: {output_file}")
            return True, str(output_file)

        except Exception as e:
            logger.error(f"[联邦聚合] 导出聚合结果失败: {e}")
            return False, str(e)

    def start_new_round(self) -> int:
        """开始新的聚合轮次"""
        self.current_round += 1
        self._save_current_round()

        round_dir = self.aggregation_dir / f"round_{self.current_round}"
        round_dir.mkdir(parents=True, exist_ok=True)

        logger.info(f"[联邦聚合] 开始第 {self.current_round} 轮聚合")

        self._cleanup_old_rounds()

        return self.current_round

    def _cleanup_old_rounds(self):
        """清理旧的聚合轮次数据"""
        retention = self.agg_config["retention_rounds"]
        if self.current_round <= retention:
            return

        oldest_to_keep = self.current_round - retention
        for round_dir in self.aggregation_dir.glob("round_*"):
            try:
                round_num = int(round_dir.name.split("_")[1])
                if round_num < oldest_to_keep:
                    import shutil
                    shutil.rmtree(round_dir)
                    logger.info(f"[联邦聚合] 已清理旧轮次数据: round_{round_num}")
            except Exception:
                continue

    def get_aggregator_status(self) -> Dict[str, Any]:
        """获取聚合服务状态"""
        clients = self.get_pending_clients()
        can_agg, agg_msg = self.can_aggregate()

        return {
            "current_round": self.current_round,
            "pending_clients": len(clients),
            "client_details": clients,
            "can_aggregate": can_agg,
            "aggregate_message": agg_msg,
            "agg_config": self.agg_config,
            "min_clients_required": self.agg_config["min_clients"],
            "encryption_available": self.encryption_key is not None,
        }


_federated_aggregator = None


def get_federated_aggregator() -> FederatedAggregator:
    """获取联邦学习聚合服务单例"""
    global _federated_aggregator
    if _federated_aggregator is None:
        _federated_aggregator = FederatedAggregator()
    return _federated_aggregator
