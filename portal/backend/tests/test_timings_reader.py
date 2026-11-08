"""Tests del timings_reader: agregación cross-client + summary."""
from __future__ import annotations

import json
from datetime import datetime, timezone
from pathlib import Path

import pandas as pd

from app.config import get_settings
from app.schemas import RunRecord, RunState
from app.services import timings_reader


def _write_summary(label: str, n_rounds: int = 5, started_iso="2026-05-23T10:00:00+00:00",
                   finished_iso="2026-05-23T10:05:00+00:00"):
    base = get_settings().results_dir / label
    base.mkdir(parents=True, exist_ok=True)
    (base / "run_summary.json").write_text(json.dumps({
        "experimentLabel": label,
        "k": 5,
        "totalRounds": n_rounds,
        "strategy": "fedavg",
        "anonymityMode": "pbs",
        "seed": 42,
        "startedAt": started_iso,
        "finishedAt": finished_iso,
        "totalSeconds": 300.0,
        "roundsCompleted": n_rounds,
        "finalAccuracy": 0.8,
        "finalLoss": 0.4,
        "clientsCompleted": 5,
        "clientsFailed": 0,
        "globalEvals": [
            {"round": r + 1, "accuracy": 0.7 + r * 0.02, "loss": 0.5 - r * 0.01,
             "evalTimeMs": 100, "timestamp": started_iso}
            for r in range(n_rounds)
        ],
        "clients": [{"clientId": f"client-{i+1}", "recordedRounds": n_rounds} for i in range(5)],
    }))


def _write_client_csv(label: str, client_id: int, n_rounds: int = 5, train_ms=20000,
                      pbs_each_ms=50, submit_ms=2000, phase3_ms=200,
                      tally_ms=0, cc_total_ms=None, cc_agg_ms=0, cc_rq_ms=0):
    """Si cc_total_ms es None, defaultea a 0 (matching el chaincode silencioso).
    En tests del block_commit pasar cc_total_ms explícito + phase3_ms > cc_total_ms.
    """
    if cc_total_ms is None:
        cc_total_ms = 0
    base = get_settings().results_dir / label
    base.mkdir(parents=True, exist_ok=True)
    rows = []
    rows.append(
        "round,clientId,phase1_blind_ms,phase1_unblind_ms,phase1_verify_ms,"
        "phase1_commit_rtt_ms,phase1_sign_rtt_ms,train_loss,train_accuracy,"
        "train_num_samples,train_duration_ms,phase2_submit_ms,phase3_triggered,"
        "phase3_duration_ms,chaincode_range_query_ms,chaincode_aggregation_ms,"
        "chaincode_tally_ms,chaincode_total_ms,round_wall_time_ms,"
        "phase3_poll_count,phase3_poll_ms,phase3_was_leader,phase3_leader_position"
    )
    for r in range(1, n_rounds + 1):
        is_leader = client_id == 1
        leader_flag = 'true' if is_leader else 'false'
        rows.append(
            f"{r},client-{client_id},{pbs_each_ms},{pbs_each_ms},{pbs_each_ms},"
            f"{pbs_each_ms},{pbs_each_ms},0.4,0.8,1000,{train_ms},{submit_ms},"
            f"{leader_flag},{phase3_ms},{cc_rq_ms},{cc_agg_ms},"
            f"{tally_ms},{cc_total_ms},{train_ms + 5 * pbs_each_ms + submit_ms + phase3_ms},"
            f"0,0,{leader_flag},{0 if is_leader else client_id - 1}"
        )
    (base / f"client-{client_id}.csv").write_text("\n".join(rows) + "\n")


def _make_record(label: str, state=RunState.DONE) -> RunRecord:
    return RunRecord(
        run_id=f"test-{label}",
        spec_label=label,
        spec_path=f"/tmp/{label}.yml",
        state=state,
        queued_at=datetime(2026, 5, 23, 9, 59, tzinfo=timezone.utc),
        started_at=datetime(2026, 5, 23, 10, 0, tzinfo=timezone.utc),
        finished_at=datetime(2026, 5, 23, 10, 5, tzinfo=timezone.utc),
    )


