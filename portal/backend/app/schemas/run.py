"""Estado de una ejecución (run), gestionada por el JobQueue del portal."""
from __future__ import annotations

from datetime import datetime
from enum import Enum

from pydantic import BaseModel, Field


class RunState(str, Enum):
    """Estados de un run en el JobQueue.

    Transiciones:
      PENDING -> PREPARING -> STARTING_INFRA -> RUNNING -> DONE
    Cualquier estado activo puede terminar en FAILED, o en ABORTED si el
    operador cancela el run.
    """

    PENDING = "pending"
    PREPARING = "preparing"  # subprocess prepare-data.sh
    STARTING_INFRA = "starting_infra"  # docker-compose up + network-init
    RUNNING = "running"  # coordinator POST /experiments/run en curso
    DONE = "done"
    FAILED = "failed"
    ABORTED = "aborted"

    @property
    def is_terminal(self) -> bool:
        return self in {RunState.DONE, RunState.FAILED, RunState.ABORTED}


class RunRecord(BaseModel):
    """Registro persistido de un run en SQLite (sobrevive restart del portal)."""

    run_id: str
    spec_label: str
    spec_path: str
    state: RunState = RunState.PENDING
    queued_at: datetime
    started_at: datetime | None = None
    finished_at: datetime | None = None
    final_accuracy: float | None = None
    final_loss: float | None = None
    total_seconds: float | None = None
    error_message: str | None = None
    log_file: str | None = Field(
        default=None,
        description="Path al log file persistido para servir vía SSE",
    )
