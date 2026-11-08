#!/usr/bin/env python3
"""Evaluación del modelo global sobre el conjunto de prueba.

El coordinator (PythonEvalGateway) lo invoca como subproceso en cada ronda.
Lee los pesos de --model_in (float32 big-endian) y escribe
"accuracy=X loss=Y" en la última línea de stdout; ante un error escribe en
stderr y termina con código 1.
"""
import argparse
import json
import sys

from python.datasets import DATASET_LOADERS
from python.models import MODEL_REGISTRY
from python.training_core.ipc import load_weights_flat


def main() -> int:
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--model_name', required=True, choices=list(MODEL_REGISTRY.keys()))
    p.add_argument('--num_params', type=int, required=True)
    p.add_argument('--model_in', required=True)
    p.add_argument('--testset', required=True)
    p.add_argument('--hyperparams', required=True,
                   help='JSON con features/target/normalization.')
    args = p.parse_args()

    try:
        hyperparams = json.loads(args.hyperparams)

        model = MODEL_REGISTRY[args.model_name](args.num_params, hyperparams)
        expected = model.num_params()
        if expected != args.num_params:
            raise ValueError(
                f"Model '{args.model_name}' has num_params={expected}, "
                f"declared {args.num_params}")

        weights = load_weights_flat(args.model_in)
        if len(weights) != expected:
            raise ValueError(
                f"Weight count mismatch: expected {expected}, got {len(weights)}")
        model.set_flat_weights(weights)

        loader = DATASET_LOADERS[model.dataset_type()]['load_test']
        X, y = loader(args.testset, hyperparams)

        stats = model.evaluate(X, y)

        print(f"accuracy={stats.accuracy:.6f} loss={stats.loss:.6f}")
        return 0
    except Exception as e:
        print(f"ERROR: {e}", file=sys.stderr)
        return 1


if __name__ == '__main__':
    sys.exit(main())
