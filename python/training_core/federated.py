"""Operaciones FL comunes: cómputo del delta y término proximal de FedProx."""
import numpy as np
import torch


def compute_update_delta(weights_in: np.ndarray, weights_out: np.ndarray) -> np.ndarray:
    """Δw = w_local - w_global; el cliente envía Δw y el contrato lo agrega."""
    return weights_out - weights_in


def proximal_term(model, global_params, mu: float) -> torch.Tensor:
    """FedProx: (μ/2) · ||w - w_global||², con w_global copiado al inicio de la ronda."""
    if mu <= 0:
        return torch.tensor(0.0)
    s = torch.tensor(0.0)
    for p, g in zip(model.parameters(), global_params):
        s = s + ((p - g) ** 2).sum()
    return (mu / 2.0) * s
