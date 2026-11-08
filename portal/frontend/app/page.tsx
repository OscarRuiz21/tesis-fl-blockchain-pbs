"use client";
import { Suspense, useCallback, useEffect, useMemo, useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import useSWR from "swr";
import { api } from "@/lib/api";
import { useToast } from "@/components/Toast";
import { TopBar } from "@/components/TopBar";
import { RunRail } from "@/components/RunRail";
import { RunDetail } from "@/components/RunDetail";
import { NewExperimentModal } from "@/components/modals/NewExperimentModal";
import { CompareModal } from "@/components/modals/CompareModal";
import type { ExperimentSpec } from "@/lib/types";

type ModalKind = "new" | "compare" | null;

const ACTIVE_STATES = ["pending", "preparing", "starting_infra", "running"];

function DashboardInner() {
  const router = useRouter();
  const sp = useSearchParams();
  const toast = useToast();

  // URL state
  const selectedRunId = sp?.get("run") ?? null;
  const modalParam = sp?.get("modal") ?? null;
  const modal: ModalKind =
    modalParam === "new" || modalParam === "compare"
      ? (modalParam as ModalKind)
      : null;
  const editLabel = sp?.get("edit") ?? null;

  // Editing a spec: fetch the spec when modal=new&edit=<label>
  const [editingSpec, setEditingSpec] = useState<ExperimentSpec | null>(null);
  useEffect(() => {
    if (!editLabel || modal !== "new") {
      setEditingSpec(null);
      return;
    }
    api
      .getExperiment(editLabel)
      .then(setEditingSpec)
      .catch((e) =>
        toast.push({
          kind: "error",
          title: "No se pudo cargar spec",
          message: String(e),
        }),
      );
  }, [editLabel, modal, toast]);

  // Auto-refresh runs
  const { data: runs } = useSWR("runs:list", () => api.listRuns(), {
    refreshInterval: 3000,
  });

  const updateParams = useCallback(
    (patch: Record<string, string | null>) => {
      const next = new URLSearchParams(sp?.toString() ?? "");
      for (const [k, v] of Object.entries(patch)) {
        if (v === null || v === "") next.delete(k);
        else next.set(k, v);
      }
      const qs = next.toString();
      router.replace(qs ? `/?${qs}` : "/", { scroll: false });
    },
    [router, sp],
  );

  // Auto-select latest run if none chosen (prefer active, fallback to first)
  useEffect(() => {
    if (selectedRunId || !runs || runs.length === 0) return;
    const active = runs.find((r) => ACTIVE_STATES.includes(r.state));
    const latest = active ?? runs[0];
    if (latest) updateParams({ run: latest.run_id });
  }, [selectedRunId, runs, updateParams]);

  const selectedRun = useMemo(
    () => (runs ?? []).find((r) => r.run_id === selectedRunId) ?? null,
    [runs, selectedRunId],
  );

  const openModal = (m: ModalKind) => updateParams({ modal: m });
  const closeModal = () => updateParams({ modal: null, edit: null });
  const selectRun = (id: string) => updateParams({ run: id });

  async function launchSpec(label: string) {
    const loadingId = toast.push({
      kind: "loading",
      title: `Encolando ${label}…`,
      message: "Validando el spec y preparando la infraestructura",
      durationMs: 0,
    });
    try {
      const run = await api.enqueueRun(label);
      toast.update(loadingId, {
        kind: "success",
        title: "Experimento lanzado",
        message: `run_id: ${run.run_id.slice(0, 8)}…`,
        durationMs: 5000,
      });
      selectRun(run.run_id);
    } catch (e) {
      toast.update(loadingId, {
        kind: "error",
        title: "No se pudo lanzar",
        message: String(e),
        durationMs: 8000,
      });
    }
  }

  function openSpec(label: string) {
    updateParams({ modal: "new", edit: label });
  }

  function openNew() {
    updateParams({ modal: "new", edit: null });
  }

  async function abortRun() {
    if (!selectedRun) return;
    if (!confirm("¿Abortar el run en curso?")) return;
    try {
      await api.abortRun(selectedRun.run_id);
      toast.push({
        kind: "info",
        title: "Abort enviado",
        message: selectedRun.spec_label,
      });
    } catch (e) {
      toast.push({
        kind: "error",
        title: "No se pudo abortar",
        message: String(e),
      });
    }
  }

  function exportPdf() {
    if (!selectedRun) return;
    window.open(api.exportPdfUrl(selectedRun.run_id), "_blank");
  }

  return (
    <div className="flex flex-col h-screen overflow-hidden bg-slate-100">
      <TopBar
        selectedRun={selectedRun}
        onNew={openNew}
        onCompare={() => openModal("compare")}
        onAbort={abortRun}
        onExportPdf={exportPdf}
      />

      <div className="flex flex-1 overflow-hidden">
        <main className="flex-1 overflow-hidden">
          {selectedRun ? (
            <RunDetail key={selectedRun.run_id} run={selectedRun} />
          ) : (
            <EmptyState onNew={openNew} hasRuns={(runs?.length ?? 0) > 0} />
          )}
        </main>
        <RunRail
          selectedRunId={selectedRunId}
          onSelectRun={selectRun}
          onLaunchSpec={launchSpec}
          onOpenSpec={openSpec}
          onNewSpec={openNew}
        />
      </div>

      <NewExperimentModal
        open={modal === "new"}
        onClose={closeModal}
        initial={editLabel ? editingSpec ?? undefined : undefined}
        title={editLabel ? `Editar: ${editLabel}` : "Nuevo experimento"}
        onSaved={(label) =>
          toast.push({
            kind: "success",
            title: "Spec guardada",
            message: label,
          })
        }
        onLaunched={(runId) => selectRun(runId)}
      />
      <CompareModal open={modal === "compare"} onClose={closeModal} />
    </div>
  );
}

function EmptyState({
  onNew,
  hasRuns,
}: {
  onNew: () => void;
  hasRuns: boolean;
}) {
  return (
    <div className="flex items-center justify-center h-full text-center bg-slate-50">
      <div className="max-w-md p-6">
        <div className="text-6xl mb-4 opacity-30">🧪</div>
        <h2 className="text-xl font-semibold text-slate-700">
          {hasRuns ? "Selecciona una corrida de la derecha" : "Todavía no hay corridas"}
        </h2>
        <p className="text-sm text-slate-500 mt-2">
          {hasRuns
            ? "Haz clic en una corrida de la lista de la derecha para ver sus métricas y tiempos."
            : "Crea el primer spec con el asistente y lánzalo. La lista de la derecha mostrará todas las corridas."}
        </p>
        {!hasRuns && (
          <button
            onClick={onNew}
            className="mt-5 px-4 py-2 bg-slate-900 text-white rounded text-sm font-medium hover:bg-slate-700"
          >
            + Crear primer experimento
          </button>
        )}
      </div>
    </div>
  );
}

export default function DashboardPage() {
  return (
    <Suspense fallback={<div className="p-6 text-slate-500">Cargando…</div>}>
      <DashboardInner />
    </Suspense>
  );
}
