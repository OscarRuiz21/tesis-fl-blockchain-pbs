// Espejos TypeScript de los schemas Pydantic del backend.

export interface CatalogResponse {
  models: string[];
  aggregations: string[];
  anonymityModes: string[];
  partitionStrategies: string[];
  datasetTypes: string[];
}

export interface ModelSpec {
  name: string;
  num_params: number;
  hyperparams: Record<string, unknown>;
}

export interface DatasetSpec {
  type: string;
  base_path: string;
  target_column: string;
  test_set_path: string;
}

export interface PartitionSpec {
  strategy: "iid" | "dirichlet";
  alpha?: number | null;
}

export interface FederationSpec {
  num_clients: number;
  num_rounds: number;
  aggregation: "fedavg" | "fedmedian" | "fedprox";
  anonymity: "pbs" | "none";
}

export interface TimeoutsSpec {
  t_train_seconds: number;
  t_round_seconds: number;
  t_experiment_seconds: number;
}

export interface OutputSpec {
  results_dir: string;
  per_client_csv: boolean;
  global_eval_csv: boolean;
  run_summary_json: boolean;
}

export interface ExperimentMeta {
  label: string;
  description: string;
  seed: number;
}

export interface ExperimentSpec {
  experiment: ExperimentMeta;
  model: ModelSpec;
  dataset: DatasetSpec;
  partition: PartitionSpec;
  federation: FederationSpec;
  timeouts: TimeoutsSpec;
  output: OutputSpec;
}

export type RunState =
  | "pending"
  | "preparing"
  | "starting_infra"
  | "running"
  | "done"
  | "failed"
  | "aborted";

export interface RunRecord {
  run_id: string;
  spec_label: string;
  spec_path: string;
  state: RunState;
  queued_at: string;
  started_at: string | null;
  finished_at: string | null;
  final_accuracy: number | null;
  final_loss: number | null;
  total_seconds: number | null;
  error_message: string | null;
  log_file: string | null;
}

export interface ValidationError {
  field: string;
  message: string;
}

export interface ValidateResponse {
  valid: boolean;
  errors: ValidationError[];
}

export interface GlobalEvalRow {
  round: number;
  accuracy: number;
  loss: number;
  eval_time_ms?: number;
  timestamp?: string | null;
}

export interface ClientMetricRow {
  round: number;
  accuracy: number;
  loss: number;
  num_samples?: number;
  elapsed_ms?: number;
}

export interface RunSummary {
  experimentLabel: string;
  k: number;
  totalRounds: number;
  strategy: string;
  anonymityMode: string;
  seed: number;
  startedAt: string;
  finishedAt: string;
  totalSeconds: number;
  roundsCompleted: number;
  finalAccuracy: number | null;
  finalLoss: number | null;
}

export interface RunMetrics {
  run_id: string;
  summary: RunSummary | null;
  global_evals: GlobalEvalRow[];
  per_client: Record<string, ClientMetricRow[]>;
}

export interface ExperimentListItem {
  label: string;
  path: string;
  modified: string;
  raw: Record<string, unknown>;
}

export interface ComparePreset {
  key: string;
  label: string;
  description: string;
  metric_default: string;
}

// ---------- Timings (análisis de overhead PBS + blockchain) ----------

export interface RoundTimings {
  round: number;
  k_clients: number;
  train_ms_avg: number;
  train_ms_min: number;
  train_ms_max: number;
  pbs_ms_avg: number;
  pbs_ms_min: number;
  pbs_ms_max: number;
  submit_ms_avg: number;
  submit_ms_min: number;
  submit_ms_max: number;
  phase3_ms_avg: number;
  phase3_ms_max: number;
  chaincode_total_ms_avg: number;
  chaincode_aggregation_ms_avg: number;
  chaincode_tally_ms_avg: number;
  chaincode_range_query_ms_avg: number;
  leader_phase3_wall_ms: number;
  block_commit_ms_avg: number;
  // Métricas medidas dentro del chaincode, sin ruido de red
  submit_pbs_verify_ms_avg: number;
  submit_sig_model_verify_ms_avg: number;
  submit_chaincode_total_ms_avg: number;
  query_global_model_ms_avg: number;
  fetch_pubkey_rtt_ms_avg: number;
  submit_attempts_avg: number;
  submit_attempts_max: number;
  round_wall_ms_avg: number;
  round_wall_ms_max: number;
}

export interface TimingsSummary {
  rounds_observed: number;
  k_clients: number;
  total_train_ms: number;
  total_pbs_ms: number;
  total_submit_ms: number;
  total_phase3_ms: number;
  total_wall_ms: number;
  pct_train: number;
  pct_pbs: number;
  pct_submit: number;
  pct_phase3: number;
  pbs_overhead_pct: number;
  blockchain_overhead_pct: number;
  tally_hash_total_ms: number;
  // Desglose blockchain granular
  total_submit_rtt_ms: number;
  total_chaincode_cpu_ms: number;
  total_block_commit_ms: number;
  pct_submit_rtt: number;
  pct_chaincode_cpu: number;
  pct_block_commit: number;
  // Métricas medidas dentro del chaincode, sin ruido de red
  total_submit_pbs_verify_ms: number;
  total_submit_sig_model_verify_ms: number;
  total_submit_chaincode_total_ms: number;
  total_query_global_model_ms: number;
  total_fetch_pubkey_rtt_ms: number;
  mean_submit_attempts: number;
  max_submit_attempts: number;
}

export interface ConvergenceRow {
  round: number;
  accuracy: number;
  delta_accuracy: number;
  loss: number;
  delta_loss: number;
}

export interface TimingsBundle {
  run_id: string;
  per_round: RoundTimings[];
  summary: TimingsSummary | null;
  convergence: ConvergenceRow[];
}
