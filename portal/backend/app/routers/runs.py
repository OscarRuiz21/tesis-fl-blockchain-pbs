"""Lifecycle de runs: POST encola, GET lista, DELETE elimina, abort cancela."""
from __future__ import annotations

from pydantic import BaseModel
from fastapi import APIRouter, HTTPException

from app.schemas import RunRecord
from app.services.job_queue import get_job_queue
from app.store import runs_repo

router = APIRouter()


class EnqueueRequest(BaseModel):
    spec_label: str


@router.get("/runs", response_model=list[RunRecord])
def list_runs(limit: int = 200) -> list[RunRecord]:
    return runs_repo.list_all(limit=limit)


@router.get("/runs/active", response_model=RunRecord | None)
def get_active_run() -> RunRecord | None:
    return runs_repo.find_active()


@router.get("/runs/{run_id}", response_model=RunRecord)
def get_run(run_id: str) -> RunRecord:
    rec = runs_repo.get(run_id)
    if rec is None:
        raise HTTPException(status_code=404, detail=f"Run {run_id} not found")
    return rec


@router.post("/runs", response_model=RunRecord, status_code=201)
async def enqueue_run(req: EnqueueRequest) -> RunRecord:
    queue = get_job_queue()
    try:
        return await queue.enqueue(req.spec_label)
    except FileNotFoundError as e:
        raise HTTPException(status_code=404, detail=str(e))


@router.post("/runs/{run_id}/abort", response_model=dict)
async def abort_run(run_id: str) -> dict:
    queue = get_job_queue()
    rec = runs_repo.get(run_id)
    if rec is None:
        raise HTTPException(status_code=404, detail=f"Run {run_id} not found")
    if queue.current_run_id() != run_id:
        raise HTTPException(
            status_code=409,
            detail="This run is not the current active one; only the active run can be aborted.",
        )
    aborted = await queue.abort_current()
    return {"aborted": aborted, "run_id": run_id}


@router.delete("/runs/{run_id}", status_code=204)
def delete_run(run_id: str) -> None:
    rec = runs_repo.get(run_id)
    if rec is None:
        raise HTTPException(status_code=404)
    if not rec.state.is_terminal:
        raise HTTPException(
            status_code=409,
            detail=f"Cannot delete non-terminal run (state={rec.state.value})",
        )
    runs_repo.delete(run_id)
