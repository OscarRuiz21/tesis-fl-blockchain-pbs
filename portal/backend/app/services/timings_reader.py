"""Lectura y agregación de timings cross-client desde los CSVs per-cliente.

Núcleo del análisis cuantitativo del overhead de PBS y blockchain.
"""
from __future__ import annotations

import logging
from datetime import datetime
from pathlib import Path

import pandas as pd

from app.schemas import (
    ConvergenceRow,
    RoundTimings,
    RunRecord,
    RunSummary,
    TimingsBundle,
    TimingsSummary,
)
from app.services import results_reader
from app.store import files_io

log = logging.getLogger(__name__)

# Columnas del CSV per-cliente (el coordinator + cliente Java las escriben).
PBS_COLS = [
    "phase1_blind_ms",
    "phase1_unblind_ms",
    "phase1_verify_ms",
    "phase1_commit_rtt_ms",
    "phase1_sign_rtt_ms",
]
TRAIN_COL = "train_duration_ms"
SUBMIT_COL = "phase2_submit_ms"
PHASE3_COL = "phase3_duration_ms"
ROUND_WALL_COL = "round_wall_time_ms"
CHAINCODE_COLS = {
    "total": "chaincode_total_ms",
    "aggregation": "chaincode_aggregation_ms",
    "tally": "chaincode_tally_ms",
    "range_query": "chaincode_range_query_ms",
}

# Columnas instrumentadas por el chaincode y el cliente. Son las métricas
# que aíslan el costo del esquema PBS sin contaminación del jitter de red ni del
# orderer Raft.
SUBMIT_PBS_VERIFY_COL = "submit_pbs_verify_ms"
SUBMIT_SIG_MODEL_VERIFY_COL = "submit_sig_model_verify_ms"
SUBMIT_STATE_WRITE_COL = "submit_state_write_ms"
SUBMIT_CC_TOTAL_COL = "submit_chaincode_total_ms"
SUBMIT_ATTEMPTS_COL = "submit_attempts"
QUERY_GLOBAL_MODEL_COL = "query_global_model_ms"
FETCH_PUBKEY_RTT_COL = "phase1_fetch_pubkey_rtt_ms"

# Identificación del líder de la ronda. Por diseño del protocolo, el líder se
# elige determinísticamente por orden lexicográfico de nullifiers; sin embargo,
# su transacción de `aggregate` puede perder la carrera contra el backup del
# coordinator. `phase3_was_leader` refleja la elección (invariante del diseño);
# `phase3_triggered` sólo refleja si su tx commitó. Para medir el wall-clock de
# la Etapa 3 desde la perspectiva del participante con visibilidad, usamos el
# primero. Fallback al segundo para CSVs antiguos que no escribían
# `phase3_was_leader`.
PHASE3_WAS_LEADER_COL = "phase3_was_leader"
PHASE3_TRIGGERED_COL = "phase3_triggered"


def _load_per_client_dfs(
    label: str,
    last_n_rounds: int | None = None,
) -> list[pd.DataFrame]:
    """Carga client-*.csv como list[DataFrame] tail(last_n_rounds) cada uno."""
    base = files_io.result_path_for(label)
    if not base.exists():
        return []
    out: list[pd.DataFrame] = []
    for csv in sorted(base.glob("client-*.csv")):
        try:
            df = pd.read_csv(csv, on_bad_lines="skip")
        except (pd.errors.EmptyDataError, OSError):
            continue
        if last_n_rounds is not None and len(df) > last_n_rounds:
            df = df.tail(last_n_rounds).reset_index(drop=True)
        out.append(df)
    return out


