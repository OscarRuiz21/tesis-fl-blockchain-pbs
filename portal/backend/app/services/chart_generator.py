"""Generación de gráficas con matplotlib (PNG/PDF estáticos) y plotly (JSON interactivo).

Tipos de gráfica soportados (por nombre):
- accuracy_vs_round
- loss_vs_round
- per_client_accuracy
- timings_pbs
- compare_overlay  (cross-experiment)
"""
from __future__ import annotations

import io
import json
import logging
from typing import Iterable

import matplotlib

matplotlib.use("Agg")  # backend sin display, requerido en container
import matplotlib.pyplot as plt
import plotly.graph_objects as go

from app.schemas import GlobalEvalRow, RoundTimings, RunMetrics

log = logging.getLogger(__name__)


def _ensure_evals(evals: list[GlobalEvalRow]) -> tuple[list[int], list[float], list[float]]:
    rounds = [e.round for e in evals]
    acc = [e.accuracy for e in evals]
    loss = [e.loss for e in evals]
    return rounds, acc, loss


def accuracy_vs_round_png(metrics: RunMetrics) -> bytes:
    rounds, acc, _ = _ensure_evals(metrics.global_evals)
    fig, ax = plt.subplots(figsize=(7, 4.5), dpi=110)
    if rounds:
        ax.plot(rounds, acc, marker="o", linewidth=1.5)
    ax.set_xlabel("Ronda")
    ax.set_ylabel("Exactitud global")
    ax.set_ylim(0, 1)
    ax.grid(alpha=0.3)
    title = metrics.summary.experiment_label if metrics.summary else metrics.run_id
    ax.set_title(f"Exactitud por ronda: {title}")
    return _fig_to_png(fig)


def loss_vs_round_png(metrics: RunMetrics) -> bytes:
    rounds, _, loss = _ensure_evals(metrics.global_evals)
    fig, ax = plt.subplots(figsize=(7, 4.5), dpi=110)
    if rounds:
        ax.plot(rounds, loss, marker="s", linewidth=1.5, color="tab:red")
    ax.set_xlabel("Ronda")
    ax.set_ylabel("Pérdida global")
    ax.grid(alpha=0.3)
    title = metrics.summary.experiment_label if metrics.summary else metrics.run_id
    ax.set_title(f"Pérdida por ronda: {title}")
    return _fig_to_png(fig)


def per_client_accuracy_png(metrics: RunMetrics) -> bytes:
    fig, ax = plt.subplots(figsize=(7, 4.5), dpi=110)
    for cid, rows in sorted(metrics.per_client.items()):
        xs = [r.round for r in rows]
        ys = [r.accuracy for r in rows]
        if xs:
            ax.plot(xs, ys, marker=".", label=cid, alpha=0.85)
    ax.set_xlabel("Ronda")
    ax.set_ylabel("Exactitud local")
    ax.set_ylim(0, 1)
    ax.grid(alpha=0.3)
    ax.legend(loc="best", fontsize=8)
    title = metrics.summary.experiment_label if metrics.summary else metrics.run_id
    ax.set_title(f"Exactitud por cliente: {title}")
    return _fig_to_png(fig)


def compare_overlay_png(
    bundles: list[tuple[str, RunMetrics]],
    metric: str = "accuracy",
) -> bytes:
    """Overlay de la misma métrica para múltiples experimentos."""
    fig, ax = plt.subplots(figsize=(8, 5), dpi=110)
    for label, m in bundles:
        rounds, acc, loss = _ensure_evals(m.global_evals)
        ys = acc if metric == "accuracy" else loss
        if rounds:
            ax.plot(rounds, ys, marker="o", label=label, linewidth=1.5)
    ax.set_xlabel("Ronda")
    metric_es = "Exactitud" if metric == "accuracy" else "Pérdida"
    ax.set_ylabel(metric_es)
    if metric == "accuracy":
        ax.set_ylim(0, 1)
    ax.grid(alpha=0.3)
    ax.legend(loc="best", fontsize=9)
    ax.set_title(f"Comparación entre experimentos: {metric_es.lower()}")
    return _fig_to_png(fig)


def accuracy_plotly_json(metrics: RunMetrics) -> dict:
    """Versión interactiva (Plotly figure JSON) para renderizar en el frontend."""
    rounds, acc, _ = _ensure_evals(metrics.global_evals)
    fig = go.Figure()
    fig.add_trace(go.Scatter(x=rounds, y=acc, mode="lines+markers", name="accuracy"))
    fig.update_layout(
        xaxis_title="Ronda",
        yaxis_title="Accuracy",
        yaxis=dict(range=[0, 1]),
        title=metrics.summary.experiment_label if metrics.summary else metrics.run_id,
        template="plotly_white",
    )
    return json.loads(fig.to_json())


def timings_stacked_png(per_round: list[RoundTimings], label: str = "") -> bytes:
    """Área apilada por ronda: entrenamiento local y Etapas 1, 2 y 3 del protocolo."""
    rounds = [r.round for r in per_round]
    train = [r.train_ms_avg / 1000.0 for r in per_round]
    pbs = [r.pbs_ms_avg / 1000.0 for r in per_round]
    submit = [r.submit_ms_avg / 1000.0 for r in per_round]
    phase3 = [r.phase3_ms_avg / 1000.0 for r in per_round]

    fig, ax = plt.subplots(figsize=(8, 5), dpi=110)
    if rounds:
        ax.stackplot(
            rounds,
            train, pbs, submit, phase3,
            labels=["Entrenamiento local", "Etapa 1: PBS", "Etapa 2: envío", "Etapa 3: agregación"],
            colors=["#94a3b8", "#10b981", "#3b82f6", "#ef4444"],
            alpha=0.85,
        )
    ax.set_xlabel("Ronda")
    ax.set_ylabel("Tiempo promedio entre clientes (s)")
    ax.set_title(f"Desglose de tiempos por ronda: {label}" if label else "Tiempos por ronda")
    ax.legend(loc="upper left", fontsize=9)
    ax.grid(alpha=0.3)
    if rounds:
        ax.set_xticks(rounds)
    return _fig_to_png(fig)


def convergence_delta_png(deltas, label: str = "") -> bytes:
    """Bar chart de Δaccuracy por ronda: detecta plateau."""
    rounds = [r.round for r in deltas]
    delta_acc = [r.delta_accuracy for r in deltas]

    fig, ax = plt.subplots(figsize=(7, 4), dpi=110)
    colors = ["#16a34a" if d > 0 else "#dc2626" for d in delta_acc]
    if rounds:
        ax.bar(rounds, delta_acc, color=colors, alpha=0.85)
    ax.axhline(0, color="#94a3b8", linewidth=0.8)
    ax.set_xlabel("Ronda")
    ax.set_ylabel("Δ exactitud")
    ax.set_title(f"Convergencia (Δacc por ronda): {label}" if label else "Δaccuracy")
    ax.grid(alpha=0.3, axis="y")
    if rounds:
        ax.set_xticks(rounds)
    return _fig_to_png(fig)


def _fig_to_png(fig) -> bytes:
    buf = io.BytesIO()
    fig.savefig(buf, format="png", bbox_inches="tight")
    plt.close(fig)
    return buf.getvalue()
