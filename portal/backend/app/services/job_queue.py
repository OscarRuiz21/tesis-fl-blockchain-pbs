"""JobQueue serializado del portal.

Garantiza que solo un run esté activo simultáneamente: un experimento bloquea
la red HLF compartida, y correr dos en paralelo corrompería el chaincode state.

Implementación: asyncio.Queue + worker task de fondo. El worker invoca al
runner.run_experiment(). El estado de cada run se persiste en SQLite vía
runs_repo para sobrevivir restart.
"""
from __future__ import annotations

import asyncio
import logging
import uuid
from datetime import datetime, timezone
from pathlib import Path

from app.schemas import RunRecord, RunState
from app.services import runner
from app.store import files_io, runs_repo

log = logging.getLogger(__name__)


class JobQueue:
    def __init__(self) -> None:
        self._q: asyncio.Queue[str] = asyncio.Queue()
        self._worker: asyncio.Task | None = None
        self._current: str | None = None
        self._current_exec_task: asyncio.Task | None = None

    def ensure_worker(self) -> None:
        if self._worker is None or self._worker.done():
            self._worker = asyncio.create_task(self._run_forever())

    async def enqueue(self, spec_label: str) -> RunRecord:
        spec_path = _resolve_spec_path(spec_label)
        if spec_path is None:
            raise FileNotFoundError(f"Spec not found: label='{spec_label}'")
        run_id = str(uuid.uuid4())
        record = RunRecord(
            run_id=run_id,
            spec_label=spec_label,
            spec_path=str(spec_path),
            state=RunState.PENDING,
            queued_at=datetime.now(timezone.utc),
        )
        runs_repo.insert(record)
        await self._q.put(run_id)
        self.ensure_worker()
        log.info("Enqueued run %s for spec %s", run_id, spec_label)
        return record

    def current_run_id(self) -> str | None:
        return self._current

    async def abort_current(self) -> bool:
        """Aborta el run en curso cancelando el task de ejecución (raises CancelledError)."""
        if self._current is None or self._current_exec_task is None:
            return False
        if self._current_exec_task.done():
            return False
        self._current_exec_task.cancel()
        log.info("Cancelled current exec task for run %s", self._current)
        return True

    async def _run_forever(self) -> None:
        log.info("JobQueue worker started")
        while True:
            run_id = await self._q.get()
            self._current = run_id
            record = runs_repo.get(run_id)
            if record is None:
                log.warning("Run %s not found in repo, skipping", run_id)
                self._current = None
                continue
            try:
                await self._execute(record)
            except Exception as e:
                log.exception("Run %s crashed in worker: %s", run_id, e)
                runs_repo.update_state(
                    run_id,
                    RunState.FAILED,
                    finished_at=datetime.now(timezone.utc),
                    error_message=str(e),
                )
            finally:
                self._current = None

    async def _execute(self, record: RunRecord) -> None:
        log.info("Executing run %s (spec=%s)", record.run_id, record.spec_label)
        # Envolvemos en un task cancelable para que abort_current() funcione.
        self._current_exec_task = asyncio.create_task(runner.run_experiment(record))
        try:
            await self._current_exec_task
        except asyncio.CancelledError:
            log.info("Run %s cancelled", record.run_id)
        finally:
            self._current_exec_task = None


def _resolve_spec_path(label: str):
    """Resuelve un spec_label a un path real. Acepta:
      - El label interno del experimento (declarado en `experiment.label` del YAML).
      - El nombre del archivo sin .yml (ej. 'smoke_K2_R1').
      - El path absoluto/relativo del archivo.

    Esto desacopla el nombre de archivo del label interno, evitando dual-truth.
    """
    from pathlib import Path

    # 1) Path directo (absoluto o relativo a cwd)
    p = Path(label)
    if p.suffix == ".yml" and p.exists():
        return p

    # 2) Path en experiments/ por filename
    p2 = files_io.spec_path_for(label)
    if p2.exists():
        return p2

    # 3) Buscar por experiment.label dentro de cada YAML guardado
    for entry in files_io.list_specs():
        if entry["label"] == label:
            return Path(entry["path"])

    return None


_singleton: JobQueue | None = None


def get_job_queue() -> JobQueue:
    global _singleton
    if _singleton is None:
        _singleton = JobQueue()
    return _singleton
