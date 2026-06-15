import math
from typing import Optional, List, Dict, Any, Tuple
from dataclasses import dataclass, field
from loguru import logger

import torch
import torch.nn as nn
from torch.optim import Optimizer
from torch.utils.data import DataLoader
import numpy as np


@dataclass
class PrivacyBudget:
    """隐私预算跟踪器"""
    epsilon: float = 1.0
    delta: float = 1e-5
    spent_epsilon: float = 0.0
    spent_delta: float = 0.0
    max_epsilon: float = 10.0

    def consume(self, epsilon: float, delta: float) -> bool:
        """消耗隐私预算"""
        if self.spent_epsilon + epsilon > self.max_epsilon:
            logger.warning(
                f"[差分隐私] 隐私预算不足: 已消耗 {self.spent_epsilon:.4f}, "
                f"本次需要 {epsilon:.4f}, 上限 {self.max_epsilon}"
            )
            return False

        self.spent_epsilon += epsilon
        self.spent_delta += delta
        logger.info(
            f"[差分隐私] 隐私预算消耗: +{epsilon:.4f} ε, "
            f"总计: {self.spent_epsilon:.4f}/{self.max_epsilon} ε"
        )
        return True

    def remaining(self) -> float:
        """剩余隐私预算"""
        return self.max_epsilon - self.spent_epsilon

    def reset(self):
        """重置隐私预算"""
        self.spent_epsilon = 0.0
        self.spent_delta = 0.0
        logger.info("[差分隐私] 隐私预算已重置")


@dataclass
class DPConfig:
    """差分隐私配置"""
    enabled: bool = True
    noise_multiplier: float = 1.0
    max_grad_norm: float = 1.0
    target_epsilon: float = 1.0
    target_delta: float = 1e-5
    clipping_mode: str = "flat"
    noise_type: str = "gaussian"


class GradientClipper:
    """梯度裁剪器"""

    def __init__(self, max_norm: float = 1.0, mode: str = "flat"):
        self.max_norm = max_norm
        self.mode = mode

    def clip_grads(self, model: nn.Module) -> float:
        """裁剪模型梯度，返回裁剪前的总范数"""
        if self.mode == "flat":
            total_norm = torch.nn.utils.clip_grad_norm_(
                model.parameters(), self.max_norm
            )
        elif self.mode == "per_layer":
            total_norm = 0.0
            for param in model.parameters():
                if param.grad is not None:
                    param_norm = param.grad.data.norm(2)
                    total_norm += param_norm.item() ** 2
                    clip_coef = self.max_norm / (param_norm + 1e-6)
                    if clip_coef < 1:
                        param.grad.data.mul_(clip_coef)
            total_norm = total_norm ** 0.5
        else:
            raise ValueError(f"未知的裁剪模式: {self.mode}")

        return float(total_norm)


class GaussianNoiseInjector:
    """高斯噪声注入器"""

    def __init__(self, noise_multiplier: float = 1.0, max_norm: float = 1.0):
        self.noise_multiplier = noise_multiplier
        self.max_norm = max_norm
        self.sigma = noise_multiplier * max_norm

    def inject_noise(self, model: nn.Module, batch_size: int) -> None:
        """向模型参数梯度注入高斯噪声"""
        noise_std = self.sigma / batch_size

        for param in model.parameters():
            if param.grad is not None:
                noise = torch.normal(
                    mean=0.0,
                    std=noise_std,
                    size=param.grad.shape,
                    device=param.grad.device,
                    dtype=param.grad.dtype,
                )
                param.grad.add_(noise)


