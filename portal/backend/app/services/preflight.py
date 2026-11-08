"""Preflight: detecta estado del cluster antes de un run y auto-ajusta.

Para cada `POST /api/runs`, este servicio:
1. Verifica que HLF (orderers + peers + chaincode CCaaS) esté arriba.
   Si no, retorna error (el operador debe correr `start-network.sh`).
2. Verifica que coordinator esté healthy. Si no, lo levanta.
3. Recrea `fl-auth-server` siempre, para limpiar el `IssuanceLedger` in-memory
   y evitar `DuplicateCredentialRequest` en re-runs del mismo spec.
4. Escala `fl-client` al K del spec y lo recrea: clientes en estado IDLE limpio.
5. Espera que todos los clientes reporten `state: IDLE` via su HTTP endpoint.

Estrategia: usa `docker compose` CLI desde dentro del container del portal
(socket Docker mounted). Logs detallados streameados al log file del run.

Exit codes:
- `(True, msg)`: todo OK, listo para el POST al coordinator.
- `(False, msg)`: falta algo crítico (típicamente, la red HLF está abajo).
"""
from __future__ import annotations

import asyncio
import json
import logging
import os
from pathlib import Path
from typing import Callable, Optional

import httpx
import yaml

from app.config import get_settings

log = logging.getLogger(__name__)


# Cache del host path detectado por auto-introspección.
_host_path_cache: Path | None = None


async def _detect_host_project_dir() -> Path | None:
    """Auto-descubre el host path del repo inspeccionando los mounts del
    container del portal vía Docker API.

    Estrategia: busca el mount cuyo Destination es /app/docker-compose.yml y
    devuelve el directorio padre del Source (host path).

    Cacheado tras la primera detección exitosa.
    """
    global _host_path_cache
    if _host_path_cache is not None:
        return _host_path_cache

    hostname = os.environ.get("HOSTNAME", "")
    if not hostname:
        return None

    proc = await asyncio.create_subprocess_exec(
        "docker", "inspect", hostname,
        "--format", "{{json .Mounts}}",
        stdout=asyncio.subprocess.PIPE,
        stderr=asyncio.subprocess.PIPE,
    )
    out_b, _ = await proc.communicate()
    if proc.returncode != 0:
        return None
    try:
        mounts = json.loads(out_b.decode())
    except json.JSONDecodeError:
        return None
    for m in mounts:
        if m.get("Destination") == "/app/docker-compose.yml":
            source = m.get("Source")
            if source:
                _host_path_cache = Path(source).parent
                log.info("Auto-detected host project dir: %s", _host_path_cache)
                return _host_path_cache
    return None

# Servicios HLF que deben estar arriba (no se recrean: re-deployar chaincode
# es destructivo y caro). Si alguno falta, abortamos y reportamos al operador.
HLF_REQUIRED = [
    "orderer0.fl.network",
    "orderer1.fl.network",
    "orderer2.fl.network",
    "peer0.org1.fl.network",
    "peer0.org2.fl.network",
    "chaincode.flprotocol.fl.network",
]


async def ensure_ready_for_run(
    spec_path: Path,
    log_fn: Callable[[str], None],
    timeout_clients_idle_s: float = 60.0,
) -> tuple[bool, str]:
    """Orquesta el preflight. Retorna (ok, message).

    `log_fn` recibe cada línea de progreso (el caller la persiste donde quiera).
    """
    settings = get_settings()
    try:
        spec_raw = yaml.safe_load(spec_path.read_text())
    except Exception as e:
        return False, f"Cannot parse spec YAML: {e}"

    try:
        k = int(spec_raw["federation"]["num_clients"])
    except (KeyError, TypeError, ValueError):
        return False, "Spec missing federation.num_clients (int)"

    log_fn(f"Preflight para K={k}")
    project_root = settings.project_root

    # ---- 1. HLF arriba ----
    log_fn("[1/6] Chequeando HLF (orderers + peers + chaincode)…")
    missing = await _missing_containers(HLF_REQUIRED)
    if missing:
        return False, (
            f"HLF no está arriba, faltan: {', '.join(missing)}. "
            "Correr `./scripts/start-network.sh` desde el host primero."
        )
    log_fn(f"  ✓ HLF OK ({len(HLF_REQUIRED)} servicios)")

    # ---- 2. Coordinator arriba ----
    log_fn("[2/6] Chequeando coordinator…")
    coord_missing = await _missing_containers(["coordinator"])
    if coord_missing:
        log_fn("  ! coordinator caído, levantando…")
        rc, out = await _compose(project_root, ["up", "-d", "--wait", "coordinator"])
        if rc != 0:
            return False, f"docker compose up coordinator failed (rc={rc}):\n{out[:500]}"
        log_fn("  ✓ coordinator levantado")
    else:
        log_fn("  ✓ coordinator OK")

    # ---- 3. Particiones del dataset (auto-prep si faltan) ----
    log_fn("[3/6] Particiones del dataset…")
    # Import diferido para evitar import circular (data_prep importa de este módulo).
    from app.services.data_prep import ensure_partitions

    ok_prep, msg_prep = await ensure_partitions(spec_path, log_fn)
    if not ok_prep:
        return False, f"prepare-data falló: {msg_prep}"

    # ---- 4. AS recreate (siempre, para limpiar IssuanceLedger) ----
    log_fn("[4/6] Recreando fl-auth-server (limpia IssuanceLedger)…")
    rc, out = await _compose(
        project_root,
        ["up", "-d", "--force-recreate", "--wait", "fl-auth-server"],
    )
    if rc != 0:
        return False, f"recreate AS failed (rc={rc}):\n{out[:500]}"
    log_fn("  ✓ AS healthy")

    # ---- 5. fl-client escalado a K y recreado ----
    log_fn(f"[5/6] Escalando fl-client a K={k} + force-recreate…")
    rc, out = await _compose(
        project_root,
        ["up", "-d", f"--scale=fl-client={k}", "--force-recreate", "--wait", "fl-client"],
    )
    if rc != 0:
        return False, f"scale fl-client failed (rc={rc}):\n{out[:500]}"
    log_fn(f"  ✓ {k} clientes healthy según Docker")

    # ---- 6. Esperar a que todos los clientes reporten IDLE ----
    log_fn(f"[6/6] Esperando que los {k} clientes reporten IDLE…")
    ok, msg = await _wait_clients_idle(k, timeout_clients_idle_s, log_fn)
    if not ok:
        return False, msg

    log_fn(f"  ✓ {k} clientes en IDLE")
    log_fn("Preflight DONE, listo para POST /experiments/run")
    return True, f"Cluster listo para K={k}"


