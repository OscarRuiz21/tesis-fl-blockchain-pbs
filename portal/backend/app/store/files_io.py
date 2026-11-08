"""Helpers de filesystem para experiments/ y results/."""
from __future__ import annotations

import json
import re
from datetime import datetime
from pathlib import Path

import yaml

from app.config import get_settings


_SAFE_LABEL_RE = re.compile(r"[^a-zA-Z0-9_\-]")


def safe_label(raw: str) -> str:
    """Sanitiza un label para usarlo como nombre de archivo/dir."""
    s = _SAFE_LABEL_RE.sub("_", raw.strip())
    return s or "exp"


def experiments_dir() -> Path:
    d = get_settings().experiments_dir
    d.mkdir(parents=True, exist_ok=True)
    return d


def results_dir() -> Path:
    d = get_settings().results_dir
    d.mkdir(parents=True, exist_ok=True)
    return d


def spec_path_for(label: str) -> Path:
    return experiments_dir() / f"{safe_label(label)}.yml"


def write_spec_yaml(label: str, spec_dict: dict) -> Path:
    """Escribe un spec YAML en experiments/<label>.yml."""
    path = spec_path_for(label)
    with path.open("w", encoding="utf-8") as f:
        yaml.safe_dump(spec_dict, f, sort_keys=False, default_flow_style=False)
    return path


def read_spec_yaml(label: str) -> dict:
    path = spec_path_for(label)
    if not path.exists():
        raise FileNotFoundError(f"Spec not found: {path}")
    with path.open("r", encoding="utf-8") as f:
        return yaml.safe_load(f)


def delete_spec(label: str) -> bool:
    path = spec_path_for(label)
    if path.exists():
        path.unlink()
        return True
    return False


def list_specs() -> list[dict]:
    """Lista todos los specs guardados como [{label, path, modified}]."""
    out = []
    for p in sorted(experiments_dir().glob("*.yml")):
        try:
            with p.open("r", encoding="utf-8") as f:
                doc = yaml.safe_load(f) or {}
            label = doc.get("experiment", {}).get("label", p.stem)
            out.append({
                "label": label,
                "path": str(p),
                "modified": datetime.fromtimestamp(p.stat().st_mtime).isoformat(),
                "raw": doc,
            })
        except (yaml.YAMLError, OSError):
            continue
    return out


def list_result_dirs() -> list[dict]:
    """Lista los subdirectorios de results/ con sus metadatos básicos."""
    out = []
    for d in sorted(results_dir().iterdir()):
        if not d.is_dir():
            continue
        summary = d / "run_summary.json"
        if summary.exists():
            try:
                with summary.open() as f:
                    body = json.load(f)
                out.append({
                    "label": body.get("experimentLabel", d.name),
                    "path": str(d),
                    "modified": datetime.fromtimestamp(d.stat().st_mtime).isoformat(),
                    "summary": body,
                })
                continue
            except (json.JSONDecodeError, OSError):
                pass
        out.append({
            "label": d.name,
            "path": str(d),
            "modified": datetime.fromtimestamp(d.stat().st_mtime).isoformat(),
            "summary": None,
        })
    return out


def result_path_for(label: str) -> Path:
    return results_dir() / safe_label(label)


def dataset_partitions_status() -> list[dict]:
    """Inspecciona data/partitions/ para listar K disponibles por dataset.

    Convención: data/partitions/<dataset>/client-<i>/*.csv producidas por
    simulate_clients.py. Si la estructura no matchea, retorna lista vacía.
    """
    data = get_settings().data_dir
    partitions = data / "partitions"
    if not partitions.exists():
        return []
    out = []
    for ds in sorted(partitions.iterdir()):
        if not ds.is_dir():
            continue
        ks = sorted({
            int(p.name.split("-")[1])
            for p in ds.iterdir()
            if p.is_dir() and p.name.startswith("client-") and p.name.split("-")[1].isdigit()
        })
        out.append({"dataset": ds.name, "K_available": ks})
    return out
