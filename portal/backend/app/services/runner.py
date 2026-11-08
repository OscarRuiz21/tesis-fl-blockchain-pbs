"""Runner: POST al coordinator (`/experiments/run`) y consolida métricas.

El portal no ejecuta `run-experiment.sh`: asume que la red HLF ya está arriba
(`start-network.sh`) y trabaja sobre los contenedores existentes.

1. PREPARING: valida el spec con el coordinator.
2. STARTING_INFRA: preflight (particiones, AS recreado, K clientes en IDLE).
3. RUNNING: POST al coordinator con el body YAML del spec.
4. DONE / FAILED según la respuesta y run_summary.json.

Cada fase escribe líneas al log persistente (`portal-data/logs/<run_id>.log`).
"""
from __future__ import annotations

import asyncio
import logging
import os
import signal
from datetime import datetime, timezone
from pathlib import Path

import httpx
import yaml

from app.config import get_settings
from app.gateway.coordinator_client import get_coordinator_client
from app.schemas import RunRecord, RunState
from app.services import preflight, results_reader
from app.store import runs_repo

log = logging.getLogger(__name__)

_current_task: asyncio.Task | None = None
_current_proc: asyncio.subprocess.Process | None = None


def _log_path(run_id: str) -> Path:
    base = get_settings().portal_data_dir / "logs"
    base.mkdir(parents=True, exist_ok=True)
    return base / f"{run_id}.log"


def abort_current_subprocess() -> bool:
    """Cancela el task en curso (POST sync al coordinator)."""
    global _current_task
    if _current_task is None or _current_task.done():
        return False
    _current_task.cancel()
    log.info("Cancelled current run task")
    return True


def _tee(lf, msg: str) -> None:
    line = f"{datetime.now(timezone.utc).isoformat(timespec='seconds')}  {msg}\n"
    lf.write(line)
    lf.flush()


async def run_experiment(record: RunRecord) -> None:
    """Orquesta una run: valida, preflight (auto-ajuste de infra), POST al coordinator y procesa."""
    started = datetime.now(timezone.utc)
    log_file = _log_path(record.run_id)
    runs_repo.update_state(
        record.run_id,
        RunState.PREPARING,
        started_at=started,
        log_file=str(log_file),
    )

    spec_path = Path(record.spec_path)
    if not spec_path.exists():
        runs_repo.update_state(
            record.run_id, RunState.FAILED,
            finished_at=datetime.now(timezone.utc),
            error_message=f"Spec file not found: {spec_path}",
        )
        return

    yaml_body = spec_path.read_text(encoding="utf-8")
    coordinator = get_coordinator_client()
    s = get_settings()

    with log_file.open("w", encoding="utf-8") as lf:
        _tee(lf, f"# run_id={record.run_id}")
        _tee(lf, f"# spec={record.spec_label}")
        _tee(lf, f"# spec_path={spec_path}")
        _tee(lf, f"# coordinator={s.coordinator_url}")
        _tee(lf, f"# {len(yaml_body)} bytes YAML")
        _tee(lf, "")
        _tee(lf, "[1/4] Validando spec con coordinator…")

        try:
            v = await coordinator.validate_spec(yaml_body)
        except Exception as e:
            _tee(lf, f"ERROR validando: {e}")
            runs_repo.update_state(
                record.run_id, RunState.FAILED,
                finished_at=datetime.now(timezone.utc),
                error_message=f"validate_spec failed: {e}",
            )
            return
        if not v.valid:
            for err in v.errors:
                _tee(lf, f"  ✗ {err.field}: {err.message}")
            runs_repo.update_state(
                record.run_id, RunState.FAILED,
                finished_at=datetime.now(timezone.utc),
                error_message=f"Spec inválida: {len(v.errors)} error(s)",
            )
            return
        _tee(lf, "  ✓ spec válida")

        # Resumen humano del spec
        try:
            raw = yaml.safe_load(yaml_body) or {}
            fed = raw.get("federation", {})
            mdl = raw.get("model", {})
            _tee(lf, f"  K={fed.get('num_clients')} R={fed.get('num_rounds')} "
                     f"agg={fed.get('aggregation')} anon={fed.get('anonymity')} "
                     f"model={mdl.get('name')} num_params={mdl.get('num_params')}")
        except Exception:
            pass

        # ---- Preflight: auto-ajuste de infra ----
        _tee(lf, "")
        _tee(lf, "[2/4] Preflight: auto-ajuste de infra (HLF, AS, K clientes)…")
        runs_repo.update_state(record.run_id, RunState.STARTING_INFRA)

        def _log(line: str) -> None:
            _tee(lf, line)

        try:
            ok, msg = await preflight.ensure_ready_for_run(spec_path, _log)
        except Exception as e:
            _tee(lf, f"ERROR en preflight: {e}")
            runs_repo.update_state(
                record.run_id, RunState.FAILED,
                finished_at=datetime.now(timezone.utc),
                error_message=f"preflight crashed: {e}",
            )
            return
        if not ok:
            _tee(lf, f"  ✗ {msg}")
            runs_repo.update_state(
                record.run_id, RunState.FAILED,
                finished_at=datetime.now(timezone.utc),
                error_message=f"preflight failed: {msg}",
            )
            return
        _tee(lf, f"  ✓ {msg}")

        _tee(lf, "")
        _tee(lf, "[3/4] Lanzando POST /experiments/run al coordinator…")
        runs_repo.update_state(record.run_id, RunState.RUNNING)

        try:
            resp = await coordinator.run_experiment(yaml_body)
        except httpx.HTTPStatusError as e:
            body = e.response.text[:500] if e.response is not None else ""
            _tee(lf, f"ERROR HTTP {e.response.status_code if e.response else '?'}: {body}")
            runs_repo.update_state(
                record.run_id, RunState.FAILED,
                finished_at=datetime.now(timezone.utc),
                error_message=f"coordinator returned {e.response.status_code if e.response else '?'}: {body[:200]}",
            )
            return
        except asyncio.CancelledError:
            _tee(lf, "ABORT: cancelled by operator")
            runs_repo.update_state(
                record.run_id, RunState.ABORTED,
                finished_at=datetime.now(timezone.utc),
            )
            raise
        except Exception as e:
            _tee(lf, f"ERROR llamando coordinator: {e}")
            runs_repo.update_state(
                record.run_id, RunState.FAILED,
                finished_at=datetime.now(timezone.utc),
                error_message=str(e),
            )
            return

        _tee(lf, f"  ✓ Coordinator respondió OK")
        _tee(lf, f"  label={resp.get('label')} K={resp.get('k')} R={resp.get('totalRounds')}")
        _tee(lf, f"  finalAccuracy={resp.get('finalAccuracy')} finalLoss={resp.get('finalLoss')}")
        _tee(lf, f"  totalSeconds={resp.get('totalSeconds')}")

        _tee(lf, "")
        _tee(lf, "[4/4] Consolidando run_summary.json…")
        summary = results_reader.read_summary(record.spec_label)
        if summary is not None:
            _tee(lf, f"  ✓ Encontrado en results/{record.spec_label}/run_summary.json")
            final_acc = summary.final_accuracy
            final_loss = summary.final_loss
        else:
            _tee(lf, f"  ! No se pudo cargar run_summary.json (usando datos del response)")
            final_acc = resp.get("finalAccuracy")
            final_loss = resp.get("finalLoss")

        finished = datetime.now(timezone.utc)
        total = (finished - started).total_seconds()
        runs_repo.update_state(
            record.run_id,
            RunState.DONE,
            finished_at=finished,
            total_seconds=total,
            final_accuracy=final_acc,
            final_loss=final_loss,
        )
        _tee(lf, "")
        _tee(lf, f"DONE en {total:.1f}s")
        log.info("Run %s DONE in %.1fs", record.run_id, total)


