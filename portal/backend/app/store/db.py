"""SQLite engine para persistir el JobQueue del portal.

Sobrevive reinicios del container: si el portal cae mid-run, al levantar
recupera la lista de runs PENDING/RUNNING. Los que estaban RUNNING los
marca como FAILED con mensaje "portal restarted mid-run".
"""
from __future__ import annotations

from datetime import datetime, timezone
from pathlib import Path
from typing import Optional

from sqlmodel import Field, Session, SQLModel, create_engine

from app.config import get_settings
from app.schemas.run import RunState


class RunRecordDb(SQLModel, table=True):
    """Tabla de runs persistidos. Espejo flat de RunRecord (pydantic)."""

    __tablename__ = "runs"

    run_id: str = Field(primary_key=True)
    spec_label: str
    spec_path: str
    state: str
    queued_at: datetime
    started_at: datetime | None = None
    finished_at: datetime | None = None
    final_accuracy: float | None = None
    final_loss: float | None = None
    total_seconds: float | None = None
    error_message: str | None = None
    log_file: str | None = None


_engine = None


def get_engine():
    global _engine
    if _engine is None:
        s = get_settings()
        s.portal_data_dir.mkdir(parents=True, exist_ok=True)
        db_path = s.portal_data_dir / "runs.db"
        _engine = create_engine(f"sqlite:///{db_path}", echo=False)
        SQLModel.metadata.create_all(_engine)
        # Marca como FAILED los runs que quedaron RUNNING/STARTING_INFRA/PREPARING
        # al apagón previo. PENDING quedan tal cual (vuelven a la cola).
        _recover_stuck_runs(_engine)
    return _engine


def _recover_stuck_runs(engine) -> None:
    from sqlmodel import select

    stuck = (RunState.PREPARING.value, RunState.STARTING_INFRA.value, RunState.RUNNING.value)
    with Session(engine) as s:
        rows = s.exec(select(RunRecordDb).where(RunRecordDb.state.in_(stuck))).all()
        for r in rows:
            r.state = RunState.FAILED.value
            r.error_message = "portal restarted mid-run"
            r.finished_at = datetime.now(timezone.utc)
            s.add(r)
        s.commit()


def get_session() -> Session:
    return Session(get_engine())