# -----------------------------------------------------------------------------
# Helpers
# -----------------------------------------------------------------------------

async def _compose(cwd: Path, args: list[str]) -> tuple[int, str]:
    """Ejecuta `docker compose <args>` y retorna (rc, combined_output).

    Si `PORTAL_HOST_PROJECT_DIR` está seteado, lo usa como `--project-directory`
    para que el Docker daemon (que corre en el HOST) resuelva paths relativos
    del compose contra el filesystem del host, no del container del portal.
    """
    settings = get_settings()
    # Prefiere host_project_dir explícito; si no, auto-detecta.
    host_dir = settings.host_project_dir or await _detect_host_project_dir()
    cmd = ["docker", "compose"]
    # `-p`: mismo project name que el stack ya corriendo; sin él se crearía uno paralelo.
    cmd += ["-p", settings.compose_project]
    # `-f` lo lee el CLI dentro del container, así que va el path interno montado.
    cmd += ["-f", str(settings.project_root / "docker-compose.yml")]
    # `--project-directory` lo recibe el daemon en el host, así que va el path real del host.
    if host_dir is not None:
        cmd += ["--project-directory", str(host_dir)]
    cmd += args
    proc = await asyncio.create_subprocess_exec(
        *cmd,
        stdout=asyncio.subprocess.PIPE,
        stderr=asyncio.subprocess.STDOUT,
        cwd=str(cwd),
    )
    out_b, _ = await proc.communicate()
    return proc.returncode or 0, out_b.decode("utf-8", errors="replace")


async def _missing_containers(names: list[str]) -> list[str]:
    """Retorna la sublista de `names` que NO están corriendo (status != running)."""
    proc = await asyncio.create_subprocess_exec(
        "docker", "ps", "--format", "{{.Names}}",
        stdout=asyncio.subprocess.PIPE,
        stderr=asyncio.subprocess.PIPE,
    )
    out_b, _ = await proc.communicate()
    running = set(out_b.decode().split())
    return [n for n in names if n not in running]


async def _wait_clients_idle(
    k: int,
    timeout_s: float,
    log_fn: Callable[[str], None],
    project_prefix: str = "fl-pbs",
) -> tuple[bool, str]:
    """Polls /lifecycle/status de cada cliente vía la red Docker hasta que
    todos respondan IDLE, o se agote el timeout."""
    targets = [f"http://{project_prefix}-fl-client-{i}:8080" for i in range(1, k + 1)]
    deadline = asyncio.get_event_loop().time() + timeout_s
    pending = set(range(1, k + 1))

    async with httpx.AsyncClient(timeout=httpx.Timeout(3.0, connect=1.5)) as cli:
        while pending and asyncio.get_event_loop().time() < deadline:
            still_pending = set()
            for i in sorted(pending):
                url = f"{targets[i-1]}/lifecycle/status"
                try:
                    r = await cli.get(url)
                    if r.status_code == 200 and r.json().get("state") == "IDLE":
                        log_fn(f"  client-{i} IDLE ✓")
                        continue
                    still_pending.add(i)
                except Exception:
                    still_pending.add(i)
            pending = still_pending
            if pending:
                await asyncio.sleep(2.0)

    if pending:
        return False, f"Timeout: clients no en IDLE tras {timeout_s:.0f}s: {sorted(pending)}"
    return True, "all IDLE"
