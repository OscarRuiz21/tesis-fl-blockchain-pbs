"""Clase base de los modelos federados.

Cada modelo declara su dimensión y el cargador de datos que usa, y contiene
sus ciclos de entrenamiento y evaluación. Los pesos se exponen como un vector
plano, que training_core/ipc.py serializa en el formato del ModelSerializer
de Java.
"""
from abc import abstractmethod
from dataclasses import dataclass

import numpy as np
import torch
import torch.nn as nn


@dataclass(frozen=True)
class TrainStats:
    loss: float
    accuracy: float
    num_samples: int


@dataclass(frozen=True)
class EvalStats:
    loss: float
    accuracy: float


class FLModel(nn.Module):
    """Base de los modelos PyTorch del prototipo.

    Hereda de nn.Module, que no usa ABCMeta, así que @abstractmethod solo
    documenta los métodos que cada subclase debe implementar.
    """

    def count_params(self) -> int:
        return sum(p.numel() for p in self.parameters())

    def get_flat_weights(self) -> np.ndarray:
        """El orden es el de self.parameters(); el contrato agrega en ese orden."""
        return np.concatenate([
            p.data.cpu().numpy().flatten() for p in self.parameters()
        ])

    def set_flat_weights(self, weights: np.ndarray) -> None:
        offset = 0
        for param in self.parameters():
            numel = param.numel()
            chunk = weights[offset:offset + numel]
            if chunk.shape[0] != numel:
                raise RuntimeError(
                    f"Weight shape mismatch at offset {offset}: "
                    f"expected {numel}, got {chunk.shape[0]}")
            param.data.copy_(
                torch.tensor(chunk, dtype=torch.float32).reshape(param.shape))
            offset += numel
        if offset != len(weights):
            raise RuntimeError(
                f"Weights not fully consumed: {len(weights) - offset} leftover")

    @abstractmethod
    def num_params(self) -> int:
        """Debe coincidir con el num_params del YAML: el contrato valida la
        dimensión de cada update contra ese valor."""

    @abstractmethod
    def dataset_type(self) -> str:
        """Clave en DATASET_LOADERS."""

    @abstractmethod
    def fit(self, X: torch.Tensor, y: torch.Tensor, hyperparams: dict) -> TrainStats:
        """Entrena en sitio sobre los datos locales."""

    @abstractmethod
    def evaluate(self, X: torch.Tensor, y: torch.Tensor) -> EvalStats:
        """Evalúa sin modificar los pesos."""
