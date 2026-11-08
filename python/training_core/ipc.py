"""Intercambio de pesos con Java: float32 big-endian, plano y sin metadatos.

Debe coincidir byte a byte con el ModelSerializer de Java, porque el contrato
calcula los hashes del protocolo sobre estos mismos bytes.
"""
import struct

import numpy as np


def load_weights_flat(path: str) -> np.ndarray:
    with open(path, 'rb') as f:
        raw = f.read()
    n = len(raw) // 4
    if n * 4 != len(raw):
        raise ValueError(
            f"File size {len(raw)} not multiple of 4 (float32 expected)")
    return np.array(struct.unpack(f'>{n}f', raw), dtype=np.float32)


def save_update_flat(update: np.ndarray, path: str) -> None:
    flat = update.flatten().astype(np.float32)
    with open(path, 'wb') as f:
        f.write(struct.pack(f'>{len(flat)}f', *flat))