def _round_timings(round_id: int, dfs_at_round: list[pd.DataFrame]) -> RoundTimings | None:
    """Agrega timings cross-client para una ronda específica.

    `dfs_at_round`: lista de filas DataFrames (una por cliente) correspondientes
    a la misma ronda.
    """
    # Construimos arrays cross-client
    train = []
    pbs = []
    submit = []
    phase3 = []
    cc_total = []
    cc_agg = []
    cc_tally = []
    cc_rq = []
    wall = []
    # Leader-only: una fila por ronda (el cliente elegido por nullifier ordering).
    # Identificado por phase3_was_leader=true (diseño), no por phase3_triggered
    # (que sólo es true si el aggregate del líder le ganó al backup del coord).
    leader_phase3_wall: list[float] = []
    leader_chaincode_total: list[float] = []
    # Columnas medidas dentro del chaincode (sin red).
    submit_pbs_verify: list[float] = []
    submit_sig_model_verify: list[float] = []
    submit_chaincode_total: list[float] = []
    query_global_model: list[float] = []
    fetch_pubkey_rtt: list[float] = []
    submit_attempts: list[float] = []

    for df_row in dfs_at_round:
        if df_row is None or df_row.empty:
            continue
        r = df_row.iloc[0]  # una fila por cliente por ronda
        # Train
        v = _num(r.get(TRAIN_COL))
        if v is not None:
            train.append(v)
        # PBS = suma de las 5 fases
        pbs_sum = 0.0
        has_pbs = False
        for c in PBS_COLS:
            x = _num(r.get(c))
            if x is not None:
                pbs_sum += x
                has_pbs = True
        if has_pbs:
            pbs.append(pbs_sum)
        # Submit
        v = _num(r.get(SUBMIT_COL))
        if v is not None:
            submit.append(v)
        # Etapa 3 (promedio entre los K clientes, diluido; útil para el área apilada).
        v = _num(r.get(PHASE3_COL))
        if v is not None:
            phase3.append(v)
        # Chaincode breakdown (solo el líder lo escribe != 0)
        v = _num(r.get(CHAINCODE_COLS["total"]))
        if v is not None and v > 0:
            cc_total.append(v)
        v = _num(r.get(CHAINCODE_COLS["aggregation"]))
        if v is not None and v > 0:
            cc_agg.append(v)
        v = _num(r.get(CHAINCODE_COLS["tally"]))
        if v is not None and v > 0:
            cc_tally.append(v)
        v = _num(r.get(CHAINCODE_COLS["range_query"]))
        if v is not None and v > 0:
            cc_rq.append(v)
        # Wall
        v = _num(r.get(ROUND_WALL_COL))
        if v is not None:
            wall.append(v)

        # Leader-only: el cliente elegido por nullifier ordering. Si está
        # `phase3_was_leader` lo usamos; si no, caemos a `phase3_triggered`
        # por compatibilidad con CSVs viejos.
        leader_flag = r.get(PHASE3_WAS_LEADER_COL)
        if leader_flag is None or (isinstance(leader_flag, float) and leader_flag != leader_flag):
            leader_flag = r.get(PHASE3_TRIGGERED_COL, "")
        is_leader = str(leader_flag).strip().lower() == "true"
        if is_leader:
            v = _num(r.get(PHASE3_COL))
            if v is not None:
                leader_phase3_wall.append(v)
            # chaincode_total puede ser 0 si el backup del coordinator ganó la
            # carrera (el líder elegido nunca completó su propia tx). En ese
            # caso block_commit se aproxima a phase3_wall completo, lo cual es
            # una sobreestimación menor (~1%) frente a la magnitud típica del
            # commit del orderer.
            v = _num(r.get(CHAINCODE_COLS["total"]))
            if v is not None:
                leader_chaincode_total.append(v)

        # Columnas sin red (no filtran por líder: todos los submits tienen estas
        # mediciones del chaincode side de su propia submit tx).
        v = _num(r.get(SUBMIT_PBS_VERIFY_COL))
        if v is not None:
            submit_pbs_verify.append(v)
        v = _num(r.get(SUBMIT_SIG_MODEL_VERIFY_COL))
        if v is not None:
            submit_sig_model_verify.append(v)
        v = _num(r.get(SUBMIT_CC_TOTAL_COL))
        if v is not None:
            submit_chaincode_total.append(v)
        v = _num(r.get(QUERY_GLOBAL_MODEL_COL))
        if v is not None:
            query_global_model.append(v)
        v = _num(r.get(FETCH_PUBKEY_RTT_COL))
        if v is not None:
            fetch_pubkey_rtt.append(v)
        v = _num(r.get(SUBMIT_ATTEMPTS_COL))
        if v is not None:
            submit_attempts.append(v)

    if not train and not pbs and not submit:
        return None

    # Block commit latency = lo que el líder esperó por el orderer (block cut
    # + propagación + read-after-write). Si no hay leader detectado, cae a 0.
    leader_p3 = _avg(leader_phase3_wall)
    leader_cc = _avg(leader_chaincode_total)
    block_commit = max(0.0, leader_p3 - leader_cc)

    return RoundTimings(
        round=round_id,
        k_clients=len(dfs_at_round),
        train_ms_avg=_avg(train),
        train_ms_min=_min(train),
        train_ms_max=_max(train),
        pbs_ms_avg=_avg(pbs),
        pbs_ms_min=_min(pbs),
        pbs_ms_max=_max(pbs),
        submit_ms_avg=_avg(submit),
        submit_ms_min=_min(submit),
        submit_ms_max=_max(submit),
        phase3_ms_avg=_avg(phase3),
        phase3_ms_max=_max(phase3),
        chaincode_total_ms_avg=_avg(cc_total),
        chaincode_aggregation_ms_avg=_avg(cc_agg),
        chaincode_tally_ms_avg=_avg(cc_tally),
        chaincode_range_query_ms_avg=_avg(cc_rq),
        leader_phase3_wall_ms=leader_p3,
        block_commit_ms_avg=block_commit,
        submit_pbs_verify_ms_avg=_avg(submit_pbs_verify),
        submit_sig_model_verify_ms_avg=_avg(submit_sig_model_verify),
        submit_chaincode_total_ms_avg=_avg(submit_chaincode_total),
        query_global_model_ms_avg=_avg(query_global_model),
        fetch_pubkey_rtt_ms_avg=_avg(fetch_pubkey_rtt),
        submit_attempts_avg=_avg(submit_attempts) if submit_attempts else 1.0,
        submit_attempts_max=int(_max(submit_attempts)) if submit_attempts else 1,
        round_wall_ms_avg=_avg(wall),
        round_wall_ms_max=_max(wall),
    )


