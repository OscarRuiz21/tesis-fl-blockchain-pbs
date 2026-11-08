"""Particionamiento Dirichlet (Hsu et al., 2019) con sesgo de clases según α.

Para cada clase c se sortean proporciones π_c ~ Dir(α) sobre los K clientes.
Con α pequeño cada cliente ve pocas clases; al crecer α la partición se
acerca a IID. Requiere un target discreto.
"""
import numpy as np
import pandas as pd


def partition_dirichlet(
        df: pd.DataFrame,
        K: int,
        alpha: float,
        seed: int,
        target_col: str) -> list[pd.DataFrame]:
    if K < 1:
        raise ValueError(f"K must be >= 1, got {K}")
    if alpha <= 0 or not np.isfinite(alpha):
        raise ValueError(f"alpha must be positive finite, got {alpha}")

    rng = np.random.default_rng(seed)
    classes = df[target_col].unique()
    client_indices: list[list[int]] = [[] for _ in range(K)]

    for cls in classes:
        cls_indices = df.index[df[target_col] == cls].tolist()
        rng.shuffle(cls_indices)
        proportions = rng.dirichlet(np.repeat(alpha, K))
        cumulative = (np.cumsum(proportions) * len(cls_indices)).astype(int)
        splits = np.split(cls_indices, cumulative[:-1])
        for i, split in enumerate(splits):
            client_indices[i].extend(split.tolist())

    result = []
    for i in range(K):
        idx = client_indices[i]
        rng.shuffle(idx)
        result.append(df.loc[idx].reset_index(drop=True))
    return result
