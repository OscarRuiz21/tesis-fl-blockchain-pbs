"""Tests del results_reader: lee summary, global_eval CSV, per-client CSVs."""
from __future__ import annotations

import json

from app.config import get_settings
from app.services import results_reader


def _setup_label(label: str, *, with_summary=True, with_global=True, n_clients=2):
    base = get_settings().results_dir / label
    base.mkdir(parents=True, exist_ok=True)
    if with_summary:
        (base / "run_summary.json").write_text(json.dumps({
            "experimentLabel": label,
            "k": n_clients,
            "totalRounds": 3,
            "strategy": "fedavg",
            "anonymityMode": "pbs",
            "seed": 42,
            "startedAt": "2026-05-17T10:00:00Z",
            "finishedAt": "2026-05-17T10:05:00Z",
            "totalSeconds": 300.0,
            "roundsCompleted": 3,
            "finalAccuracy": 0.85,
            "finalLoss": 0.42,
            "clientsCompleted": n_clients,
            "clientsFailed": 0,
            "globalEvals": [],
            "clients": [],
        }))
    if with_global:
        (base / "global_eval.csv").write_text(
            "label,round,accuracy,loss,evalTimeMs,timestamp\n"
            f"{label},1,0.65,0.55,12,2026-05-17T10:01:00Z\n"
            f"{label},2,0.78,0.45,11,2026-05-17T10:02:00Z\n"
            f"{label},3,0.85,0.42,13,2026-05-17T10:03:00Z\n"
        )
    for i in range(1, n_clients + 1):
        (base / f"client-{i}.csv").write_text(
            "round,accuracy,loss,num_samples,elapsed_ms\n"
            f"1,0.60,0.60,500,1200\n"
            f"2,0.75,0.50,500,1100\n"
            f"3,0.82,0.45,500,1050\n"
        )
    return base


def test_read_summary_parses_camelCase():
    _setup_label("smoke")
    s = results_reader.read_summary("smoke")
    assert s is not None
    assert s.experiment_label == "smoke"
    assert s.k == 2
    assert s.final_accuracy == 0.85
    assert s.rounds_completed == 3


def test_read_summary_returns_none_when_missing():
    assert results_reader.read_summary("nonexistent") is None


def test_read_global_evals():
    _setup_label("evals")
    rows = results_reader.read_global_evals("evals")
    assert len(rows) == 3
    assert rows[0].round == 1
    assert rows[2].accuracy == 0.85


def test_read_global_evals_empty_when_no_csv():
    assert results_reader.read_global_evals("missing") == []


def test_read_client_metrics_returns_dict_by_client():
    _setup_label("clients", n_clients=3)
    per = results_reader.read_client_metrics("clients")
    assert set(per.keys()) == {"client-1", "client-2", "client-3"}
    assert len(per["client-1"]) == 3
    assert per["client-2"][2].accuracy == 0.82


def test_build_metrics_bundle_includes_all():
    _setup_label("bundle", n_clients=2)
    m = results_reader.build_metrics_bundle("bundle", "run-id-1")
    assert m.run_id == "run-id-1"
    assert m.summary is not None
    assert len(m.global_evals) == 3
    assert len(m.per_client) == 2