def aggregate_timings_per_round(
    label: str,
    summary: RunSummary | None,
) -> list[RoundTimings]:
    """Devuelve un RoundTimings por ronda del último run."""
    if summary is None:
        return []
    dfs = _load_per_client_dfs(label, last_n_rounds=summary.total_rounds)
    if not dfs:
        return []

    out: list[RoundTimings] = []
    # Asumimos que tras tail(R) las filas están alineadas por índice (todos
    # los clientes empiezan en round=1 hasta round=R en orden).
    for round_idx in range(min(len(df) for df in dfs)):
        round_id = round_idx + 1
        dfs_at_round = [df.iloc[[round_idx]] for df in dfs if round_idx < len(df)]
        rt = _round_timings(round_id, dfs_at_round)
        if rt is not None:
            out.append(rt)
    return out


def build_summary(per_round: list[RoundTimings]) -> TimingsSummary | None:
    """Calcula totales + porcentajes desde la lista de rondas."""
    if not per_round:
        return None
    total_train = sum(r.train_ms_avg for r in per_round)
    total_pbs = sum(r.pbs_ms_avg for r in per_round)
    total_submit = sum(r.submit_ms_avg for r in per_round)
    total_phase3 = sum(r.phase3_ms_avg for r in per_round)
    total_wall = sum(r.round_wall_ms_avg for r in per_round)

    # Para porcentajes usamos la suma de las fases como denominador (puede
    # diferir del wall por solapamiento; usamos wall como referencia más justa).
    denom = max(total_wall, total_train + total_pbs + total_submit + total_phase3, 1.0)
    pct_train = 100.0 * total_train / denom
    pct_pbs = 100.0 * total_pbs / denom
    pct_submit = 100.0 * total_submit / denom
    pct_phase3 = 100.0 * total_phase3 / denom

    tally_total = sum(r.chaincode_tally_ms_avg for r in per_round)

    # Desglose blockchain granular (sólo leader rows aportan a chaincode/commit).
    total_submit_rtt = total_submit  # alias semántico: submit_ms_avg ES el RTT
    total_chaincode_cpu = sum(r.chaincode_total_ms_avg for r in per_round)
    total_block_commit = sum(r.block_commit_ms_avg for r in per_round)
    pct_submit_rtt = 100.0 * total_submit_rtt / denom
    pct_chaincode_cpu = 100.0 * total_chaincode_cpu / denom
    pct_block_commit = 100.0 * total_block_commit / denom

    # Métricas sin red: totales del experimento.
    # Cada submit aporta su valor de pbs_verify; multiplicamos por k_clients
    # para obtener el total de trabajo cross-K (avg ya es per-client).
    k = per_round[0].k_clients if per_round else 1
    total_pbs_verify = sum(r.submit_pbs_verify_ms_avg for r in per_round) * k
    total_sig_verify = sum(r.submit_sig_model_verify_ms_avg for r in per_round) * k
    total_cc_submit = sum(r.submit_chaincode_total_ms_avg for r in per_round) * k
    total_query_gm = sum(r.query_global_model_ms_avg for r in per_round) * k
    total_fetch_pk = sum(r.fetch_pubkey_rtt_ms_avg for r in per_round) * k
    # Attempts: el mean del experimento entero (1.0 = sin retries)
    mean_attempts = (
        sum(r.submit_attempts_avg for r in per_round) / len(per_round)
        if per_round else 1.0
    )
    max_attempts = max((r.submit_attempts_max for r in per_round), default=1)

    return TimingsSummary(
        rounds_observed=len(per_round),
        k_clients=per_round[0].k_clients if per_round else 0,
        total_train_ms=total_train,
        total_pbs_ms=total_pbs,
        total_submit_ms=total_submit,
        total_phase3_ms=total_phase3,
        total_wall_ms=total_wall,
        pct_train=pct_train,
        pct_pbs=pct_pbs,
        pct_submit=pct_submit,
        pct_phase3=pct_phase3,
        pbs_overhead_pct=pct_pbs,
        blockchain_overhead_pct=pct_submit + pct_phase3,
        tally_hash_total_ms=tally_total,
        total_submit_rtt_ms=total_submit_rtt,
        total_chaincode_cpu_ms=total_chaincode_cpu,
        total_block_commit_ms=total_block_commit,
        pct_submit_rtt=pct_submit_rtt,
        pct_chaincode_cpu=pct_chaincode_cpu,
        pct_block_commit=pct_block_commit,
        total_submit_pbs_verify_ms=total_pbs_verify,
        total_submit_sig_model_verify_ms=total_sig_verify,
        total_submit_chaincode_total_ms=total_cc_submit,
        total_query_global_model_ms=total_query_gm,
        total_fetch_pubkey_rtt_ms=total_fetch_pk,
        mean_submit_attempts=mean_attempts,
        max_submit_attempts=max_attempts,
    )


