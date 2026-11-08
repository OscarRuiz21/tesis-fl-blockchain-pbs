#!/usr/bin/env python3
"""Implementación independiente del Tally Hash, para comparar contra Java.

En operación el Tally Hash solo lo calcula el contrato
(ProtocolHash.computeTallyHashHex). Este script es un segundo testigo para
JavaPythonHashCompatibilityTest: una comparación Java contra Java no detecta
un error de codificación, de orden o de endianness que esté en ambos lados.

Replica byte a byte org.fl.flprotocol.crypto.ProtocolHash:

    1. h_k = SHA-256(Δw_k), en hex en minúsculas
    2. {h_k} en orden lexicográfico por hex
    3. T_r = SHA-256( bytes(h_1) ‖ ... ‖ bytes(h_K) )

donde bytes(h_k) son los 32 bytes binarios del digest, no su texto hex. El
orden de los archivos en la línea de comandos no altera el resultado.

Uso:
    python/.venv/bin/python python/tests/compute_tally_hash.py \\
        update_0.bin update_1.bin update_2.bin
"""
import argparse
import hashlib
import sys


def main() -> int:
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument(
        'files',
        nargs='+',
        help='Paths a archivos de update (cualquier orden)',
    )
    args = p.parse_args()

    hashes_hex = []
    for path in args.files:
        with open(path, 'rb') as f:
            hashes_hex.append(hashlib.sha256(f.read()).hexdigest())

    hashes_hex.sort()

    final = hashlib.sha256()
    for h in hashes_hex:
        final.update(bytes.fromhex(h))

    print(final.hexdigest())
    return 0


if __name__ == '__main__':
    sys.exit(main())
