import clsx from "clsx";
import type { RunState } from "@/lib/types";

const map: Record<RunState, string> = {
  pending: "bg-slate-200 text-slate-700",
  preparing: "bg-amber-100 text-amber-800",
  starting_infra: "bg-blue-100 text-blue-800",
  running: "bg-emerald-100 text-emerald-800",
  done: "bg-emerald-600 text-white",
  failed: "bg-red-600 text-white",
  aborted: "bg-slate-600 text-white",
};

const labels: Record<RunState, string> = {
  pending: "en cola",
  preparing: "preparando",
  starting_infra: "iniciando",
  running: "en curso",
  done: "terminada",
  failed: "falló",
  aborted: "abortada",
};

export function StateBadge({ state }: { state: RunState }) {
  return (
    <span
      className={clsx(
        "inline-block px-2 py-0.5 rounded text-[11px] font-semibold uppercase",
        map[state],
      )}
    >
      {labels[state]}
    </span>
  );
}
