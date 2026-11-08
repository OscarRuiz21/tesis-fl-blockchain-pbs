"""Cargadores de datos por dataset_type() del modelo.

Cada entrada expone load_client y load_test con la firma
(path, hyperparams) -> (X, y).
"""
from python.datasets import tabular_csv


DATASET_LOADERS: dict[str, dict[str, callable]] = {
    'tabular_csv': {
        'load_client': tabular_csv.load_client,
        'load_test': tabular_csv.load_test,
    },
}