def test_build_bundle_returns_per_round_when_summary_and_csvs_present():
    label = "test-tim-1"
    _write_summary(label, n_rounds=3)
    for c in range(1, 6):
        _write_client_csv(label, c, n_rounds=3)

    bundle = timings_reader.build_bundle(_make_record(label))
    assert len(bundle.per_round) == 3
    rt = bundle.per_round[0]
    assert rt.round == 1
    assert rt.k_clients == 5
    assert rt.train_ms_avg == 20000
    # PBS = suma de 5 fases × 50ms = 250ms cada cliente
    assert rt.pbs_ms_avg == 250
    assert rt.submit_ms_avg == 2000


def test_summary_calculates_percentages():
    label = "test-tim-2"
    _write_summary(label, n_rounds=3)
    for c in range(1, 6):
        _write_client_csv(label, c, n_rounds=3, train_ms=10000, pbs_each_ms=10,
                          submit_ms=1000, phase3_ms=100)

    bundle = timings_reader.build_bundle(_make_record(label))
    s = bundle.summary
    assert s is not None
    # 3 rondas: total_train = 30000ms, total_pbs = 150ms, total_submit = 3000ms
    assert s.total_train_ms == 30000
    assert s.total_pbs_ms == 150
    assert s.total_submit_ms == 3000
    # PBS overhead debe ser muy bajo (~ 0.4%)
    assert s.pbs_overhead_pct < 1.0
    # Train debería ser el mayor porcentaje
    assert s.pct_train > 50


def test_convergence_computes_deltas():
    label = "test-tim-3"
    _write_summary(label, n_rounds=3)
    for c in range(1, 6):
        _write_client_csv(label, c, n_rounds=3)

    bundle = timings_reader.build_bundle(_make_record(label))
    conv = bundle.convergence
    assert len(conv) == 3
    assert conv[0].delta_accuracy == 0.0  # primera ronda, sin delta
    # Δ entre acc[1]=0.72 y acc[0]=0.70 = 0.02
    assert abs(conv[1].delta_accuracy - 0.02) < 0.001


def test_bundle_empty_when_no_data():
    bundle = timings_reader.build_bundle(_make_record("nonexistent"))
    assert bundle.per_round == []
    assert bundle.summary is None
    assert bundle.convergence == []


def test_tally_hash_total_aggregates():
    label = "test-tim-tally"
    _write_summary(label, n_rounds=2)
    # Solo el leader (client-1) escribe chaincode_tally_ms > 0
    for c in range(1, 6):
        tally = 15 if c == 1 else 0
        _write_client_csv(label, c, n_rounds=2, tally_ms=tally)

    bundle = timings_reader.build_bundle(_make_record(label))
    # Tally_hash = 15ms × 2 rondas = 30ms total
    assert bundle.summary is not None
    assert bundle.summary.tally_hash_total_ms == 30


