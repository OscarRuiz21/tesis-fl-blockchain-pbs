"""Registro de modelos por nombre (model.name del YAML del experimento).

Cada constructor recibe (num_params, hyperparams) y devuelve un FLModel;
train.py y eval.py comprueban después que model.num_params() coincida con el
valor declarado. Sin hyperparams se usan valores por omisión para los tests.
"""
from typing import Callable, Optional

from python.models.base import FLModel
from python.models.logistic_regression import LogisticRegression
from python.models.mlp import MLP


def _features_len(hyperparams: Optional[dict], fallback_n_features: int) -> int:
    if hyperparams and 'features' in hyperparams:
        return len(hyperparams['features'])
    return fallback_n_features


def _build_logistic_regression(
    num_params: int = 26,
    hyperparams: Optional[dict] = None,
) -> FLModel:
    """num_params = n_features + 1 (incluye el sesgo)."""
    n_features = _features_len(hyperparams, num_params - 1)
    return LogisticRegression(n_features=n_features, fit_intercept=True)


def _build_mlp(
    num_params: int = 321,
    hyperparams: Optional[dict] = None,
) -> FLModel:
    n_features = _features_len(hyperparams, 10)
    hidden_dims = (hyperparams or {}).get('hidden_dims', [16, 8])
    dropout = float((hyperparams or {}).get('dropout', 0.2))
    return MLP(n_features=n_features, hidden_dims=hidden_dims, dropout=dropout)


MODEL_REGISTRY: dict[str, Callable[..., FLModel]] = {
    'logistic_regression': _build_logistic_regression,
    'mlp': _build_mlp,
}
