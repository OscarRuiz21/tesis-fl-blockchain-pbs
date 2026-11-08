"""Auto-prep de particiones de datos para un spec del experimento.

Corre `scripts/prepare-data.sh` dentro de un container efímero `fl-data-prep`
(profile `prep`) que tiene yq + pandas/numpy/sklearn pero NO torch. Esto evita
que el operador tenga que correr el script manualmente desde el host antes de
cada experimento con K nuevo / seed nuevo / dataset distinto.

Convención de paths (espejada del script):
    python/data/<dataset_name>/partitions/K{K}_seed{seed}_<strategy>[_alphaX]/client-{i}.csv

Si client-1.csv ya existe, se reutilizan las particiones y no se regenera nada.
"""
from __future__ import annotations

import asyncio
import logging
from pathlib import Path
from typing import Callable

import yaml

from app.config import get_settings
from app.services.preflight import _detect_host_project_dir

log = logging.getLogger(__name__)


def expected_partition_dir(spec: dict) -> Path:
    """Calcula el path RELATIVO al project_root donde deberían vivir las
    particiones para este spec. Mismo cálculo que prepare-data.sh."""
    ds_name = spec.get("dataset", {}).get("name", "uci_credit")
    K = int(spec["federation"]["num_clients"])
    seed = int(spec["experiment"]["seed"])
    strategy = spec["partition"]["strategy"]
    if strategy == "dirichlet":
        alpha = spec["partition"].get("alpha", 0.5)
        suffix = f"K{K}_seed{seed}_dirichlet_alpha{alpha}"
    else:
        suffix = f"K{K}_seed{seed}_{strategy}"
    return Path("python/data") / ds_name / "partitions" / suffix


def partitions_present(spec: dict) -> tuple[bool, Path]:
    """¿Las particiones ya están en disco? (chequea client-1.csv)."""
    rel = expected_partition_dir(spec)
    full = get_settings().project_root / rel
    return (full / "client-1.csv").exists(), full


async def ensure_partitions(
    spec_path: Path,
    log_fn: Callable[[str], None],
) -> tuple[bool, str]:
    """Si faltan particiones del spec, las genera vía fl-data-prep.

    Retorna (ok, msg).
    """
    try:
        spec = yaml.safe_load(spec_path.read_text())
    except Exception as e:
        return False, f"Cannot parse spec YAML: {e}"

    present, expected = partitions_present(spec)
    rel_expected = expected.name
    if present:
        log_fn(f"  ✓ Particiones presentes: {rel_expected}")
        return True, "cached"

    log_fn(f"  ! Faltan particiones, generando con prepare-data.sh…")
    log_fn(f"    target: {rel_expected}")

    s = get_settings()
    spec_rel = spec_path.relative_to(s.project_root)
    host_dir = s.host_project_dir or await _detect_host_project_dir()

    cmd = ["docker", "compose"]
    cmd += ["-p", s.compose_project]
    cmd += ["-f", str(s.project_root / "docker-compose.yml")]
    if host_dir is not None:
        cmd += ["--project-directory", str(host_dir)]
    cmd += ["--profile", "prep"]
    cmd += [
        "run", "--rm", "fl-data-prep",
        "/workspace/scripts/prepare-data.sh",
        f"/workspace/{spec_rel}",
    ]
    log_fn(f"    $ docker compose run --rm fl-data-prep prepare-data.sh {spec_rel}")

    proc = await asyncio.create_subprocess_exec(
        *cmd,
        stdout=asyncio.subprocess.PIPE,
        stderr=asyncio.subprocess.STDOUT,
    )
    out_b, _ = await proc.communicate()
    out = out_b.decode("utf-8", errors="replace").strip()
    for line in out.splitlines():
        log_fn(f"    | {line}")

    if proc.returncode != 0:
        return False, f"prepare-data falló (rc={proc.returncode})"

    # Verificar que efectivamente quedaron las particiones.
    present_after, _ = partitions_present(spec)
    if not present_after:
        return False, "prepare-data terminó OK pero client-1.csv sigue ausente"

    log_fn(f"  ✓ Particiones generadas en {rel_expected}")
    return True, "generated"
