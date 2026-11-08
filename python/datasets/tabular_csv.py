"""Cargador de datasets tabulares en CSV.

Las columnas salen de hyperparams['features'] y hyperparams['target'], que el
coordinator toma del YAML del experimento. Cada cliente normaliza con sus
propias estadísticas, como lo haría con datos privados; el conjunto de prueba
se normaliza con las suyas.
"""
from pathlib import Path

import pandas as pd
import torch


def _validate_schema(
    df: pd.DataFrame,
    features: list[str],
    target: str,
) -> None:
    missing = [f for f in features if f not in df.columns]
    if missing:
        raise ValueError(f"Missing features in CSV: {missing}")
    if target not in df.columns:
        raise ValueError(f"Missing target in CSV: '{target}'")


def _normalize_per_client(
    df: pd.DataFrame,
    features: list[str],
    method: str,
) -> pd.DataFrame:
    if method == 'none':
        return df
    df = df.copy()
    if method == 'zscore':
        for f in features:
            mean, std = df[f].mean(), df[f].std()
            if std == 0:
                continue  # columna constante
            df[f] = (df[f] - mean) / std
    elif method == 'minmax':
        for f in features:
            mn, mx = df[f].min(), df[f].max()
            if mx == mn:
                continue
            df[f] = (df[f] - mn) / (mx - mn)
    else:
        raise ValueError(f"Unknown normalization method: {method}")
    return df


def load_client(
    path: str,
    hyperparams: dict,
) -> tuple[torch.Tensor, torch.Tensor]:
    p = Path(path)
    if not p.exists():
        raise FileNotFoundError(f"Client CSV not found: {path}")

    features: list[str] = hyperparams['features']
    target: str = hyperparams['target']
    normalization: str = hyperparams.get('normalization', 'none')

    df = pd.read_csv(p)
    _validate_schema(df, features, target)
    df = df[features + [target]]
    df = _normalize_per_client(df, features, normalization)

    X = torch.tensor(df[features].values, dtype=torch.float32)
    y = torch.tensor(df[target].values, dtype=torch.float32).unsqueeze(1)
    return X, y


def load_test(
    path: str,
    hyperparams: dict,
) -> tuple[torch.Tensor, torch.Tensor]:
    p = Path(path)
    if not p.exists():
        raise FileNotFoundError(f"Test CSV not found: {path}")

    features: list[str] = hyperparams['features']
    target: str = hyperparams['target']
    normalization: str = hyperparams.get('normalization', 'none')

    df = pd.read_csv(p)
    _validate_schema(df, features, target)
    df = df[features + [target]]
    df = _normalize_per_client(df, features, normalization)

    X = torch.tensor(df[features].values, dtype=torch.float32)
    y = torch.tensor(df[target].values, dtype=torch.float32).unsqueeze(1)
    return X, y
