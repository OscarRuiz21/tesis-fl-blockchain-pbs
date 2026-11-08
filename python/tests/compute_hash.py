#!/usr/bin/env python3
"""SHA-256 de un archivo, para comparar contra la implementación Java.

Imprime el digest en hex en minúsculas de los bytes del archivo, sin
interpretarlos, igual que MessageDigest.getInstance("SHA-256") en Java. Lo
invoca JavaPythonHashCompatibilityTest del AS.

Uso:
    python/.venv/bin/python python/tests/compute_hash.py --input weights.bin
"""
import argparse
import hashlib
import sys


def main() -> int:
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--input', required=True, help='Path al archivo binario')
    args = p.parse_args()

    with open(args.input, 'rb') as f:
        data = f.read()

    print(hashlib.sha256(data).hexdigest())
    return 0


if __name__ == '__main__':
    sys.exit(main())
