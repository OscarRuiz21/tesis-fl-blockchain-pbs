"""Tests de particionamiento IID y Dirichlet."""
import numpy as np
import pandas as pd

from python.partitioning.dirichlet import partition_dirichlet
from python.partitioning.iid import partition_iid


def _build_df(n_per_class: int = 200, n_classes: int = 2) -> pd.DataFrame:
    """DataFrame con n_classes balanceados y feature dummy."""
    rows = []
    for c in range(n_classes):
        for _ in range(n_per_class):
            rows.append({'feature': float(c), 'target': c})
    return pd.DataFrame(rows)


def test_iid_splits_evenly():
    df = _build_df(n_per_class=200, n_classes=2)  # 400 rows
    parts = partition_iid(df, K=5, seed=42)
    assert len(parts) == 5
    sizes = [len(p) for p in parts]
    # 400/5 = 80 ± 1
    assert all(abs(s - 80) <= 1 for s in sizes), f"sizes={sizes}"


def test_iid_same_seed_same_splits():
    df = _build_df(n_per_class=100, n_classes=2)
    p1 = partition_iid(df, K=3, seed=42)
    p2 = partition_iid(df, K=3, seed=42)
    for a, b in zip(p1, p2):
        pd.testing.assert_frame_equal(
            a.reset_index(drop=True), b.reset_index(drop=True))


def test_iid_different_seeds_different_splits():
    df = _build_df(n_per_class=100, n_classes=2)
    p1 = partition_iid(df, K=3, seed=42)
    p2 = partition_iid(df, K=3, seed=999)
    # Al menos una partición debe diferir.
    diff = any(
        not a.reset_index(drop=True).equals(b.reset_index(drop=True))
        for a, b in zip(p1, p2)
    )
    assert diff, "Different seeds should produce different splits"


def test_iid_preserves_all_rows():
    df = _build_df(n_per_class=100, n_classes=2)
    parts = partition_iid(df, K=4, seed=42)
    total = sum(len(p) for p in parts)
    assert total == len(df)


def test_dirichlet_low_alpha_produces_skew():
    """Con α = 0.1 las particiones se concentran en pocas clases."""
    df = _build_df(n_per_class=200, n_classes=2)
    parts = partition_dirichlet(df, K=5, alpha=0.1, target_col='target', seed=42)
    assert len(parts) == 5

    # Al menos una partición debe estar fuertemente sesgada (>80% de una clase).
    skew_found = False
    for p in parts:
        if len(p) == 0:
            continue
        class_props = p['target'].value_counts(normalize=True)
        if class_props.max() > 0.8:
            skew_found = True
            break
    assert skew_found, \
        f"α=0.1 should produce at least one heavily-skewed partition"


def test_dirichlet_high_alpha_approaches_iid():
    """Con α = 100 las particiones quedan casi balanceadas."""
    df = _build_df(n_per_class=500, n_classes=2)
    parts = partition_dirichlet(df, K=5, alpha=100.0, target_col='target', seed=42)

    for p in parts:
        if len(p) < 10:
            continue
        class_props = p['target'].value_counts(normalize=True)
        # Con α alto y K=5, n=1000, esperamos ~50/50 ± 10%.
        assert 0.35 <= class_props.iloc[0] <= 0.65, \
            f"α=100 should be close to balanced, got {class_props.to_dict()}"


def test_dirichlet_preserves_all_rows():
    df = _build_df(n_per_class=100, n_classes=2)
    parts = partition_dirichlet(df, K=4, alpha=1.0, target_col='target', seed=42)
    total = sum(len(p) for p in parts)
    assert total == len(df)
