"""Repositorio CRUD de runs (SQLite vía sqlmodel)."""
from __future__ import annotations

from datetime import datetime
from typing import Optional

from sqlmodel import select

from app.schemas.run import RunRecord, RunState
from app.store.db import RunRecordDb, get_session


def _to_pydantic(row: RunRecordDb) -> RunRecord:
    return RunRecord(
        run_id=row.run_id,
        spec_label=row.spec_label,
        spec_path=row.spec_path,
        state=RunState(row.state),
        queued_at=row.queued_at,
        started_at=row.started_at,
        finished_at=row.finished_at,
        final_accuracy=row.final_accuracy,
        final_loss=row.final_loss,
        total_seconds=row.total_seconds,
        error_message=row.error_message,
        log_file=row.log_file,
    )


def insert(record: RunRecord) -> RunRecord:
    with get_session() as s:
        row = RunRecordDb(
            run_id=record.run_id,
            spec_label=record.spec_label,
            spec_path=record.spec_path,
            state=record.state.value,
            queued_at=record.queued_at,
            started_at=record.started_at,
            finished_at=record.finished_at,
            final_accuracy=record.final_accuracy,
            final_loss=record.final_loss,
            total_seconds=record.total_seconds,
            error_message=record.error_message,
            log_file=record.log_file,
        )
        s.add(row)
        s.commit()
        s.refresh(row)
        return _to_pydantic(row)


def update_state(
    run_id: str,
    state: RunState,
    *,
    started_at: datetime | None = None,
    finished_at: datetime | None = None,
    final_accuracy: float | None = None,
    final_loss: float | None = None,
    total_seconds: float | None = None,
    error_message: str | None = None,
    log_file: str | None = None,
) -> RunRecord | None:
    with get_session() as s:
        row = s.get(RunRecordDb, run_id)
        if row is None:
            return None
        row.state = state.value
        if started_at is not None:
            row.started_at = started_at
        if finished_at is not None:
            row.finished_at = finished_at
        if final_accuracy is not None:
            row.final_accuracy = final_accuracy
        if final_loss is not None:
            row.final_loss = final_loss
        if total_seconds is not None:
            row.total_seconds = total_seconds
        if error_message is not None:
            row.error_message = error_message
        if log_file is not None:
            row.log_file = log_file
        s.add(row)
        s.commit()
        s.refresh(row)
        return _to_pydantic(row)


def get(run_id: str) -> RunRecord | None:
    with get_session() as s:
        row = s.get(RunRecordDb, run_id)
        return _to_pydantic(row) if row else None


def list_all(limit: int = 200) -> list[RunRecord]:
    with get_session() as s:
        rows = s.exec(
            select(RunRecordDb).order_by(RunRecordDb.queued_at.desc()).limit(limit)
        ).all()
        return [_to_pydantic(r) for r in rows]


def delete(run_id: str) -> bool:
    with get_session() as s:
        row = s.get(RunRecordDb, run_id)
        if row is None:
            return False
        s.delete(row)
        s.commit()
        return True


def find_active() -> RunRecord | None:
    """Retorna el run actualmente RUNNING/PREPARING/STARTING_INFRA, si existe.

    Hay como máximo uno por la queue serializada.
    """
    active = (
        RunState.PREPARING.value,
        RunState.STARTING_INFRA.value,
        RunState.RUNNING.value,
    )
    with get_session() as s:
        row = s.exec(
            select(RunRecordDb)
            .where(RunRecordDb.state.in_(active))
            .order_by(RunRecordDb.queued_at.asc())
        ).first()
        return _to_pydantic(row) if row else None
