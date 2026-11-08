"use client";
import useSWR from "swr";
import clsx from "clsx";
import { useState } from "react";
import { api } from "@/lib/api";
import type { RunRecord } from "@/lib/types";
import { Card } from "@/components/ui/Card";
import { MetricsCharts } from "@/components/MetricsCharts";
import { TimingsPanel } from "@/components/TimingsPanel";
import { ConvergenceCard } from "@/components/ConvergenceCard";

const STATE_DESCRIPTIONS: Record<string, string> = {
  pending: "En cola, esperando turno",
  preparing: "Validando el spec con el coordinator",
  starting_infra: "Preparando la infraestructura (AS y K clientes)",
  running: "Rondas de aprendizaje federado en curso",
};

type Tab = "metrics" | "timings";

const TABS: [Tab, string][] = [
  ["metrics", "Métricas de entrenamiento"],
  ["timings", "Métricas de tiempos"],
];

export function RunDetail({ run }: { run: RunRecord }) {
  const [tab, setTab] = useState<Tab>("metrics");

  const { data: metrics } = useSWR(
    `metrics:${run.run_id}`,
    () => api.getMetrics(run.run_id),
    { refreshInterval: 3000 },
  );
  const { data: timings } = useSWR(
    `timings:${run.run_id}`,
    () => api.getTimings(run.run_id),
    { refreshInterval: 5000 },
  );

  const isActive = !["done", "failed", "aborted"].includes(run.state);

  return (
    <div className="flex flex-col h-full">
      <div className="px-5 py-3 bg-white border-b border-slate-200 grid grid-cols-2 md:grid-cols-4 gap-3 shrink-0">
        <Kpi
          label="Exactitud final"
          value={run.final_accuracy?.toFixed(4) ?? "—"}
        />
        <Kpi
          label="Pérdida final"
          value={run.final_loss?.toFixed(4) ?? "—"}
        />
        <Kpi
          label="Tiempo total (s)"
          value={run.total_seconds?.toFixed(1) ?? "—"}
        />
        <Kpi
          label="Id de corrida"
          value={run.run_id.slice(0, 8)}
          mono
          small
        />
      </div>

      {isActive && (
        <div className="border-l-4 border-blue-500 bg-blue-50 px-4 py-2 flex items-center gap-3 shrink-0">
          <span className="inline-block w-2 h-2 rounded-full bg-blue-500 animate-pulse" />
          <div className="text-sm flex-1">
            <strong className="text-blue-900">
              {STATE_DESCRIPTIONS[run.state] ?? run.state}
            </strong>
            <span className="text-blue-700 ml-2">
              · actualización automática
            </span>
          </div>
        </div>
      )}

      <div className="px-5 bg-white border-b border-slate-200 flex gap-1 shrink-0">
        {TABS.map(([t, label]) => (
          <button
            key={t}
            onClick={() => setTab(t)}
            className={clsx(
              "px-3 py-2.5 text-sm font-medium border-b-2 -mb-px transition-colors",
              tab === t
                ? "border-slate-900 text-slate-900"
                : "border-transparent text-slate-500 hover:text-slate-800",
            )}
          >
            {label}
          </button>
        ))}
      </div>

      <div className="flex-1 overflow-auto p-5 bg-slate-50">
        {tab === "metrics" && (
          <div className="space-y-4">
            {metrics && metrics.global_evals.length > 0 ? (
              <MetricsCharts metrics={metrics} />
            ) : (
              <Card title="Métricas de entrenamiento">
                <div className="text-sm text-slate-500 py-8 text-center">
                  {isActive
                    ? "Esperando la evaluación de la primera ronda…"
                    : "Sin evaluaciones globales registradas."}
                </div>
              </Card>
            )}
            {timings && <ConvergenceCard rows={timings.convergence} />}
          </div>
        )}

        {tab === "timings" &&
          (timings ? (
            <TimingsPanel bundle={timings} />
          ) : (
            <Card title="Métricas de tiempos">
              <div className="text-sm text-slate-500 py-6 text-center">
                Cargando tiempos…
              </div>
            </Card>
          ))}

        {run.error_message && (
          <Card title="Error" className="mt-4">
            <pre className="text-xs text-red-700 whitespace-pre-wrap">
              {run.error_message}
            </pre>
          </Card>
        )}
      </div>
    </div>
  );
}

function Kpi({
  label,
  value,
  mono,
  small,
}: {
  label: string;
  value: string;
  mono?: boolean;
  small?: boolean;
}) {
  return (
    <div className="bg-slate-50 rounded px-3 py-1.5">
      <div className="text-[10px] text-slate-500 uppercase tracking-wide">
        {label}
      </div>
      <div
        className={clsx(
          "font-bold mt-0.5",
          mono && "font-mono",
          small ? "text-sm" : "text-xl",
        )}
      >
        {value}
      </div>
    </div>
  );
}
