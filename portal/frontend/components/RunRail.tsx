"use client";
import { useState } from "react";
import useSWR from "swr";
import clsx from "clsx";
import { api } from "@/lib/api";
import type { ExperimentListItem, RunRecord } from "@/lib/types";
import { StateBadge } from "@/components/ui/StateBadge";

type Filter = "all" | "active" | "done" | "failed";

const FILTERS: Filter[] = ["all", "active", "done", "failed"];
const FILTER_LABELS: Record<Filter, string> = {
  all: "Todas",
  active: "Activas",
  done: "Terminadas",
  failed: "Fallidas",
};

export function RunRail({
  selectedRunId,
  onSelectRun,
  onLaunchSpec,
  onOpenSpec,
  onNewSpec,
}: {
  selectedRunId: string | null;
  onSelectRun: (id: string) => void;
  onLaunchSpec: (label: string) => void;
  onOpenSpec: (label: string) => void;
  onNewSpec: () => void;
}) {
  const { data: runs, mutate: mutateRuns } = useSWR(
    "runs:list",
    () => api.listRuns(),
    { refreshInterval: 3000 },
  );
  const { data: specs, mutate: mutateSpecs } = useSWR(
    "specs:list",
    () => api.listExperiments(),
    { refreshInterval: 5000 },
  );

  const [filter, setFilter] = useState<Filter>("all");
  const [query, setQuery] = useState("");

  const filtered = (runs ?? [])
    .filter((r) => {
      if (filter === "active")
        return [
          "pending",
          "preparing",
          "starting_infra",
          "running",
        ].includes(r.state);
      if (filter === "done") return r.state === "done";
      if (filter === "failed")
        return ["failed", "aborted"].includes(r.state);
      return true;
    })
    .filter((r) =>
      query
        ? r.spec_label.toLowerCase().includes(query.toLowerCase()) ||
          r.run_id.includes(query)
        : true,
    );

  async function deleteRun(id: string) {
    if (!confirm("¿Eliminar esta corrida?")) return;
    await api.deleteRun(id);
    if (id === selectedRunId) {
      const remaining = (runs ?? []).filter((r) => r.run_id !== id);
      if (remaining[0]) onSelectRun(remaining[0].run_id);
    }
    mutateRuns();
  }

  async function deleteSpec(label: string) {
    if (!confirm(`¿Eliminar spec "${label}"?`)) return;
    await api.deleteExperiment(label);
    mutateSpecs();
  }

  return (
    <aside className="w-80 shrink-0 border-l border-slate-200 bg-slate-50 flex flex-col h-full">
      <div className="p-3 border-b border-slate-200 bg-white shrink-0">
        <input
          type="text"
          placeholder="Filtrar corridas..."
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          className="w-full px-2 py-1.5 text-xs border border-slate-200 rounded bg-white focus:outline-none focus:ring-1 focus:ring-slate-400"
        />
        <div className="flex gap-1 mt-2">
          {FILTERS.map((f) => (
            <button
              key={f}
              onClick={() => setFilter(f)}
              className={clsx(
                "px-2 py-0.5 rounded text-[10px] font-medium uppercase tracking-wide flex-1",
                filter === f
                  ? "bg-slate-900 text-white"
                  : "bg-slate-100 text-slate-600 hover:bg-slate-200",
              )}
            >
              {FILTER_LABELS[f]}
            </button>
          ))}
        </div>
      </div>

      <div className="flex-1 overflow-auto">
        <div className="px-3 py-2 text-[10px] uppercase tracking-wider text-slate-500 font-semibold sticky top-0 bg-slate-50 border-b border-slate-200 z-10">
          Corridas ({filtered.length})
        </div>
        {filtered.length === 0 && (
          <div className="px-3 py-4 text-xs text-slate-500 italic">
            Sin runs con este filtro.
          </div>
        )}
        {filtered.map((r) => (
          <RunCard
            key={r.run_id}
            run={r}
            selected={r.run_id === selectedRunId}
            onSelect={() => onSelectRun(r.run_id)}
            onDelete={() => deleteRun(r.run_id)}
          />
        ))}
      </div>

      <div className="border-t border-slate-200 bg-white shrink-0 max-h-72 overflow-auto">
        <div className="px-3 py-2 text-[10px] uppercase tracking-wider text-slate-500 font-semibold border-b border-slate-200 flex justify-between items-center sticky top-0 bg-white z-10">
          <span>Experimentos ({specs?.length ?? 0})</span>
          <button
            onClick={onNewSpec}
            className="text-[10px] text-slate-600 hover:text-slate-900 font-bold"
            title="Nuevo experimento"
          >
            + Nuevo
          </button>
        </div>
        {(specs ?? []).length === 0 && (
          <div className="px-3 py-3 text-xs text-slate-500 italic">
            Sin specs. Crea uno con + Nuevo.
          </div>
        )}
        {(specs ?? []).map((s) => (
          <SpecCard
            key={s.label}
            spec={s}
            onLaunch={() => onLaunchSpec(s.label)}
            onOpen={() => onOpenSpec(s.label)}
            onDelete={() => deleteSpec(s.label)}
          />
        ))}
      </div>
    </aside>
  );
}

