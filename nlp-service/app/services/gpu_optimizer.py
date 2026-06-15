import os
import torch
import time
from loguru import logger
from typing import Optional, Callable, Any
from functools import wraps
from app.config import get_settings


class GpuOptimizer:
    """GPU优化管理器，负责GPU资源管理、模型预热、推理优化等"""

    def __init__(self):
        self.settings = get_settings()
        self.device = self._init_device()
        self.model_prepared = False
        self.gpu_memory_fraction = 0.9
        self._init_memory_management()

    def _init_device(self) -> torch.device:
        if self.settings.use_gpu and torch.cuda.is_available():
            gpu_count = torch.cuda.device_count()
            logger.info(f"[GPU优化] 检测到 {gpu_count} 个CUDA设备")
            for i in range(gpu_count):
                props = torch.cuda.get_device_properties(i)
                logger.info(
                    f"  GPU {i}: {props.name}, "
                    f"显存: {props.total_memory / 1024**3:.1f}GB, "
                    f"计算能力: {props.major}.{props.minor}"
                )
            device = torch.device("cuda:0")
            logger.info(f"[GPU优化] 使用设备: {device}")
        else:
            device = torch.device("cpu")
            if self.settings.use_gpu:
                logger.warning("[GPU优化] GPU不可用，将使用CPU进行推理")
            else:
                logger.info("[GPU优化] GPU已禁用，使用CPU进行推理")
        return device

    def _init_memory_management(self):
        """初始化GPU内存管理"""
        if self.device.type == "cuda":
            try:
                torch.cuda.set_per_process_memory_fraction(
                    self.gpu_memory_fraction, self.device
                )
                torch.cuda.empty_cache()
                logger.info(
                    f"[GPU优化] 内存限制设置为: {self.gpu_memory_fraction * 100:.0f}%"
                )
            except Exception as e:
                logger.warning(f"[GPU优化] 内存管理设置失败: {e}")

    def optimize_model(self, model: torch.nn.Module) -> torch.nn.Module:
        """优化模型以提升推理性能"""
        if self.device.type == "cuda":
            model = model.to(self.device)

            try:
                model = model.eval()
                if hasattr(torch, "compile") and self.settings.use_gpu:
                    try:
                        model = torch.compile(model, mode="max-autotune")
                        logger.info("[GPU优化] 模型已使用torch.compile优化")
                    except Exception as e:
                        logger.debug(f"[GPU优化] torch.compile不可用: {e}")

                logger.info("[GPU优化] 模型已迁移到GPU并设置为评估模式")
            except Exception as e:
                logger.error(f"[GPU优化] 模型优化失败: {e}")

        return model

    def warmup_model(
        self,
        model: torch.nn.Module,
        sample_input: dict,
        iterations: int = 5,
    ) -> None:
        """模型预热，消除首次推理延迟"""
        if self.device.type != "cuda" or self.model_prepared:
            return

        logger.info(f"[GPU优化] 开始模型预热 ({iterations}次迭代)...")
        model.eval()

        try:
            with torch.no_grad():
                for i in range(iterations):
                    inputs = {
                        k: v.to(self.device) if isinstance(v, torch.Tensor) else v
                        for k, v in sample_input.items()
                    }
                    start = time.time()
                    _ = model(**inputs)
                    elapsed = (time.time() - start) * 1000
                    logger.debug(f"  预热迭代 {i+1}/{iterations}: {elapsed:.1f}ms")

            self.model_prepared = True
            logger.info("[GPU优化] 模型预热完成")
        except Exception as e:
            logger.warning(f"[GPU优化] 模型预热失败: {e}")

    def optimize_inference(self, func: Callable) -> Callable:
        """推理优化装饰器：自动混合精度、内存优化"""

        @wraps(func)
        def wrapper(*args, **kwargs):
            if self.device.type == "cuda":
                with torch.no_grad(), torch.cuda.amp.autocast(enabled=True):
                    result = func(*args, **kwargs)
                    torch.cuda.synchronize()
                return result
            else:
                with torch.no_grad():
                    return func(*args, **kwargs)

        return wrapper

    def batch_inference(
        self,
        model: torch.nn.Module,
        batch_data: list,
        batch_size: int = 32,
        collate_fn: Optional[Callable] = None,
    ) -> list:
        """批量推理优化"""
        if not batch_data:
            return []

        results = []
        total_batches = (len(batch_data) + batch_size - 1) // batch_size

        for i in range(0, len(batch_data), batch_size):
            batch = batch_data[i : i + batch_size]

            if collate_fn:
                inputs = collate_fn(batch)
            else:
                inputs = self._default_collate(batch)

            if self.device.type == "cuda":
                inputs = {
                    k: v.to(self.device) if isinstance(v, torch.Tensor) else v
                    for k, v in inputs.items()
                }

            with torch.no_grad():
                batch_results = model(**inputs)
                results.extend(self._process_batch_results(batch_results))

            if self.device.type == "cuda" and (i // batch_size) % 10 == 0:
                torch.cuda.empty_cache()

            logger.debug(
                f"[GPU优化] 批次 {i // batch_size + 1}/{total_batches} 完成"
            )

        return results

    def _default_collate(self, batch: list) -> dict:
        """默认的数据整理函数"""
        tensors = [torch.tensor(x) for x in batch]
        return {"input_ids": torch.stack(tensors)}

    def _process_batch_results(self, batch_results: Any) -> list:
        """处理批量推理结果"""
        if isinstance(batch_results, (list, tuple)):
            return list(batch_results)
        elif isinstance(batch_results, torch.Tensor):
            return batch_results.cpu().tolist()
        else:
            return [batch_results]

    def get_gpu_info(self) -> dict:
        """获取GPU信息"""
        info = {
            "device_type": self.device.type,
            "gpu_available": torch.cuda.is_available(),
            "gpu_count": torch.cuda.device_count() if torch.cuda.is_available() else 0,
        }

        if self.device.type == "cuda":
            props = torch.cuda.get_device_properties(self.device)
            info.update(
                {
                    "gpu_name": props.name,
                    "total_memory_gb": round(props.total_memory / 1024**3, 2),
                    "compute_capability": f"{props.major}.{props.minor}",
                    "memory_allocated_gb": round(
                        torch.cuda.memory_allocated(self.device) / 1024**3, 2
                    ),
                    "memory_cached_gb": round(
                        torch.cuda.memory_reserved(self.device) / 1024**3, 2
                    ),
                }
            )

        return info

    def clear_memory(self):
        """清理GPU内存"""
        if self.device.type == "cuda":
            torch.cuda.empty_cache()
            logger.info("[GPU优化] GPU内存已清理")


_gpu_optimizer = None


def get_gpu_optimizer() -> GpuOptimizer:
    """获取GPU优化器单例"""
    global _gpu_optimizer
    if _gpu_optimizer is None:
        _gpu_optimizer = GpuOptimizer()
    return _gpu_optimizer
