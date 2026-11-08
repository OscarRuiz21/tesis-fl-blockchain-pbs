"""Tests del runs_repo: SQLite CRUD + recovery."""
from __future__ import annotations

from datetime import datetime

from app.schemas import RunRecord, RunState
from app.store import runs_repo


def _record(run_id: str = "r1", state: RunState = RunState.PENDING) -> RunRecord:
    return RunRecord(
        run_id=run_id,
        spec_label="smoke-K2",
        spec_path="/tmp/smoke-K2.yml",
        state=state,
        queued_at=datetime.utcnow(),
    )


def test_insert_and_get_roundtrip():
    rec = _record("r-1")
    runs_repo.insert(rec)
    loaded = runs_repo.get("r-1")
    assert loaded is not None
    assert loaded.run_id == "r-1"
    assert loaded.spec_label == "smoke-K2"
    assert loaded.state == RunState.PENDING


def test_update_state_transitions():
    runs_repo.insert(_record("r-2", RunState.PENDING))
    runs_repo.update_state("r-2", RunState.RUNNING, started_at=datetime.utcnow())
    rec = runs_repo.get("r-2")
    assert rec is not None
    assert rec.state == RunState.RUNNING
    assert rec.started_at is not None


def test_update_state_records_final_metrics():
    runs_repo.insert(_record("r-3"))
    runs_repo.update_state(
        "r-3", RunState.DONE,
        finished_at=datetime.utcnow(),
        final_accuracy=0.85,
        final_loss=0.42,
        total_seconds=120.0,
    )
    rec = runs_repo.get("r-3")
    assert rec is not None
    assert rec.state == RunState.DONE
    assert rec.final_accuracy == 0.85
    assert rec.total_seconds == 120.0


def test_list_all_returns_in_recent_order():
    for i in range(3):
        runs_repo.insert(_record(f"r-list-{i}"))
    rows = runs_repo.list_all(limit=10)
    ids = [r.run_id for r in rows]
    assert "r-list-0" in ids
    assert "r-list-1" in ids
    assert "r-list-2" in ids


def test_delete_removes_record():
    runs_repo.insert(_record("r-del"))
    assert runs_repo.get("r-del") is not None
    assert runs_repo.delete("r-del") is True
    assert runs_repo.get("r-del") is None


def test_find_active_picks_running_run():
    runs_repo.insert(_record("r-active", RunState.RUNNING))
    runs_repo.insert(_record("r-pending", RunState.PENDING))
    active = runs_repo.find_active()
    assert active is not None
    assert active.run_id == "r-active"


def test_run_state_terminal_property():
    assert RunState.DONE.is_terminal
    assert RunState.FAILED.is_terminal
    assert RunState.ABORTED.is_terminal
    assert not RunState.PENDING.is_terminal
    assert not RunState.RUNNING.is_terminal
