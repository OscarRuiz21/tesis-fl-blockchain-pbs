"use client";
import { useState } from "react";
import useSWR from "swr";
import { Modal } from "@/components/Modal";
import { api } from "@/lib/api";
import { Card } from "@/components/ui/Card";
import { Button } from "@/components/ui/Button";

export function CompareModal({
  open,
  onClose,
}: {
  open: boolean;
  onClose: () => void;
}) {
  const { data: runs } = useSWR(open ? "runs:list" : null, () =>
    api.listRuns(),
  );
  const { data: presets } = useSWR(open ? "compare:presets" : null, () =>
    api.comparePresets(),
  );
  const [selected, setSelected] = useState<string[]>([]);
  const [metric, setMetric] = useState<"accuracy" | "loss">("accuracy");
  const [result, setResult] = useState<
    Awaited<ReturnType<typeof api.compare>> | null
  >(null);

  const doneRuns = (runs ?? []).filter((r) => r.state === "done");

  function toggle(id: string) {
    setSelected((s) =>
      s.includes(id) ? s.filter((x) => x !== id) : [...s, id],
    );
  }

  async function doCompare() {
    const r = await api.compare(selected, metric);
    setResult(r);
  }

  async function exportPdf() {
    const r = await fetch(api.compareExportPdfUrl(), {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ run_ids: selected, metric }),
    });
    const blob = await r.blob();
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = "comparison.pdf";
    a.click();
    URL.revokeObjectURL(url);
  }

  return (
    <Modal
      open={open}
      onClose={onClose}
      title="Comparar experimentos"
      size="xl"
    >
      <div className="space-y-4">
        {presets && presets.length > 0 && (
          <Card title="Comparaciones predefinidas">
            <div className="grid grid-cols-3 gap-2">
              {presets.map((p) => (
                <div key={p.key} className="text-xs border rounded p-3">
                  <div className="font-semibold">{p.label}</div>
                  <div className="text-slate-500 mt-1">{p.description}</div>
                </div>
              ))}
            </div>
          </Card>
        )}

        <Card title={`Selecciona corridas (${selected.length} elegidas)`}>
          <div className="flex items-center gap-3 mb-3">
            <label className="text-xs font-semibold">Métrica:</label>
            <select
              className="border rounded px-2 py-1 text-sm"
              value={metric}
              onChange={(e) =>
                setMetric(e.target.value as "accuracy" | "loss")
              }
            >
              <option value="accuracy">exactitud</option>
              <option value="loss">pérdida</option>
            </select>
            <Button onClick={doCompare} disabled={selected.length < 2}>
              Comparar ({selected.length})
            </Button>
            {result && (
              <Button variant="secondary" onClick={exportPdf}>
                Exportar PDF
              </Button>
            )}
          </div>

          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead className="text-xs text-slate-500 border-b">
                <tr>
                  <th className="text-left py-2">✓</th>
                  <th className="text-left">Experimento</th>
                  <th className="text-left">Exactitud final</th>
                  <th className="text-left">Pérdida final</th>
                  <th className="text-left">Total (s)</th>
                </tr>
              </thead>
              <tbody>
                {doneRuns.map((r) => (
                  <tr key={r.run_id} className="border-b last:border-0">
                    <td>
                      <input
                        type="checkbox"
                        checked={selected.includes(r.run_id)}
                        onChange={() => toggle(r.run_id)}
                      />
                    </td>
                    <td className="py-2 font-mono text-xs">{r.spec_label}</td>
                    <td>{r.final_accuracy?.toFixed(4) ?? "—"}</td>
                    <td>{r.final_loss?.toFixed(4) ?? "—"}</td>
                    <td>{r.total_seconds?.toFixed(1) ?? "—"}</td>
                  </tr>
                ))}
                {doneRuns.length === 0 && (
                  <tr>
                    <td
                      colSpan={5}
                      className="py-4 text-slate-500 text-sm text-center"
                    >
                      No hay runs terminados para comparar.
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>
        </Card>

        {result && (
          <>
            <Card title={`Gráfica superpuesta: ${metric === "accuracy" ? "exactitud" : "pérdida"}`}>
              <img
                key={selected.join(",")}
                src={`${api.compareChartPngUrl(selected, metric)}&cb=${Date.now()}`}
                alt="comparison"
                className="w-full"
              />
            </Card>
            <Card title="Tabla resumen">
              <div className="overflow-x-auto">
                <table className="w-full text-xs">
                  <thead className="border-b text-slate-500">
                    <tr>
                      {Object.keys(result.rows[0] ?? {}).map((k) => (
                        <th key={k} className="text-left py-1">
                          {k}
                        </th>
                      ))}
                    </tr>
                  </thead>
                  <tbody>
                    {result.rows.map((r, i) => (
                      <tr key={i} className="border-b last:border-0">
                        {Object.values(r).map((v, j) => (
                          <td key={j} className="py-1">
                            {String(v)}
                          </td>
                        ))}
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </Card>
          </>
        )}
      </div>
    </Modal>
  );
}