def test_block_commit_and_chaincode_cpu_breakdown():
    """Etapa 3 del líder = 2000 ms, chaincode_total = 25 ms, así que block_commit = 1975 ms."""
    label = "test-tim-bc-breakdown"
    _write_summary(label, n_rounds=2)
    # Leader (client-1) tarda 2000ms en la Etapa 3, con 25ms de chaincode CPU.
    _write_client_csv(label, 1, n_rounds=2, train_ms=10000, pbs_each_ms=10,
                      submit_ms=500, phase3_ms=2000, cc_total_ms=25,
                      cc_agg_ms=10, cc_rq_ms=5, tally_ms=10)
    # Non-leaders: phase3=0 (no triggered), chaincode_total=0.
    for c in range(2, 6):
        _write_client_csv(label, c, n_rounds=2, train_ms=10000, pbs_each_ms=10,
                          submit_ms=500, phase3_ms=0, cc_total_ms=0)

    bundle = timings_reader.build_bundle(_make_record(label))
    s = bundle.summary
    assert s is not None
    # 2 rondas × 25ms chaincode CPU = 50ms
    assert s.total_chaincode_cpu_ms == 50
    # 2 rondas × (2000 - 25) = 3950ms block commit latency
    assert s.total_block_commit_ms == 3950
    # Submit RTT = 2 rondas × avg_K(500) = 1000ms
    assert s.total_submit_rtt_ms == 1000
    # Tally aislado debe seguir igual: 2 × 10 = 20ms
    assert s.tally_hash_total_ms == 20
    # Sanity: pct_block_commit > pct_chaincode_cpu (commit domina)
    assert s.pct_block_commit > s.pct_chaincode_cpu
    # Per-round: block_commit_ms_avg = 1975 en cada ronda
    assert bundle.per_round[0].block_commit_ms_avg == 1975
    assert bundle.per_round[0].leader_phase3_wall_ms == 2000


def test_leader_detected_via_was_leader_when_triggered_false():
    """Caso R2 pbs-seed13: el líder fue elegido por nullifier ordering pero
    su tx de aggregate perdió la carrera contra el backup del coordinator
    (triggered=false, was_leader=true). El wall del líder sigue siendo
    medible y block_commit no debe colapsar a 0."""
    import csv as csvlib
    label = "test-tim-backup-won"
    _write_summary(label, n_rounds=1)
    # client-1 fue líder (was_leader=true) pero su tx falló (triggered=false).
    # phase3_duration capturó el wall completo (2000ms) incluyendo el polling
    # tras la falla. chaincode_total=0 porque la tx del líder no commiteó.
    base = get_settings().results_dir / label
    base.mkdir(parents=True, exist_ok=True)
    header = (
        "round,clientId,phase1_blind_ms,phase1_unblind_ms,phase1_verify_ms,"
        "phase1_commit_rtt_ms,phase1_sign_rtt_ms,train_loss,train_accuracy,"
        "train_num_samples,train_duration_ms,phase2_submit_ms,phase3_triggered,"
        "phase3_duration_ms,chaincode_range_query_ms,chaincode_aggregation_ms,"
        "chaincode_tally_ms,chaincode_total_ms,round_wall_time_ms,"
        "phase3_poll_count,phase3_poll_ms,phase3_was_leader,phase3_leader_position"
    )
    # client-1: líder elegido, pero su aggregate falló: triggered=false, cc=0, dur=2000
    (base / "client-1.csv").write_text(
        f"{header}\n"
        "1,client-1,10,0,10,30,30,0.4,0.8,1000,10000,500,false,2000,0,0,0,0,12580,5,200,true,0\n"
    )
    # Resto de clientes no fueron líderes
    for c in range(2, 6):
        (base / f"client-{c}.csv").write_text(
            f"{header}\n"
            f"1,client-{c},10,0,10,30,30,0.4,0.8,1000,10000,500,false,0,0,0,0,0,10580,5,200,false,{c-1}\n"
        )

    bundle = timings_reader.build_bundle(_make_record(label))
    assert len(bundle.per_round) == 1
    r1 = bundle.per_round[0]
    # El líder se identifica por was_leader, no por triggered.
    assert r1.leader_phase3_wall_ms == 2000, "wall del líder elegido debe registrarse aunque triggered=false"
    # block_commit aproxima al wall completo cuando el chaincode_total no se midió.
    assert r1.block_commit_ms_avg == 2000


def test_summary_discarded_if_older_than_run():
    label = "test-tim-old"
    # Summary terminó ANTES de que arrancara este run
    _write_summary(label, finished_iso="2026-05-23T09:00:00+00:00")
    for c in range(1, 6):
        _write_client_csv(label, c)

    bundle = timings_reader.build_bundle(_make_record(label))
    # Sin summary no se calculan per_round
    assert bundle.summary is None
    assert bundle.per_round == []
