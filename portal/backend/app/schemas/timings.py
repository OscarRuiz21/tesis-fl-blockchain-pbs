"""Schemas para el análisis temporal del run: overhead de PBS y blockchain."""
from __future__ import annotations

from pydantic import BaseModel, Field


class RoundTimings(BaseModel):
    """Métricas temporales agregadas a través de los K clientes en una ronda.

    Todos los valores en milisegundos. avg/min/max sobre los K clientes.
    """

    round: int
    k_clients: int

    # ---- Entrenamiento local (ML) ----
    train_ms_avg: float
    train_ms_min: float
    train_ms_max: float

    # ---- Etapa 1: emisión de la credencial (PBS), costo del anonimato ----
    # Suma de blind + unblind + verify + commit_rtt + sign_rtt por cliente,
    # luego avg/min/max sobre K.
    pbs_ms_avg: float
    pbs_ms_min: float
    pbs_ms_max: float

    # ---- Etapa 2: envío al chaincode (firma ECDSA y validación en el chaincode) ----
    submit_ms_avg: float
    submit_ms_min: float
    submit_ms_max: float

    # ---- Etapa 3: agregación (solo el líder la ejecuta, los demás esperan) ----
    phase3_ms_avg: float  # tiempo total de espera + ejecución (avg cross-K, diluido)
    phase3_ms_max: float

    # ---- Chaincode breakdown (sólo el líder lo ejecuta, valores ya filtrados) ----
    chaincode_total_ms_avg: float = 0.0
    chaincode_aggregation_ms_avg: float = 0.0
    chaincode_tally_ms_avg: float = 0.0
    chaincode_range_query_ms_avg: float = 0.0

    # ---- Leader-only blockchain breakdown ----
    # leader_phase3_wall = lo que tarda el líder en la Etapa 3 (sin diluir por los
    # K-1 clientes que esperan).
    # block_commit_ms = leader_phase3_wall - chaincode_total = latencia del
    # orderer Raft (block cut + propagación), históricamente el cuello de
    # botella real del stack HLF.
    leader_phase3_wall_ms: float = 0.0
    block_commit_ms_avg: float = 0.0

    # ---- Métricas sin ruido de red ----
    # submit_pbs_verify_ms: chaincode verify de la credencial Abe-Okamoto
    # (en proceso, sin red). En modo none es exactamente 0. Es la métrica que
    # aísla el costo del esquema de anonimato del ruido del cluster.
    submit_pbs_verify_ms_avg: float = 0.0
    submit_sig_model_verify_ms_avg: float = 0.0
    submit_chaincode_total_ms_avg: float = 0.0
    # query_global_model: lectura del modelo agregado entre la Etapa 1 y el
    # entrenamiento.
    query_global_model_ms_avg: float = 0.0
    # fetch_pubkey: GET /blind-sign/public-key al AS. Cache miss en R1
    # (handshake mTLS + RTT), casi 0 de R2 en adelante porque el cliente lo cachea.
    fetch_pubkey_rtt_ms_avg: float = 0.0
    # submit_attempts: contador del retry loop con backoff por MVCC.
    # =1 normalmente, >1 indica contención del orderer.
    submit_attempts_avg: float = 1.0
    submit_attempts_max: int = 1

    # ---- Round wall time (clock-on-the-wall, del cliente que tardó más) ----
    round_wall_ms_avg: float
    round_wall_ms_max: float


class TimingsSummary(BaseModel):
    """Resumen agregado del experimento completo (todas las rondas)."""

    rounds_observed: int
    k_clients: int

    # Totales acumulados (suma de avg per round)
    total_train_ms: float
    total_pbs_ms: float
    total_submit_ms: float
    total_phase3_ms: float
    total_wall_ms: float

    # Porcentajes (suman ~100% del wall time observado)
    pct_train: float = Field(description="% del wall time dedicado a entrenamiento local")
    pct_pbs: float = Field(description="% del wall time dedicado al protocolo PBS")
    pct_submit: float = Field(description="% del wall time dedicado a submit (Phase 2)")
    pct_phase3: float = Field(description="% del wall time dedicado a aggregate (Phase 3)")

    # Métricas clave del análisis de overhead
    pbs_overhead_pct: float = Field(
        description="Costo del anonimato como % del wall time total"
    )
    blockchain_overhead_pct: float = Field(
        description="Costo del blockchain (submit + aggregate) como % del wall time"
    )
    tally_hash_total_ms: float = Field(
        description="Suma del costo de Tally_Hash (verificabilidad on-chain)"
    )

    # ---- Desglose blockchain granular (cuánto pesa cada componente) ----
    # Submit RTT (Etapa 2, por cliente y ronda): costo de propose+endorse+broadcast.
    total_submit_rtt_ms: float = 0.0
    # Chaincode CPU = chaincode_total leader-only (rq + agg + tally).
    total_chaincode_cpu_ms: float = 0.0
    # Block commit latency = leader_phase3_wall - chaincode_total. Dominante
    # en HLF 2.5 con el block cut interval por defecto (~2 s).
    total_block_commit_ms: float = 0.0

    pct_submit_rtt: float = 0.0
    pct_chaincode_cpu: float = 0.0
    pct_block_commit: float = 0.0

    # ---- Métricas sin ruido de red, acumuladas en el experimento ----
    # Suma sobre todos los envíos (K clientes × R rondas; 80 para K=10, R=8):
    #   - submit_pbs_verify: decenas de ms por envío en pbs, exactamente 0 en
    #     none. Aísla el costo del esquema de anonimato.
    #   - sig_model_verify: verificación ECDSA de σ_model (anti front-running, P6).
    #   - chaincode_total: suma de los anteriores más state_write.
    total_submit_pbs_verify_ms: float = 0.0
    total_submit_sig_model_verify_ms: float = 0.0
    total_submit_chaincode_total_ms: float = 0.0
    total_query_global_model_ms: float = 0.0
    total_fetch_pubkey_rtt_ms: float = 0.0
    # Mean attempts cross-experimento. 1.0 = sin retries; >1.0 = MVCC contention.
    mean_submit_attempts: float = 1.0
    max_submit_attempts: int = 1


class ConvergenceRow(BaseModel):
    """Δaccuracy por ronda: detecta plateau o convergencia temprana."""

    round: int
    accuracy: float
    delta_accuracy: float = Field(
        description="accuracy[r] - accuracy[r-1] (0 en la primera ronda)"
    )
    loss: float
    delta_loss: float


class TimingsBundle(BaseModel):
    """Bundle completo de métricas temporales para GET /timings."""

    run_id: str
    per_round: list[RoundTimings]
    summary: TimingsSummary | None
    convergence: list[ConvergenceRow]
