#!/usr/bin/env python3
"""Entrenamiento local de una ronda.

El cliente Java (PythonTrainingGateway) lo invoca como subproceso en cada
ronda. El modelo y el cargador de datos se eligen por nombre en
MODEL_REGISTRY y DATASET_LOADERS.

Entrada: pesos globales en --model_in (float32 big-endian).
Salida: delta de pesos en --update_out (float32 big-endian) y, en la última
línea de stdout, "accuracy=X loss=Y num_samples=N". Ante cualquier error
escribe en stderr y termina con código 1.
"""
import argparse
import json
import sys

import numpy as np
import torch

from python.datasets import DATASET_LOADERS
from python.models import MODEL_REGISTRY
from python.training_core.federated import compute_update_delta
from python.training_core.ipc import load_weights_flat, save_update_flat


def main() -> int:
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--model_name', required=True, choices=list(MODEL_REGISTRY.keys()))
    p.add_argument('--num_params', type=int, required=True)
    p.add_argument('--model_in', required=True)
    p.add_argument('--update_out', required=True)
    p.add_argument('--data', required=True)
    p.add_argument('--client_id', required=True)
    p.add_argument('--hyperparams', required=True,
                   help='JSON con epochs/lr/batch_size/features/target/etc.')
    p.add_argument('--seed', type=int, default=42)
    args = p.parse_args()

    try:
        torch.manual_seed(args.seed)
        np.random.seed(args.seed)

        hyperparams = json.loads(args.hyperparams)

        # LR se dimensiona con num_params; el MLP con features y hidden_dims.
        model = MODEL_REGISTRY[args.model_name](args.num_params, hyperparams)

        # num_params debe coincidir con el que el contrato valida en cada update.
        expected = model.num_params()
        if expected != args.num_params:
            raise ValueError(
                f"Model '{args.model_name}' has num_params={expected}, "
                f"YAML declares {args.num_params}")

        weights_in = load_weights_flat(args.model_in)
        if len(weights_in) != expected:
            raise ValueError(
                f"Weight count mismatch: expected {expected}, got {len(weights_in)}")

        # En la ronda 1 el contrato entrega pesos en cero. Cargarlos en un MLP
        # anula el gradiente de las capas internas (ReLU) y el modelo no
        # aprende, así que se conserva la inicialización del constructor (He en
        # el MLP), igual en todos los clientes porque la semilla es la misma.
        # El delta se sigue calculando contra los ceros recibidos, de modo que
        # el FedAvg del contrato (w + media de deltas) da la media de los
        # modelos entrenados.
        if np.any(weights_in):
            model.set_flat_weights(weights_in)

        loader = DATASET_LOADERS[model.dataset_type()]['load_client']
        X, y = loader(args.data, hyperparams)

        stats = model.fit(X, y, hyperparams)

        delta = compute_update_delta(weights_in, model.get_flat_weights())
        save_update_flat(delta, args.update_out)

        # El cliente Java lee esta línea para sus métricas.
        print(f"accuracy={stats.accuracy:.6f} "
              f"loss={stats.loss:.6f} "
              f"num_samples={stats.num_samples}")
        return 0
    except Exception as e:
        print(f"ERROR: {e}", file=sys.stderr)
        return 1


if __name__ == '__main__':
    sys.exit(main())
