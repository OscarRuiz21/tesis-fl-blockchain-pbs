"""Tests del loader tabular_csv: schema, normalización, retorno de tensores."""
import os
import tempfile

import pandas as pd
import pytest
import torch

from python.datasets.tabular_csv import load_client, load_test


def _write_csv(rows: list[dict]) -> str:
    """Escribe un CSV temporal y retorna su path."""
    fd, path = tempfile.mkstemp(suffix='.csv')
    os.close(fd)
    pd.DataFrame(rows).to_csv(path, index=False)
    return path


def test_load_client_returns_tensors_with_correct_shape():
    path = _write_csv([
        {'a': 1.0, 'b': 2.0, 'target': 0},
        {'a': 3.0, 'b': 4.0, 'target': 1},
        {'a': 5.0, 'b': 6.0, 'target': 0},
    ])
    try:
        X, y = load_client(path, {
            'features': ['a', 'b'],
            'target': 'target',
            'normalization': 'none',
        })
        assert isinstance(X, torch.Tensor)
        assert isinstance(y, torch.Tensor)
        assert X.shape == (3, 2)
        assert y.shape == (3, 1)
        assert X.dtype == torch.float32
        assert y.dtype == torch.float32
    finally:
        os.unlink(path)


def test_load_client_rejects_missing_target():
    path = _write_csv([{'a': 1.0, 'b': 2.0}])
    try:
        with pytest.raises(ValueError, match='Missing target'):
            load_client(path, {
                'features': ['a', 'b'],
                'target': 'target',
                'normalization': 'none',
            })
    finally:
        os.unlink(path)


def test_load_client_rejects_missing_features():
    path = _write_csv([{'a': 1.0, 'target': 0}])
    try:
        with pytest.raises(ValueError, match='Missing features'):
            load_client(path, {
                'features': ['a', 'b'],  # 'b' falta
                'target': 'target',
                'normalization': 'none',
            })
    finally:
        os.unlink(path)


def test_load_client_zscore_normalization():
    path = _write_csv([
        {'a': 1.0, 'target': 0},
        {'a': 2.0, 'target': 0},
        {'a': 3.0, 'target': 1},
    ])
    try:
        X, _ = load_client(path, {
            'features': ['a'],
            'target': 'target',
            'normalization': 'zscore',
        })
        # zscore con media 2 y desviación 1: (-1, 0, 1).
        assert abs(X.mean().item()) < 1e-5
        assert abs(X.std().item() - 1.0) < 0.1
    finally:
        os.unlink(path)


def test_load_client_minmax_normalization():
    path = _write_csv([
        {'a': 0.0, 'target': 0},
        {'a': 5.0, 'target': 0},
        {'a': 10.0, 'target': 1},
    ])
    try:
        X, _ = load_client(path, {
            'features': ['a'],
            'target': 'target',
            'normalization': 'minmax',
        })
        # minmax: 0, 5, 10 pasan a 0, 0.5, 1.
        assert X[0].item() == 0.0
        assert abs(X[1].item() - 0.5) < 1e-5
        assert X[2].item() == 1.0
    finally:
        os.unlink(path)


def test_load_test_returns_tensors():
    path = _write_csv([
        {'x': 1.0, 'y': 2.0, 'label': 0},
        {'x': 3.0, 'y': 4.0, 'label': 1},
    ])
    try:
        X, y = load_test(path, {
            'features': ['x', 'y'],
            'target': 'label',
        })
        assert X.shape == (2, 2)
        assert y.shape == (2, 1)
    finally:
        os.unlink(path)


def test_load_client_filters_to_features_and_target():
    """Columnas extra del CSV deben ignorarse."""
    path = _write_csv([
        {'a': 1.0, 'b': 2.0, 'target': 0, 'noise': 99.0},
    ])
    try:
        X, _ = load_client(path, {
            'features': ['a', 'b'],
            'target': 'target',
            'normalization': 'none',
        })
        assert X.shape == (1, 2)
    finally:
        os.unlink(path)
