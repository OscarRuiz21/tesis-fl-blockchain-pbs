"use client";
import dynamic from "next/dynamic";
import type { RunMetrics } from "@/lib/types";
import { Card } from "@/components/ui/Card";

const Plot = dynamic(() => import("react-plotly.js"), {
  ssr: false,
  loading: () => (
    <div className="text-xs text-slate-500">Cargando gráfica…</div>
  ),
});

export function MetricsCharts({ metrics }: { metrics: RunMetrics }) {
  // Datos globales por ronda
  const rounds = metrics.global_evals.map((e) => e.round);
  const acc = metrics.global_evals.map((e) => e.accuracy);
  const loss = metrics.global_evals.map((e) => e.loss);

  // Auto-zoom: en vez de fijar [0,1], dejamos que Plotly haga auto-range con
  // un padding del 10% para que las pequeñas diferencias se vean bien.
  const accRange = autoRange(acc, 0.02);
  const lossRange = autoRange(loss, 0.02);

  // Mismo tratamiento para per-cliente: rango global
  const allClientAcc = Object.values(metrics.per_client).flatMap((rows) =>
    rows.map((r) => r.accuracy),
  );
  const perClientRange = autoRange(allClientAcc, 0.02);

  const sharedConfig = { displaylogo: false, responsive: true };
  const sharedLayout = {
    autosize: true,
    margin: { t: 30, l: 60, r: 20, b: 50 },
    plot_bgcolor: "#fafafa",
    paper_bgcolor: "white",
    font: { family: "system-ui, sans-serif", size: 12, color: "#334155" },
    showlegend: false,
    xaxis: { gridcolor: "#e2e8f0", zerolinecolor: "#cbd5e1" },
    yaxis: { gridcolor: "#e2e8f0", zerolinecolor: "#cbd5e1" },
  } as const;

  return (
    <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
      <Card title="Exactitud global por ronda">
        <Plot
          data={[
            {
              x: rounds,
              y: acc,
              type: "scatter",
              mode: "lines+markers",
              name: "exactitud",
              line: { color: "#0f766e", width: 2.5 },
              marker: { size: 8, color: "#0f766e" },
              fill: "tozeroy",
              fillcolor: "rgba(15, 118, 110, 0.08)",
              hovertemplate: "Ronda %{x}<br>Accuracy: %{y:.4f}<extra></extra>",
            },
          ]}
          layout={{
            ...sharedLayout,
            yaxis: {
              ...sharedLayout.yaxis,
              range: accRange,
              title: { text: "exactitud" },
              tickformat: ".3f",
            },
            xaxis: {
              ...sharedLayout.xaxis,
              title: { text: "ronda" },
              dtick: 1,
            },
          }}
          useResizeHandler
          style={{ width: "100%", height: "300px" }}
          config={sharedConfig}
        />
      </Card>

      <Card title="Pérdida global por ronda">
        <Plot
          data={[
            {
              x: rounds,
              y: loss,
              type: "scatter",
              mode: "lines+markers",
              name: "pérdida",
              line: { color: "#b91c1c", width: 2.5 },
              marker: { size: 8, color: "#b91c1c" },
              fill: "tozeroy",
              fillcolor: "rgba(185, 28, 28, 0.08)",
              hovertemplate: "Ronda %{x}<br>Loss: %{y:.4f}<extra></extra>",
            },
          ]}
          layout={{
            ...sharedLayout,
            yaxis: {
              ...sharedLayout.yaxis,
              range: lossRange,
              title: { text: "pérdida" },
              tickformat: ".4f",
            },
            xaxis: {
              ...sharedLayout.xaxis,
              title: { text: "ronda" },
              dtick: 1,
            },
          }}
          useResizeHandler
          style={{ width: "100%", height: "300px" }}
          config={sharedConfig}
        />
      </Card>

      <Card title="Exactitud por cliente y ronda" className="lg:col-span-2">
        {Object.keys(metrics.per_client).length === 0 ? (
          <div className="text-sm text-slate-500 py-8 text-center">
            Las métricas por cliente aparecen al terminar la corrida
            <div className="text-xs mt-1 opacity-70">
              (se leen de run_summary.json, que se escribe al final)
            </div>
          </div>
        ) : (
          <Plot
            data={Object.entries(metrics.per_client).map(([cid, rows], i) => ({
              x: rows.map((r) => r.round),
              y: rows.map((r) => r.accuracy),
              type: "scatter",
              mode: "lines+markers",
              name: cid,
              line: { width: 2, color: CLIENT_COLORS[i % CLIENT_COLORS.length] },
              marker: { size: 6 },
              hovertemplate: `${cid}<br>Ronda %{x}<br>Acc: %{y:.4f}<extra></extra>`,
            }))}
            layout={{
              ...sharedLayout,
              showlegend: true,
              legend: { orientation: "h", y: -0.18, x: 0.5, xanchor: "center" },
              yaxis: {
                ...sharedLayout.yaxis,
                range: perClientRange,
                title: { text: "exactitud local (entrenamiento)" },
                tickformat: ".3f",
              },
              xaxis: {
                ...sharedLayout.xaxis,
                title: { text: "ronda" },
                dtick: 1,
              },
              margin: { ...sharedLayout.margin, b: 80 },
            }}
            useResizeHandler
            style={{ width: "100%", height: "380px" }}
            config={sharedConfig}
          />
        )}
      </Card>
    </div>
  );
}

// Auto-zoom con padding proporcional al rango.
function autoRange(values: number[], padFrac = 0.05): [number, number] {
  if (values.length === 0) return [0, 1];
  const valid = values.filter((v) => Number.isFinite(v));
  if (valid.length === 0) return [0, 1];
  const min = Math.min(...valid);
  const max = Math.max(...valid);
  if (min === max) return [min - 0.05, max + 0.05];
  const pad = (max - min) * padFrac;
  return [min - pad, max + pad];
}

// Paleta cualitativa amigable (tailwind-ish).
const CLIENT_COLORS = [
  "#0f766e", // teal-700
  "#b91c1c", // red-700
  "#1d4ed8", // blue-700
  "#a16207", // amber-700
  "#7e22ce", // purple-700
  "#be185d", // pink-700
  "#0891b2", // cyan-600
  "#65a30d", // lime-600
];
