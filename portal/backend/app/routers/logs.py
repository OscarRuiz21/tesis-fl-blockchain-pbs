"""Streaming SSE de logs por run + snapshot HTTP."""
from __future__ import annotations

from pathlib import Path
from fastapi import APIRouter, HTTPException
from sse_starlette.sse import EventSourceResponse

from app.services import log_streamer
from app.store import runs_repo

router = APIRouter()


@router.get("/runs/{run_id}/logs/snapshot")
async def log_snapshot(run_id: str, max_kb: int = 256) -> dict:
    rec = runs_repo.get(run_id)
    if rec is None:
        raise HTTPException(status_code=404)
    if rec.log_file is None:
        return {"log": "", "state": rec.state.value}
    body = await log_streamer.read_log_snapshot(Path(rec.log_file), max_bytes=max_kb * 1024)
    return {"log": body, "state": rec.state.value}


@router.get("/runs/{run_id}/logs/stream")
async def log_stream(run_id: str):
    rec = runs_repo.get(run_id)
    if rec is None:
        raise HTTPException(status_code=404)
    if rec.log_file is None:
        raise HTTPException(status_code=409, detail="Log file not yet allocated")

    log_path = Path(rec.log_file)

    async def event_iter():
        # 1) Dump completo de lo que ya hay en el log file (historia + activo).
        if log_path.exists():
            try:
                with log_path.open("r", encoding="utf-8", errors="replace") as f:
                    for raw in f:
                        yield {"event": "log", "data": raw.rstrip("\n")}
            except OSError as e:
                yield {"event": "log", "data": f"# (error reading log file: {e})"}

        # 2) Si el run ya terminó, cerramos aquí.
        current = runs_repo.get(run_id)
        if current is None or current.state.is_terminal:
            yield {"event": "done", "data": current.state.value if current else "unknown"}
            return

        # 3) Run activo: seguir el archivo hasta que el run llegue a un estado terminal.
        async for line in log_streamer.tail_log(log_path):
            yield {"event": "log", "data": line.rstrip("\n")}
            current = runs_repo.get(run_id)
            if current is None or current.state.is_terminal:
                yield {"event": "done", "data": current.state.value if current else "unknown"}
                break

    return EventSourceResponse(event_iter())
