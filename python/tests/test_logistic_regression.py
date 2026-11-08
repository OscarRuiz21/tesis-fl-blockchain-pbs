"""Tests de LogisticRegression."""
import numpy as np
import torch

from python.models.logistic_regression import LogisticRegression


def _build_separable_data(n_per_class: int = 100, seed: int = 0):
    """Dos nubes linealmente separables en (-1,-1) y (1,1)."""
    rng = np.random.default_rng(seed)
    X0 = rng.normal(loc=-1.0, scale=0.3, size=(n_per_class, 2))
    X1 = rng.normal(loc=1.0, scale=0.3, size=(n_per_class, 2))
    X = np.vstack([X0, X1]).astype(np.float32)
    y = np.array([0.0] * n_per_class + [1.0] * n_per_class, dtype=np.float32)
    return (
        torch.tensor(X),
        torch.tensor(y).unsqueeze(1),
    )


def _default_hyperparams(**overrides) -> dict:
    base = {
        'epochs': 100,
        'batch_size': 32,
        'lr': 0.1,
        'strategy': 'fedavg',
        'mu': 0.0,
        'regularization': 'none',
        'reg_strength': 0.0,
        'balance_classes': False,
    }
    base.update(overrides)
    return base


def test_num_params_matches_n_features_plus_bias():
    model = LogisticRegression(n_features=25, fit_intercept=True)
    assert model.num_params() == 26


def test_num_params_without_bias():
    model = LogisticRegression(n_features=10, fit_intercept=False)
    assert model.num_params() == 10


def test_dataset_type_is_tabular_csv():
    model = LogisticRegression(n_features=2, fit_intercept=True)
    assert model.dataset_type() == 'tabular_csv'


def test_fit_converges_on_separable_data():
    torch.manual_seed(42)
    X, y = _build_separable_data(n_per_class=100, seed=42)

    model = LogisticRegression(n_features=2, fit_intercept=True)
    stats = model.fit(X, y, _default_hyperparams())

    assert stats.accuracy > 0.95, f"Expected >0.95, got {stats.accuracy}"
    assert stats.loss < 0.2, f"Expected <0.2, got {stats.loss}"
    assert stats.num_samples == 200


def test_fit_fedprox_mu_zero_equals_fedavg():
    # La inicialización y el barajado del DataLoader consumen el RNG global de
    # torch; hay que fijar ambos para comparar.
    X, y = _build_separable_data(n_per_class=50, seed=42)

    torch.manual_seed(0)
    model_avg = LogisticRegression(n_features=2, fit_intercept=True)
    torch.manual_seed(123)
    model_avg.fit(X, y, _default_hyperparams(strategy='fedavg'))
    w_avg = model_avg.get_flat_weights()

    torch.manual_seed(0)
    model_prox = LogisticRegression(n_features=2, fit_intercept=True)
    torch.manual_seed(123)
    model_prox.fit(X, y, _default_hyperparams(strategy='fedprox', mu=0.0))
    w_prox = model_prox.get_flat_weights()

    np.testing.assert_allclose(w_avg, w_prox, atol=1e-6,
                               err_msg="FedProx mu=0 must equal FedAvg")


def test_evaluate_returns_metrics():
    torch.manual_seed(0)
    X, y = _build_separable_data(n_per_class=50, seed=0)
    model = LogisticRegression(n_features=2, fit_intercept=True)
    stats = model.evaluate(X, y)
    assert 0.0 <= stats.accuracy <= 1.0
    assert stats.loss > 0.0


def test_flat_weights_round_trip():
    model = LogisticRegression(n_features=5, fit_intercept=True)
    w = np.arange(6, dtype=np.float32)  # 5 pesos + sesgo
    model.set_flat_weights(w)
    w_back = model.get_flat_weights()
    np.testing.assert_allclose(w, w_back, atol=1e-7)