class DifferentialPrivacyEngine:
    """差分隐私训练引擎"""

    def __init__(
        self,
        model: nn.Module,
        optimizer: Optimizer,
        config: Optional[DPConfig] = None,
        privacy_budget: Optional[PrivacyBudget] = None,
    ):
        self.config = config or DPConfig()
        self.model = model
        self.optimizer = optimizer
        self.privacy_budget = privacy_budget or PrivacyBudget()

        if self.config.enabled:
            self.clipper = GradientClipper(
                max_norm=self.config.max_grad_norm,
                mode=self.config.clipping_mode,
            )
            self.noise_injector = GaussianNoiseInjector(
                noise_multiplier=self.config.noise_multiplier,
                max_norm=self.config.max_grad_norm,
            )
            logger.info(
                f"[差分隐私] 引擎已初始化 - "
                f"噪声乘数: {self.config.noise_multiplier}, "
                f"最大梯度范数: {self.config.max_grad_norm}, "
                f"目标ε: {self.config.target_epsilon}"
            )

    def compute_privacy_spent(
        self,
        steps: int,
        batch_size: int,
        dataset_size: int,
    ) -> Tuple[float, float]:
        """计算已消耗的隐私预算（使用RDP方法近似）"""
        if not self.config.enabled:
            return 0.0, 0.0

        q = batch_size / dataset_size
        sigma = self.config.noise_multiplier
        delta = self.config.target_delta

        epsilon = self._compute_epsilon_rdp(q, sigma, steps, delta)
        return epsilon, delta

    def _compute_epsilon_rdp(
        self, q: float, sigma: float, steps: int, delta: float
    ) -> float:
        """使用RDP（Renyi Differential Privacy）计算epsilon"""
        try:
            orders = [1.0 + x / 10.0 for x in range(1, 100)] + list(range(11, 64))

            rdp_eps = []
            for alpha in orders:
                rdp_eps.append(self._compute_rdp_for_alpha(q, sigma, steps, alpha))

            epsilon = min(
                [
                    eps + (math.log(1 / delta) / (alpha - 1))
                    for eps, alpha in zip(rdp_eps, orders)
                ]
            )
            return float(epsilon)
        except Exception as e:
            logger.warning(f"[差分隐私] RDP计算失败，使用近似值: {e}")
            return self._compute_epsilon_approx(q, sigma, steps, delta)

    def _compute_rdp_for_alpha(
        self, q: float, sigma: float, steps: int, alpha: float
    ) -> float:
        """计算特定alpha阶的RDP epsilon"""
        if q == 0:
            return 0.0

        try:
            log_a = -math.log1p(-q)
            log_b = -2 * math.log(sigma)
            log_c = -math.log(2 * math.pi)

            term1 = alpha * log_a
            term2 = (alpha - 1) * log_b
            term3 = 0.5 * (alpha - 1) * log_c
            term4 = 0.5 * alpha * (alpha - 1) / (sigma ** 2)

            rdp = term1 + term2 + term3 + term4
            return float(rdp * steps)
        except Exception:
            return float(steps * alpha / (2 * sigma ** 2))

    def _compute_epsilon_approx(
        self, q: float, sigma: float, steps: int, delta: float
    ) -> float:
        """近似计算epsilon"""
        epsilon = (
            math.sqrt(2 * steps * q * math.log(1 / delta))
            * (1 + q)
            / sigma
        )
        return float(min(epsilon, self.privacy_budget.max_epsilon))

    def privacy_step(
        self,
        batch_size: int,
        dataset_size: int,
        current_step: int,
    ) -> Tuple[bool, float]:
        """执行隐私保护步骤（裁剪+加噪），返回是否成功和梯度范数"""
        if not self.config.enabled:
            return True, 0.0

        grad_norm = self.clipper.clip_grads(self.model)
        self.noise_injector.inject_noise(self.model, batch_size)

        eps, _ = self.compute_privacy_spent(current_step, batch_size, dataset_size)

        if not self.privacy_budget.consume(eps - self.privacy_budget.spent_epsilon, self.config.target_delta):
            return False, grad_norm

        return True, grad_norm

    def get_privacy_report(self) -> Dict[str, Any]:
        """获取隐私报告"""
        return {
            "enabled": self.config.enabled,
            "noise_multiplier": self.config.noise_multiplier,
            "max_grad_norm": self.config.max_grad_norm,
            "target_epsilon": self.config.target_epsilon,
            "target_delta": self.config.target_delta,
            "spent_epsilon": self.privacy_budget.spent_epsilon,
            "remaining_epsilon": self.privacy_budget.remaining(),
            "max_epsilon": self.privacy_budget.max_epsilon,
        }


class DPSGD(Optimizer):
    """差分隐私SGD优化器"""

    def __init__(
        self,
        params,
        lr: float = 0.01,
        noise_multiplier: float = 1.0,
        max_grad_norm: float = 1.0,
        batch_size: int = 1,
        **kwargs,
    ):
        defaults = dict(lr=lr, **kwargs)
        super().__init__(params, defaults)

        self.noise_multiplier = noise_multiplier
        self.max_grad_norm = max_grad_norm
        self.sigma = noise_multiplier * max_grad_norm
        self.batch_size = batch_size
        self.dp_engine = None

    def set_dp_engine(self, dp_engine: DifferentialPrivacyEngine):
        self.dp_engine = dp_engine

    @torch.no_grad()
    def step(self, closure=None):
        loss = None
        if closure is not None:
            with torch.enable_grad():
                loss = closure()

        if self.dp_engine and self.dp_engine.config.enabled:
            for group in self.param_groups:
                torch.nn.utils.clip_grad_norm_(
                    group["params"], self.max_grad_norm
                )

                for param in group["params"]:
                    if param.grad is None:
                        continue

                    noise_std = self.sigma / max(self.batch_size, 1)
                    noise = torch.normal(
                        mean=0.0,
                        std=noise_std,
                        size=param.grad.shape,
                        device=param.grad.device,
                        dtype=param.grad.dtype,
                    )
                    param.grad.add_(noise)

        for group in self.param_groups:
            for param in group["params"]:
                if param.grad is None:
                    continue
                param.add_(param.grad, alpha=-group["lr"])

        return loss


def add_laplace_noise(
    value: float,
    epsilon: float,
    sensitivity: float = 1.0,
) -> float:
    """向单个数值添加拉普拉斯噪声"""
    scale = sensitivity / epsilon
    noise = np.random.laplace(loc=0.0, scale=scale)
    return float(value + noise)


def privatize_counts(
    counts: List[int],
    epsilon: float = 1.0,
    sensitivity: float = 1.0,
    min_value: int = 0,
) -> List[int]:
    """对计数数据进行隐私保护处理"""
    scale = sensitivity / epsilon
    noisy_counts = []

    for count in counts:
        noise = np.random.laplace(loc=0.0, scale=scale)
        noisy_count = int(round(max(count + noise, min_value)))
        noisy_counts.append(noisy_count)

    return noisy_counts


_dp_engine_cache: Dict[str, DifferentialPrivacyEngine] = {}


def get_dp_engine(
    engine_id: str = "default",
    model: Optional[nn.Module] = None,
    optimizer: Optional[Optimizer] = None,
    config: Optional[DPConfig] = None,
) -> Optional[DifferentialPrivacyEngine]:
    """获取或创建差分隐私引擎"""
    if engine_id not in _dp_engine_cache:
        if model is None or optimizer is None:
            return None
        _dp_engine_cache[engine_id] = DifferentialPrivacyEngine(
            model=model, optimizer=optimizer, config=config
        )
    return _dp_engine_cache[engine_id]