def build_convergence(global_evals) -> list[ConvergenceRow]:
    """Calcula Δaccuracy y Δloss por ronda."""
    out: list[ConvergenceRow] = []
    prev_acc = None
    prev_loss = None
    for e in global_evals:
        delta_acc = 0.0 if prev_acc is None else (e.accuracy - prev_acc)
        delta_loss = 0.0 if prev_loss is None else (e.loss - prev_loss)
        out.append(ConvergenceRow(
            round=e.round,
            accuracy=e.accuracy,
            delta_accuracy=delta_acc,
            loss=e.loss,
            delta_loss=delta_loss,
        ))
        prev_acc = e.accuracy
        prev_loss = e.loss
    return out


def build_bundle(rec: RunRecord) -> TimingsBundle:
    """Bundle completo de timings para GET /runs/{id}/timings."""
    summary = results_reader.read_summary(rec.spec_label)
    # Solo mostramos timings limpios si el summary es del run actual.
    if summary is not None and rec.started_at is not None:
        s_finished = summary.finished_at
        if s_finished is not None:
            from app.services.results_reader import to_utc
            if to_utc(s_finished) < to_utc(rec.started_at):
                summary = None

    per_round = aggregate_timings_per_round(rec.spec_label, summary)
    timings_summary = build_summary(per_round)

    # Convergencia desde los global_evals filtrados por started_at.
    metrics = results_reader.build_metrics_bundle(
        rec.spec_label,
        rec.run_id,
        run_started_at=rec.started_at,
        run_is_terminal=rec.state.is_terminal,
    )
    convergence = build_convergence(metrics.global_evals)

    return TimingsBundle(
        run_id=rec.run_id,
        per_round=per_round,
        summary=timings_summary,
        convergence=convergence,
    )


# ----------------------------------------------------------------------------
# Helpers numéricos (defensivos contra NaN/strings/None)
# ----------------------------------------------------------------------------

def _num(v) -> float | None:
    if v is None:
        return None
    try:
        f = float(v)
    except (ValueError, TypeError):
        return None
    if f != f:  # NaN
        return None
    return f


def _avg(xs: list[float]) -> float:
    return sum(xs) / len(xs) if xs else 0.0


def _min(xs: list[float]) -> float:
    return min(xs) if xs else 0.0


def _max(xs: list[float]) -> float:
    return max(xs) if xs else 0.0
