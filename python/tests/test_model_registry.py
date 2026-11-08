"""Consistencia entre MODEL_REGISTRY y DATASET_LOADERS."""
from python.datasets import DATASET_LOADERS
from python.models import MODEL_REGISTRY
from python.models.base import FLModel


def test_registry_has_mvp_model():
    assert 'logistic_regression' in MODEL_REGISTRY


def test_each_factory_produces_flmodel():
    for name, factory in MODEL_REGISTRY.items():
        instance = factory()
        assert isinstance(instance, FLModel), \
            f"Model '{name}' factory must produce FLModel"


def test_each_model_dataset_type_exists_in_loaders():
    """Si falta el cargador, train.py falla en el primer load_client."""
    for name, factory in MODEL_REGISTRY.items():
        instance = factory()
        dt = instance.dataset_type()
        assert dt in DATASET_LOADERS, \
            f"Model '{name}' declares dataset_type='{dt}' " \
            f"but it's not in DATASET_LOADERS"


def test_each_loader_has_load_client_and_load_test():
    for dt, loaders in DATASET_LOADERS.items():
        assert 'load_client' in loaders, \
            f"Dataset '{dt}' missing load_client"
        assert 'load_test' in loaders, \
            f"Dataset '{dt}' missing load_test"
        assert callable(loaders['load_client'])
        assert callable(loaders['load_test'])


def test_num_params_is_int_positive():
    for name, factory in MODEL_REGISTRY.items():
        instance = factory()
        n = instance.num_params()
        assert isinstance(n, int)
        assert n > 0, f"Model '{name}' has num_params={n}"


def test_logistic_regression_num_params_is_26():
    """Valor por omisión del constructor: 25 features + sesgo."""
    model = MODEL_REGISTRY['logistic_regression']()
    assert model.num_params() == 26


def test_logistic_regression_factory_is_parametric():
    """GMSC tiene 10 features, es decir, 11 parámetros."""
    model_gmsc = MODEL_REGISTRY['logistic_regression'](11)
    assert model_gmsc.num_params() == 11
    model_credit = MODEL_REGISTRY['logistic_regression'](26)
    assert model_credit.num_params() == 26