async def prepare_data(dataset: str, k: int, strategy: str = "iid",
                       alpha: float | None = None) -> tuple[int, str]:
    """Ejecuta prepare-data.sh para un dataset y K. Devuelve (rc, log).

    Requiere yq y el CLI de docker dentro del container. La preparación normal
    la hace el preflight (data_prep.ensure_partitions).
    """
    s = get_settings()
    script = s.project_root / s.prepare_data_script
    if not script.exists():
        return 1, f"Script no encontrado: {script}"
    env = {**os.environ, "DATASET": dataset, "K": str(k), "PARTITION": strategy}
    if alpha is not None:
        env["DIRICHLET_ALPHA"] = str(alpha)
    try:
        proc = await asyncio.create_subprocess_exec(
            "bash", str(script),
            stdout=asyncio.subprocess.PIPE,
            stderr=asyncio.subprocess.STDOUT,
            cwd=str(s.project_root),
            env=env,
        )
        out_b, _ = await proc.communicate()
        return proc.returncode or 0, out_b.decode("utf-8", errors="replace")
    except FileNotFoundError as e:
        return 1, f"Cannot spawn bash (requires bash + yq + docker CLI in container): {e}"


async def prepare_data(dataset: str, k: int, strategy: str = "iid",
                       alpha: float | None = None) -> tuple[int, str]:
    """Ejecuta prepare-data.sh para un dataset y K. Devuelve (rc, log)."""
    s = get_settings()
    script = s.project_root / s.prepare_data_script
    env = {**os.environ, "DATASET": dataset, "K": str(k), "PARTITION": strategy}
    if alpha is not None:
        env["DIRICHLET_ALPHA"] = str(alpha)
    proc = await asyncio.create_subprocess_exec(
        "bash", str(script),
        stdout=asyncio.subprocess.PIPE,
        stderr=asyncio.subprocess.STDOUT,
        cwd=str(s.project_root),
        env=env,
    )
    out_b, _ = await proc.communicate()
    return proc.returncode or 0, out_b.decode("utf-8", errors="replace")
