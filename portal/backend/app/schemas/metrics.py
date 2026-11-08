"""Schemas de métricas leídas desde results/<label>/*."""
from __future__ import annotations

from datetime import datetime

from pydantic import BaseModel, Field


class GlobalEvalRow(BaseModel):
    """Una fila del global_eval.csv (eval del modelo global por ronda)."""

    label: str | None = None
    round: int
    accuracy: float
    loss: float
    eval_time_ms: int = Field(alias="evalTimeMs", default=0)
    timestamp: datetime | None = None

    model_config = {"populate_by_name": True}


class ClientMetricRow(BaseModel):
    """Una fila del client-N.csv (métricas locales por ronda)."""

    round: int
    accuracy: float
    loss: float
    num_samples: int = 0
    elapsed_ms: int = 0


class RunSummary(BaseModel):
    """Espejo del run_summary.json producido por ReportGenerator."""

    experiment_label: str = Field(alias="experimentLabel")
    k: int
    total_rounds: int = Field(alias="totalRounds")
    strategy: str
    anonymity_mode: str = Field(alias="anonymityMode")
    seed: int
    started_at: datetime = Field(alias="startedAt")
    finished_at: datetime = Field(alias="finishedAt")
    total_seconds: float = Field(alias="totalSeconds")
    rounds_completed: int = Field(alias="roundsCompleted")
    final_accuracy: float | None = Field(default=None, alias="finalAccuracy")
    final_loss: float | None = Field(default=None, alias="finalLoss")
    clients_completed: int = Field(default=0, alias="clientsCompleted")
    clients_failed: int = Field(default=0, alias="clientsFailed")
    global_evals: list[dict] = Field(default_factory=list, alias="globalEvals")
    clients: list[dict] = Field(default_factory=list)

    model_config = {"populate_by_name": True}


class RunMetrics(BaseModel):
    """Bundle de métricas servido por GET /api/runs/{id}/metrics."""

    run_id: str
    summary: RunSummary | None = None
    global_evals: list[GlobalEvalRow] = Field(default_factory=list)
    per_client: dict[str, list[ClientMetricRow]] = Field(default_factory=dict)
