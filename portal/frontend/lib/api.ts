// Cliente HTTP del portal: usa fetch nativo + SWR para caching.

import type {
  CatalogResponse,
  ComparePreset,
  ExperimentListItem,
  ExperimentSpec,
  RunMetrics,
  RunRecord,
  TimingsBundle,
  ValidateResponse,
} from "@/lib/types";

// El backend está expuesto en host:8000. CORS configurado en backend para
// permitir http://localhost:3000. Usar URL absoluta evita el proxy de Next.js
// (que requeriría NEXT_PUBLIC_API_URL en build-time).
const API_BASE =
  process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8000";
const BASE = `${API_BASE}/api`;

async function json<T>(path: string, init?: RequestInit): Promise<T> {
  const r = await fetch(`${BASE}${path}`, {
    headers: { "Content-Type": "application/json" },
    cache: "no-store",
    ...init,
  });
  if (!r.ok) {
    const body = await r.text().catch(() => "");
    throw new Error(`HTTP ${r.status} on ${path}: ${body}`);
  }
  if (r.status === 204) return null as T;
  return (await r.json()) as T;
}

export const api = {
  // Catalog
  getCatalog: () => json<CatalogResponse>("/catalog"),

  // Experiments
  listExperiments: () => json<ExperimentListItem[]>("/experiments"),
  getExperiment: (label: string) =>
    json<ExperimentSpec>(`/experiments/${encodeURIComponent(label)}`),
  createExperiment: (spec: ExperimentSpec) =>
    json<ExperimentSpec>("/experiments", {
      method: "POST",
      body: JSON.stringify(spec),
    }),
  updateExperiment: (label: string, spec: ExperimentSpec) =>
    json<ExperimentSpec>(`/experiments/${encodeURIComponent(label)}`, {
      method: "PUT",
      body: JSON.stringify(spec),
    }),
  deleteExperiment: (label: string) =>
    json<void>(`/experiments/${encodeURIComponent(label)}`, { method: "DELETE" }),
  validateInline: (spec: ExperimentSpec) =>
    json<ValidateResponse>("/experiments/validate-inline", {
      method: "POST",
      body: JSON.stringify(spec),
    }),

  // Runs
  listRuns: () => json<RunRecord[]>("/runs"),
  getActiveRun: () => json<RunRecord | null>("/runs/active"),
  getRun: (id: string) => json<RunRecord>(`/runs/${id}`),
  enqueueRun: (spec_label: string) =>
    json<RunRecord>("/runs", {
      method: "POST",
      body: JSON.stringify({ spec_label }),
    }),
  abortRun: (id: string) =>
    json<{ aborted: boolean }>(`/runs/${id}/abort`, { method: "POST" }),
  deleteRun: (id: string) => json<void>(`/runs/${id}`, { method: "DELETE" }),

  // Metrics + charts
  getMetrics: (id: string) => json<RunMetrics>(`/runs/${id}/metrics`),
  chartPngUrl: (id: string, kind: string) =>
    `${BASE}/runs/${id}/charts/${kind}.png`,
  exportPdfUrl: (id: string) => `${BASE}/runs/${id}/export.pdf`,

  // Timings
  getTimings: (id: string) => json<TimingsBundle>(`/runs/${id}/timings`),
  timingsStackedPngUrl: (id: string) => `${BASE}/runs/${id}/timings/stacked.png`,
  convergencePngUrl: (id: string) => `${BASE}/runs/${id}/timings/convergence.png`,

  // Logs
  logSnapshotUrl: (id: string) => `${BASE}/runs/${id}/logs/snapshot`,
  logStreamUrl: (id: string) => `${BASE}/runs/${id}/logs/stream`,

  // Compare
  comparePresets: () => json<ComparePreset[]>("/compare/presets"),
  compare: (run_ids: string[], metric = "accuracy") =>
    json<{ rows: Array<Record<string, unknown>>; metric: string; count: number }>(
      "/compare",
      { method: "POST", body: JSON.stringify({ run_ids, metric }) },
    ),
  compareChartPngUrl: (runIds: string[], metric = "accuracy") =>
    `${BASE}/compare/chart.png?run_ids=${runIds.join(",")}&metric=${metric}`,
  compareExportPdfUrl: () => `${BASE}/compare/export.pdf`,
};
