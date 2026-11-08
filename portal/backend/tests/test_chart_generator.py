"""Tests del chart_generator: smoke tests sobre PNG outputs."""
from __future__ import annotations

from datetime import datetime

from app.schemas import (
    ClientMetricRow,
    GlobalEvalRow,
    RunMetrics,
    RunSummary,
)
from app.services import chart_generator


def _bundle() -> RunMetrics:
    summary = RunSummary(
        experimentLabel="test-label",
        k=2,
        totalRounds=3,
        strategy="fedavg",
        anonymityMode="pbs",
        seed=42,
        startedAt=datetime(2026, 5, 17, 10, 0),
        finishedAt=datetime(2026, 5, 17, 10, 5),
        totalSeconds=300.0,
        roundsCompleted=3,
        finalAccuracy=0.85,
        finalLoss=0.42,
        clientsCompleted=2,
        clientsFailed=0,
    )
    evals = [
        GlobalEvalRow(round=1, accuracy=0.65, loss=0.55),
        GlobalEvalRow(round=2, accuracy=0.78, loss=0.45),
        GlobalEvalRow(round=3, accuracy=0.85, loss=0.42),
    ]
    per_client = {
        "client-1": [
            ClientMetricRow(round=1, accuracy=0.60, loss=0.60),
            ClientMetricRow(round=2, accuracy=0.75, loss=0.50),
        ],
        "client-2": [
            ClientMetricRow(round=1, accuracy=0.62, loss=0.58),
            ClientMetricRow(round=2, accuracy=0.77, loss=0.48),
        ],
    }
    return RunMetrics(
        run_id="rid-1",
        summary=summary,
        global_evals=evals,
        per_client=per_client,
    )


def test_accuracy_vs_round_png_is_nonempty_png():
    png = chart_generator.accuracy_vs_round_png(_bundle())
    assert png[:8] == b"\x89PNG\r\n\x1a\n"
    assert len(png) > 1000


def test_loss_vs_round_png_is_nonempty_png():
    png = chart_generator.loss_vs_round_png(_bundle())
    assert png[:8] == b"\x89PNG\r\n\x1a\n"
    assert len(png) > 1000


def test_per_client_accuracy_png_is_nonempty():
    png = chart_generator.per_client_accuracy_png(_bundle())
    assert png[:8] == b"\x89PNG\r\n\x1a\n"


def test_compare_overlay_png_handles_multiple_runs():
    a, b = _bundle(), _bundle()
    png = chart_generator.compare_overlay_png([("run-A", a), ("run-B", b)])
    assert png[:8] == b"\x89PNG\r\n\x1a\n"


def test_accuracy_plotly_json_has_trace():
    j = chart_generator.accuracy_plotly_json(_bundle())
    assert "data" in j
    assert len(j["data"]) >= 1
    assert j["data"][0]["mode"] == "lines+markers"


def test_charts_handle_empty_evals():
    empty = RunMetrics(run_id="r", global_evals=[], per_client={})
    png = chart_generator.accuracy_vs_round_png(empty)
    assert png[:8] == b"\x89PNG\r\n\x1a\n"
