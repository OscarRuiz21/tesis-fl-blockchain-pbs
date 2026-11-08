"""Tests del formato binario de pesos.

La prueba de paridad con Java se omite mientras no exista tests/vectors/.
"""
import hashlib
import os
import struct
import tempfile

import numpy as np
import pytest

from python.training_core.ipc import load_weights_flat, save_update_flat


def test_round_trip_simple():
    arr = np.array([1.5, -0.3, 0.0, 1e-9, 1e9], dtype=np.float32)
    with tempfile.TemporaryDirectory() as d:
        path = os.path.join(d, 'w.bin')
        save_update_flat(arr, path)
        back = load_weights_flat(path)
        np.testing.assert_array_equal(arr, back)


def test_round_trip_random():
    rng = np.random.default_rng(42)
    arr = rng.normal(size=1000).astype(np.float32)
    with tempfile.TemporaryDirectory() as d:
        path = os.path.join(d, 'w.bin')
        save_update_flat(arr, path)
        back = load_weights_flat(path)
        np.testing.assert_allclose(arr, back, atol=0.0)


def test_byte_size_matches_float32():
    arr = np.array([1.0, 2.0, 3.0], dtype=np.float32)
    with tempfile.TemporaryDirectory() as d:
        path = os.path.join(d, 'w.bin')
        save_update_flat(arr, path)
        assert os.path.getsize(path) == 12  # 3 * 4 bytes


def test_load_rejects_non_multiple_of_four():
    with tempfile.TemporaryDirectory() as d:
        path = os.path.join(d, 'w.bin')
        with open(path, 'wb') as f:
            f.write(b'\x00\x00\x00')  # 3 bytes
        with pytest.raises(ValueError, match='not multiple of 4'):
            load_weights_flat(path)


def test_big_endian_encoding():
    arr = np.array([1.0], dtype=np.float32)
    with tempfile.TemporaryDirectory() as d:
        path = os.path.join(d, 'w.bin')
        save_update_flat(arr, path)
        with open(path, 'rb') as f:
            raw = f.read()
        # 1.0 en float32 BE es 0x3F800000.
        assert raw == b'\x3f\x80\x00\x00'


def test_sha256_known_value():
    """Los bytes escritos son exactamente los que hashea compute_hash.py."""
    arr = np.array([0.0] * 26, dtype=np.float32)
    expected = struct.pack('>26f', *([0.0] * 26))
    with tempfile.TemporaryDirectory() as d:
        path = os.path.join(d, 'w.bin')
        save_update_flat(arr, path)
        with open(path, 'rb') as f:
            data = f.read()
        assert data == expected
        h = hashlib.sha256(data).hexdigest()
        assert len(h) == 64
