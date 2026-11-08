"""Lectura de resultados desde results/<label>/."""
from __future__ import annotations

import json
import logging
from datetime import datetime, timezone
from pathlib import Path

import pandas as pd

from app.schemas import ClientMetricRow, GlobalEvalRow, RunMetrics, RunRecord, RunSummary
from app.store import files_io

log = logging.getLogger(__name__)


def _safe_dt(s: str | None) -> datetime | None:
    if s is None:
        return None
    try:
        return datetime.fromisoformat(s.replace("Z", "+00:00"))
    except (ValueError, TypeError):
        return None


def read_summary(label: str) -> RunSummary | None:
    path = files_io.result_path_for(label) / "run_summary.json"
    if not path.exists():
        return None
    try:
        with path.open() as f:
            return RunSummary.model_validate(json.load(f))
    except (json.JSONDecodeError, ValueError) as e:
        log.warning("Could not parse %s: %s", path, e)
        return None


def to_utc(dt: datetime) -> datetime:
    """Garantiza que el datetime esté en UTC tz-aware (para comparar)."""
    if dt.tzinfo is None:
        return dt.replace(tzinfo=timezone.utc)
    return dt.astimezone(timezone.utc)

# Alias privado retrocompatible.
_to_utc = to_utc


def read_global_evals(label: str, since: datetime | None = None) -> list[GlobalEvalRow]:
    """Lee global eval del último run.

    Si `since` está seteado, filtra solo filas con `timestamp >= since`. Útil
    para mostrar SOLO los datos del run actual cuando hay runs anteriores
    apilados en `global_eval.csv` (append-only) o cuando `run_summary.json`
    es del run pasado.
    """
    since_utc = _to_utc(since) if since else None

    # Fuente A: summary.globalEvals (1 entry por round del último run terminado).
    summary = read_summary(label)
    if summary is not None and summary.global_evals:
        rows: list[GlobalEvalRow] = []
        for r in summary.global_evals:
            ts = _safe_dt(r.get("timestamp"))
            if since_utc and ts and _to_utc(ts) < since_utc:
                continue  # del run anterior, descartar
            rows.append(GlobalEvalRow(
                round=int(r.get("round", 0)),
                accuracy=float(r.get("accuracy", 0.0)),
                loss=float(r.get("loss", 0.0)),
                eval_time_ms=int(r.get("evalTimeMs", 0)),
                timestamp=ts,
            ))
        if rows:
            return rows
        # El summary existe pero todas sus filas son del run anterior: caemos al CSV
        # para ver si hay rows nuevas en el run actual (durante RUNNING).

    # Fuente B: CSV append-only. Header:
    #   experiment,round_id,accuracy,loss,eval_time_s,timestamp
    path = files_io.result_path_for(label) / "global_eval.csv"
    if not path.exists():
        return []
    try:
        df = pd.read_csv(path)
    except (pd.errors.EmptyDataError, OSError):
        return []
    rows = []
    for _, r in df.iterrows():
        rd = r.get("round_id", r.get("round", 0))
        ts_raw = r.get("timestamp")
        ts = _safe_dt(str(ts_raw)) if ts_raw is not None else None
        if since_utc and ts and _to_utc(ts) < since_utc:
            continue
        rows.append(GlobalEvalRow(
            label=str(r.get("experiment")) if "experiment" in r else None,
            round=int(rd) if not pd.isna(rd) else 0,
            accuracy=float(r.get("accuracy", 0.0)),
            loss=float(r.get("loss", 0.0)),
            eval_time_ms=int(round(float(r.get("eval_time_s", 0.0)) * 1000)) if "eval_time_s" in r else 0,
            timestamp=ts,
        ))
    return rows


def read_client_metrics(label: str, last_n_rounds: int | None = None) -> dict[str, list[ClientMetricRow]]:
    """Lee todos los client-*.csv del directorio de resultados.

    Los CSVs son append-only: re-runs del mismo spec.label apilan filas. Para
    mostrar solo el último run, pasar `last_n_rounds=R` (tomado de summary.k/R).
    """
    base = files_io.result_path_for(label)
    out: dict[str, list[ClientMetricRow]] = {}
    if not base.exists():
        return out
    for csv in sorted(base.glob("client-*.csv")):
        cid = csv.stem  # client-1, client-2, ...
        try:
            # on_bad_lines='skip': los CSVs per-cliente del coordinator pueden
            # tener schema drift (timings JSON con campos opcionales según fase).
            df = pd.read_csv(csv, on_bad_lines="skip")
        except (pd.errors.EmptyDataError, OSError):
            continue
        # Filtro al último run: quedarse con las últimas N filas si se pidió.
        if last_n_rounds is not None and len(df) > last_n_rounds:
            df = df.tail(last_n_rounds)
        rows: list[ClientMetricRow] = []
        for _, r in df.iterrows():
            # El cliente Java emite filas con `train_accuracy/train_loss/...`,
            # nombres con prefijo `train_`. Soportamos también nombres "plain"
            # por compatibilidad futura.
            acc = r.get("train_accuracy", r.get("accuracy", float("nan")))
            loss = r.get("train_loss", r.get("loss", float("nan")))
            ns = r.get("train_num_samples", r.get("num_samples", 0))
            elapsed = r.get("round_wall_time_ms", r.get("elapsed_ms", 0))
            if pd.isna(acc) or pd.isna(loss):
                continue  # fila parcial sin métricas (e.g. solo timings de fase)
            try:
                rows.append(ClientMetricRow(
                    round=int(r.get("round", 0)),
                    accuracy=float(acc),
                    loss=float(loss),
                    num_samples=int(ns) if not pd.isna(ns) else 0,
                    elapsed_ms=int(elapsed) if not pd.isna(elapsed) else 0,
                ))
            except (ValueError, TypeError):
                continue
        out[cid] = rows
    return out


def build_metrics_bundle(
    label: str,
    run_id: str,
    run_started_at: datetime | None = None,
    run_is_terminal: bool = True,
) -> RunMetrics:
    """Construye el bundle de métricas para un run.

    - `run_started_at`: si el run actual arrancó después del run_summary.json,
      filtramos los datos viejos.
    - `run_is_terminal`: si False (run en curso), el summary y los CSVs apilados
      pueden ser del run anterior; usamos timestamps para filtrar.
    """
    summary = read_summary(label)
    # Si el summary terminó ANTES de que arrancara este run, es del run anterior.
    if summary is not None and run_started_at is not None:
        s_finished = summary.finished_at
        if s_finished is not None:
            if _to_utc(s_finished) < _to_utc(run_started_at):
                summary = None  # del run anterior, no mostrar como del actual

    global_evals = read_global_evals(label, since=run_started_at if not run_is_terminal else None)

    # Para per-cliente: si el run terminó, tomamos `tail(total_rounds)` del summary.
    # Si está en curso, no tenemos un buen filtro por timestamp en los CSVs per-client,
    # así que mostramos vacío hasta que termine (mejor que mostrar datos viejos).
    if run_is_terminal and summary is not None:
        per_client = read_client_metrics(label, last_n_rounds=summary.total_rounds)
    else:
        per_client = {}

    return RunMetrics(
        run_id=run_id,
        summary=summary,
        global_evals=global_evals,
        per_client=per_client,
    )
