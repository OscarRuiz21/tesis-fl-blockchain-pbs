"use client";
import dynamic from "next/dynamic";
import type { ConvergenceRow } from "@/lib/types";
import { Card } from "@/components/ui/Card";

const Plot = dynamic(() => import("react-plotly.js"), {
  ssr: false,
  loading: () => <div className="text-xs text-slate-500">Cargando…</div>,
});

export function ConvergenceCard({ rows }: { rows: ConvergenceRow[] }) {
  if (!rows || rows.length === 0) return null;

  return (
    <Card title="Convergencia (Δ exactitud por ronda)">
      <Plot
        data={[
          {
            x: rows.map((c) => c.round),
            y: rows.map((c) => c.delta_accuracy),
            type: "bar",
            name: "Δacc",
            marker: {
              color: rows.map((c) =>
                c.delta_accuracy >= 0 ? "#16a34a" : "#dc2626",
              ),
            },
            hovertemplate: "Ronda %{x}<br>Δacc: %{y:+.4f}<extra></extra>",
          },
        ] as never}
        layout={{
          autosize: true,
          yaxis: {
            title: { text: "Δ exactitud" },
            gridcolor: "#e2e8f0",
            zeroline: true,
            zerolinecolor: "#94a3b8",
          },
          xaxis: {
            title: { text: "ronda" },
            dtick: 1,
            gridcolor: "#e2e8f0",
          },
          margin: { t: 10, l: 60, r: 20, b: 50 },
          plot_bgcolor: "#fafafa",
          paper_bgcolor: "white",
          font: { family: "system-ui, sans-serif", size: 12 },
          showlegend: false,
        }}
        useResizeHandler
        style={{ width: "100%", height: "260px" }}
        config={{ displaylogo: false, responsive: true }}
      />
      <p className="text-[11px] text-slate-600 mt-1">
        Verde = mejora · Rojo = retroceso. Cuando las barras se aplastan cerca
        de 0, el modelo llegó al plateau.
      </p>
    </Card>
  );
}
