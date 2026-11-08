"""Tests del preflight: solo lógica que no requiere Docker daemon real."""
from __future__ import annotations

from pathlib import Path

import pytest
import yaml

from app.services import preflight


def _write_spec(tmp_path: Path, k: int) -> Path:
    spec = {
        "experiment": {"label": "test", "description": "", "seed": 42},
        "model": {"name": "logistic_regression", "num_params": 26, "hyperparams": {}},
        "dataset": {"type": "tabular_csv", "base_path": "/d",
                    "target_column": "x", "test_set_path": "/d/t.csv"},
        "partition": {"strategy": "iid"},
        "federation": {"num_clients": k, "num_rounds": 1,
                       "aggregation": "fedavg", "anonymity": "pbs"},
        "timeouts": {"t_train_seconds": 10, "t_round_seconds": 30, "t_experiment_seconds": 300},
        "output": {"results_dir": "/r"},
    }
    p = tmp_path / "spec.yml"
    p.write_text(yaml.safe_dump(spec))
    return p


async def test_returns_false_when_spec_missing_K(tmp_path):
    spec = {"federation": {}}
    p = tmp_path / "bad.yml"
    p.write_text(yaml.safe_dump(spec))
    captured = []

    ok, msg = await preflight.ensure_ready_for_run(p, captured.append)
    assert ok is False
    assert "num_clients" in msg.lower()


async def test_returns_false_when_spec_unparseable(tmp_path):
    p = tmp_path / "bad.yml"
    p.write_text("not: valid: yaml: at: all: [")
    captured = []

    ok, msg = await preflight.ensure_ready_for_run(p, captured.append)
    assert ok is False
    assert "yaml" in msg.lower()


async def test_returns_false_when_hlf_missing(tmp_path, monkeypatch):
    """Si los containers HLF no aparecen en `docker ps`, falla con mensaje claro."""
    p = _write_spec(tmp_path, k=2)

    async def fake_missing(names):
        return ["orderer0.fl.network", "peer0.org1.fl.network"]

    monkeypatch.setattr(preflight, "_missing_containers", fake_missing)
    captured = []
    ok, msg = await preflight.ensure_ready_for_run(p, captured.append)
    assert ok is False
    assert "HLF" in msg
    assert "orderer0.fl.network" in msg
    assert "start-network.sh" in msg


def test_hlf_required_list_contains_expected_services():
    assert "orderer0.fl.network" in preflight.HLF_REQUIRED
    assert "peer0.org1.fl.network" in preflight.HLF_REQUIRED
    assert "peer0.org2.fl.network" in preflight.HLF_REQUIRED
    assert "chaincode.flprotocol.fl.network" in preflight.HLF_REQUIRED
    assert len(preflight.HLF_REQUIRED) == 6
