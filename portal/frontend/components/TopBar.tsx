"use client";
import type { RunRecord } from "@/lib/types";
import { StateBadge } from "@/components/ui/StateBadge";
import { Button } from "@/components/ui/Button";

export function TopBar({
  selectedRun,
  onNew,
  onCompare,
  onAbort,
  onExportPdf,
}: {
  selectedRun: RunRecord | null;
  onNew: () => void;
  onCompare: () => void;
  onAbort?: () => void;
  onExportPdf?: () => void;
}) {
  const isActive = selectedRun
    ? !["done", "failed", "aborted"].includes(selectedRun.state)
    : false;

  return (
    <header className="bg-white border-b border-slate-200 px-5 py-2.5 flex items-center justify-between gap-4 shrink-0">
      <div className="flex items-center gap-3 min-w-0">
        <div className="flex items-baseline gap-1.5 shrink-0">
          <div className="font-bold text-slate-900">FL Portal</div>
          <div className="text-[10px] text-slate-400 uppercase tracking-wider">
            Tesis UNAM
          </div>
        </div>
        {selectedRun && (
          <>
            <div className="text-slate-300">·</div>
            <div className="font-mono text-sm text-slate-700 truncate max-w-md">
              {selectedRun.spec_label}
            </div>
            <StateBadge state={selectedRun.state} />
          </>
        )}
      </div>
      <div className="flex items-center gap-2 shrink-0">
        {isActive && onAbort && (
          <Button variant="danger" onClick={onAbort}>
            Abortar
          </Button>
        )}
        {selectedRun?.state === "done" && onExportPdf && (
          <Button variant="ghost" onClick={onExportPdf}>
            Exportar PDF
          </Button>
        )}
        <div className="w-px h-6 bg-slate-200 mx-1" />
        <Button onClick={onNew}>+ Nuevo</Button>
        <Button variant="secondary" onClick={onCompare}>
          ⚖ Comparar
        </Button>
      </div>
    </header>
  );
}