function RunCard({
  run,
  selected,
  onSelect,
  onDelete,
}: {
  run: RunRecord;
  selected: boolean;
  onSelect: () => void;
  onDelete: () => void;
}) {
  const isActive = !["done", "failed", "aborted"].includes(run.state);
  return (
    <div
      onClick={onSelect}
      className={clsx(
        "px-3 py-2 cursor-pointer border-l-4 group transition-colors",
        selected
          ? "bg-blue-50 border-blue-500"
          : "border-transparent hover:bg-slate-100",
      )}
    >
      <div className="flex items-start gap-2">
        <span
          className={clsx(
            "w-1.5 h-1.5 rounded-full mt-1.5 shrink-0",
            isActive
              ? "bg-emerald-500 animate-pulse"
              : run.state === "failed" || run.state === "aborted"
                ? "bg-red-400"
                : "bg-slate-300",
          )}
        />
        <div className="flex-1 min-w-0">
          <div className="text-xs font-mono text-slate-900 truncate">
            {run.spec_label}
          </div>
          <div className="flex items-center gap-2 mt-1">
            <StateBadge state={run.state} />
            {run.final_accuracy != null && (
              <span className="text-[10px] text-slate-600">
                exactitud {run.final_accuracy.toFixed(3)}
              </span>
            )}
            {run.total_seconds != null && (
              <span className="text-[10px] text-slate-500">
                · {run.total_seconds.toFixed(0)}s
              </span>
            )}
          </div>
          <div className="text-[10px] text-slate-400 mt-0.5">
            {run.queued_at.slice(5, 16).replace("T", " ")}
          </div>
        </div>
        {!isActive && (
          <button
            onClick={(e) => {
              e.stopPropagation();
              onDelete();
            }}
            className="opacity-0 group-hover:opacity-100 text-slate-400 hover:text-red-600 text-xs px-1"
            title="Eliminar"
          >
            ✕
          </button>
        )}
      </div>
    </div>
  );
}

function SpecCard({
  spec,
  onLaunch,
  onOpen,
  onDelete,
}: {
  spec: ExperimentListItem;
  onLaunch: () => void;
  onOpen: () => void;
  onDelete: () => void;
}) {
  return (
    <div className="px-3 py-1.5 border-b border-slate-100 last:border-0 group hover:bg-slate-50">
      <div className="flex items-center justify-between gap-2">
        <button
          onClick={onOpen}
          className="text-xs font-mono text-slate-800 truncate flex-1 text-left hover:underline"
          title="Ver experimento"
        >
          {spec.label}
        </button>
        <div className="flex gap-1 shrink-0">
          <button
            onClick={onLaunch}
            className="text-[10px] px-2 py-0.5 rounded bg-emerald-600 text-white hover:bg-emerald-700 font-semibold"
            title="Lanzar"
          >
            ▶
          </button>
          <button
            onClick={onDelete}
            className="text-[10px] px-1.5 py-0.5 text-slate-400 opacity-0 group-hover:opacity-100 hover:text-red-600"
            title="Eliminar experimento"
          >
            🗑
          </button>
        </div>
      </div>
    </div>
  );
}
