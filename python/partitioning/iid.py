"""Particionamiento IID: barajar y dividir en K partes de tamaño casi igual.

Cada cliente recibe unas N/K filas con una proporción de clases parecida a la
global. Es el régimen de referencia frente a la partición Dirichlet.
"""
import numpy as np
import pandas as pd


def partition_iid(df: pd.DataFrame, K: int, seed: int) -> list[pd.DataFrame]:
    if K < 1:
        raise ValueError(f"K must be >= 1, got {K}")
    if len(df) < K:
        raise ValueError(f"Cannot partition {len(df)} rows into {K} clients")

    shuffled = df.sample(frac=1, random_state=seed).reset_index(drop=True)
    parts = np.array_split(shuffled, K)
    return [pd.DataFrame(p, columns=df.columns).reset_index(drop=True) for p in parts]
