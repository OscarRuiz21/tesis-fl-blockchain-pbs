"""Smoke tests de la API HTTP: TestClient (no requiere coordinator real)."""
from __future__ import annotations

import pytest
from fastapi.testclient import TestClient

from app.main import create_app


@pytest.fixture
def client(monkeypatch) -> TestClient:
    # Aseguramos coordinator_url apuntando a un host inalcanzable: el catalog
    # cae al fallback hardcoded sin necesidad de mocks.
    monkeypatch.setenv("PORTAL_COORDINATOR_URL", "http://invalid.local:65535")
    app = create_app()
    return TestClient(app)


def test_health(client: TestClient):
    r = client.get("/api/health")
    assert r.status_code == 200
    assert r.json() == {"status": "ok"}


def test_catalog_returns_fallback_when_coordinator_unreachable(client: TestClient):
    r = client.get("/api/catalog")
    assert r.status_code == 200
    body = r.json()
    assert "logistic_regression" in body["models"]
    assert "fedavg" in body["aggregations"]
    assert "pbs" in body["anonymityModes"]


def test_list_experiments_empty(client: TestClient):
    r = client.get("/api/experiments")
    assert r.status_code == 200
    assert r.json() == []


def test_create_and_get_experiment(client: TestClient):
    body = {
        "experiment": {"label": "smoke-K2", "description": "", "seed": 42},
        "model": {"name": "logistic_regression", "num_params": 26, "hyperparams": {"epochs": 5}},
        "dataset": {
            "type": "tabular_csv", "base_path": "/d",
            "target_column": "default", "test_set_path": "/d/test.csv",
        },
        "partition": {"strategy": "iid"},
        "federation": {
            "num_clients": 2, "num_rounds": 3,
            "aggregation": "fedavg", "anonymity": "pbs",
        },
        "timeouts": {"t_train_seconds": 60, "t_round_seconds": 180, "t_experiment_seconds": 3600},
        "output": {
            "results_dir": "/results/smoke-K2",
            "per_client_csv": True, "global_eval_csv": True, "run_summary_json": True,
        },
    }
    r = client.post("/api/experiments", json=body)
    assert r.status_code == 201

    r2 = client.get("/api/experiments/smoke-K2")
    assert r2.status_code == 200
    assert r2.json()["experiment"]["label"] == "smoke-K2"

    r3 = client.get("/api/experiments")
    assert r3.status_code == 200
    assert len(r3.json()) == 1


def test_get_missing_experiment_returns_404(client: TestClient):
    r = client.get("/api/experiments/nonexistent")
    assert r.status_code == 404


def test_compare_presets_endpoint(client: TestClient):
    r = client.get("/api/compare/presets")
    assert r.status_code == 200
    body = r.json()
    keys = {p["key"] for p in body}
    assert {"pbs_overhead", "aggregation_comparison", "scale_comparison"} <= keys


def test_list_runs_empty(client: TestClient):
    r = client.get("/api/runs")
    assert r.status_code == 200
    assert r.json() == []


def test_active_run_returns_null_when_none(client: TestClient):
    r = client.get("/api/runs/active")
    assert r.status_code == 200
    assert r.json() is None
