"use client";
import dynamic from "next/dynamic";
import type { TimingsBundle } from "@/lib/types";
import { Card } from "@/components/ui/Card";

const Plot = dynamic(() => import("react-plotly.js"), {
  ssr: false,
  loading: () => <div className="text-xs text-slate-500">Cargando…</div>,
});

function pct(numerator: number, denominator: number): number {
  if (!denominator || denominator <= 0) return 0;
  return (numerator / denominator) * 100;
}

const COLORS = {
  train: "#94a3b8",
  pbs: "#10b981",
  submit: "#3b82f6",
  phase3: "#ef4444",
};

export function TimingsPanel({ bundle }: { bundle: TimingsBundle }) {
  if (bundle.per_round.length === 0) {
    return (
      <Card title="Análisis de tiempos">
        <div className="text-sm text-slate-500 py-6 text-center">
          Los tiempos aparecen cuando termina la corrida
        </div>
      </Card>
    );
  }

  const rounds = bundle.per_round.map((r) => r.round);
  const trainS = bundle.per_round.map((r) => r.train_ms_avg / 1000);
  const pbsS = bundle.per_round.map((r) => r.pbs_ms_avg / 1000);
  const submitS = bundle.per_round.map((r) => r.submit_ms_avg / 1000);
  const phase3S = bundle.per_round.map((r) => r.phase3_ms_avg / 1000);

  const s = bundle.summary;

  return (
    <div className="space-y-4">
      {/* Verificación PBS chaincode-side */}
      {s && s.total_submit_pbs_verify_ms !== undefined && (
        <Card title="Verificación en el contrato">
          <div className="grid grid-cols-1 md:grid-cols-3 gap-3">
            <Stat
              label="Verificación PBS (total)"
              value={`${(s.total_submit_pbs_verify_ms / 1000).toFixed(2)} s / ${pct(s.total_submit_pbs_verify_ms, s.total_wall_ms).toFixed(2)} %`}
              hint={`${s.k_clients * s.rounds_observed} submits (K=${s.k_clients} × R=${s.rounds_observed})`}
              tone="emerald"
            />
            <Stat
              label="Verificación σ_model (total)"
              value={`${(s.total_submit_sig_model_verify_ms / 1000).toFixed(2)} s / ${pct(s.total_submit_sig_model_verify_ms, s.total_wall_ms).toFixed(2)} %`}
              hint="ECDSA verify de la firma del update"
              tone="emerald"
            />
            <Stat
              label="Contrato (total)"
              value={`${(s.total_submit_chaincode_total_ms / 1000).toFixed(2)} s / ${pct(s.total_submit_chaincode_total_ms, s.total_wall_ms).toFixed(2)} %`}
              hint="pbs_verify + sig_verify + state_write"
              tone="emerald"
            />
          </div>
          <p className="mt-3 text-[11px] text-slate-600 leading-relaxed">
            Tiempo medido dentro del chaincode al procesar cada submit. No incluye
            latencia de red ni del orderer. En modo <code>none</code> queda en 0.
            El porcentaje es relativo al tiempo de pared total.
          </p>
        </Card>
      )}

      {/* Wall-clock: la corrida completa, incluye todo (train + red + cripto + sleeps) */}
      {s && (
        <Card title="Tiempos de pared">
          <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
            <Stat
              label="PBS (cliente)"
              value={`${s.pbs_overhead_pct.toFixed(2)} %`}
              hint={`${(s.total_pbs_ms / 1000).toFixed(2)} s · blind + unblind + verify + 2 RTTs al AS`}
              tone="emerald"
            />
            <Stat
              label="Blockchain"
              value={`${s.blockchain_overhead_pct.toFixed(2)} %`}
              hint="RTT del envío + Etapa 3 (incluye el block commit del orderer)"
              tone="blue"
            />
            <Stat
              label="Entrenamiento"
              value={`${s.pct_train.toFixed(1)} %`}
              hint={`${(s.total_train_ms / 1000).toFixed(1)} s · entrenamiento local`}
              tone="slate"
            />
            <Stat
              label="Tally Hash"
              value={
                s.tally_hash_total_ms < 1
                  ? "<1 ms"
                  : `${s.tally_hash_total_ms.toFixed(0)} ms`
              }
              hint={
                s.tally_hash_total_ms < 1
                  ? "chaincode reporta en ms enteros; el cómputo real es sub-ms"
                  : "hash de los updates de la ronda"
              }
              tone="amber"
            />
          </div>
          <p className="mt-4 text-xs text-slate-600">
            Tiempo de pared total: <strong>{(s.total_wall_ms / 1000).toFixed(1)} s</strong> ·{" "}
            K={s.k_clients} clientes · {s.rounds_observed} rondas
          </p>
          <p className="mt-2 text-[11px] text-slate-500 leading-relaxed">
            El tiempo de pared suma todo lo que pasa en una ronda: entrenamiento local,
            RTT al AS, propose+endorse al orderer, sondeo de la Etapa 3 y cómputo
            del chaincode. Los porcentajes son relativos al tiempo de pared total observado.
          </p>
        </Card>
      )}

      {/* Tiempos auxiliares (red al AS, read de modelo) */}
      {s && (
        <Card title="Otros tiempos del experimento">
          <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
            <Stat
              label="Obtención de X_AS"
              value={
                s.total_fetch_pubkey_rtt_ms < 1
                  ? "0 ms"
                  : `${s.total_fetch_pubkey_rtt_ms.toFixed(0)} ms / ${pct(s.total_fetch_pubkey_rtt_ms, s.total_wall_ms).toFixed(2)} %`
              }
              hint="GET /blind-sign/public-key del AS. El cliente cachea el valor: cache miss en R1, hit en R2+"
              tone="blue"
            />
            <Stat
              label="Consulta del modelo global"
              value={`${(s.total_query_global_model_ms / 1000).toFixed(2)} s / ${pct(s.total_query_global_model_ms, s.total_wall_ms).toFixed(2)} %`}
              hint="read del modelo agregado de la ronda anterior, antes de entrenar"
              tone="blue"
            />
          </div>
        </Card>
      )}

      {/* Desglose blockchain granular */}
      {s && (
        <Card title="Desglose del costo de la cadena">
          <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
            <Stat
              label="Envío (Etapa 2)"
              value={`${(s.total_submit_rtt_ms / 1000).toFixed(2)} s`}
              hint={`${s.pct_submit_rtt.toFixed(1)} % del total · propuesta y endoso por cliente`}
              tone="blue"
            />
            <Stat
              label="Cómputo del contrato"
              value={
                s.total_chaincode_cpu_ms < 1000
                  ? `${s.total_chaincode_cpu_ms.toFixed(0)} ms`
                  : `${(s.total_chaincode_cpu_ms / 1000).toFixed(2)} s`
              }
              hint={`${s.pct_chaincode_cpu.toFixed(2)} % del total · lectura, agregación y Tally`}
              tone="emerald"
            />
            <Stat
              label="Confirmación del bloque"
              value={`${(s.total_block_commit_ms / 1000).toFixed(2)} s`}
              hint={`${s.pct_block_commit.toFixed(1)} % del total · orderer Raft y propagación`}
              tone="red"
            />
            <Stat
              label="Tally Hash"
              value={`${s.tally_hash_total_ms.toFixed(0)} ms`}
              hint={`incluido en chaincode CPU`}
              tone="amber"
            />
          </div>
          <p className="mt-3 text-[11px] text-slate-600 leading-relaxed">
            {s.pct_block_commit > s.pct_chaincode_cpu * 10 ? (
              <>
                La <strong>confirmación del bloque</strong> domina (
                {(s.total_block_commit_ms / 1000).toFixed(1)} s contra{" "}
                {s.total_chaincode_cpu_ms.toFixed(0)} ms del contrato). Es la latencia del orderer Raft: por omisión,
                Fabric 2.5 corta un bloque cada 2 s.
              </>
            ) : (
              <>
                Chaincode y block commit están en órdenes similares.
              </>
            )}
          </p>
        </Card>
      )}

      {/* Área apilada: tiempos por etapa y ronda */}
      <Card title="Desglose de tiempos por ronda (promedio de los K clientes)">
        <Plot
          data={[
            { x: rounds, y: trainS, name: "Entrenamiento local", stackgroup: "one",
              type: "scatter", mode: "lines", line: { width: 0.5, color: COLORS.train },
              fillcolor: COLORS.train, hovertemplate: "Ronda %{x}<br>Train: %{y:.2f} s<extra></extra>" },
            { x: rounds, y: pbsS, name: "Etapa 1: PBS", stackgroup: "one",
              type: "scatter", mode: "lines", line: { width: 0.5, color: COLORS.pbs },
              fillcolor: COLORS.pbs, hovertemplate: "Ronda %{x}<br>PBS: %{y:.3f} s<extra></extra>" },
            { x: rounds, y: submitS, name: "Etapa 2: envío", stackgroup: "one",
              type: "scatter", mode: "lines", line: { width: 0.5, color: COLORS.submit },
              fillcolor: COLORS.submit, hovertemplate: "Ronda %{x}<br>Submit: %{y:.3f} s<extra></extra>" },
            { x: rounds, y: phase3S, name: "Etapa 3: agregación", stackgroup: "one",
              type: "scatter", mode: "lines", line: { width: 0.5, color: COLORS.phase3 },
              fillcolor: COLORS.phase3, hovertemplate: "Ronda %{x}<br>Etapa 3: %{y:.3f} s<extra></extra>" },
          ] as never}
          layout={{
            autosize: true,
            yaxis: { title: { text: "segundos" }, gridcolor: "#e2e8f0" },
            xaxis: { title: { text: "ronda" }, dtick: 1, gridcolor: "#e2e8f0" },
            margin: { t: 10, l: 60, r: 20, b: 50 },
            plot_bgcolor: "#fafafa",
            paper_bgcolor: "white",
            font: { family: "system-ui, sans-serif", size: 12 },
            legend: { orientation: "h", y: -0.18, x: 0.5, xanchor: "center" },
            hovermode: "x unified",
          }}
          useResizeHandler
          style={{ width: "100%", height: "380px" }}
          config={{ displaylogo: false, responsive: true }}
        />
      </Card>

      {/* Tabla per-ronda */}
      <Card title="Detalle por ronda (ms)">
        <div className="overflow-x-auto">
          <table className="w-full text-xs">
            <thead className="text-[10px] text-slate-500 border-b">
              <tr>
                <th className="text-left py-2 px-2">R</th>
                <th className="text-right">Entrenamiento</th>
                <th className="text-right">PBS (cliente)</th>
                <th className="text-right text-emerald-700">Verif. PBS</th>
                <th className="text-right">Obtener X_AS</th>
                <th className="text-right">Consulta MG</th>
                <th className="text-right">Envío</th>
                <th className="text-right text-red-700">Confirmación</th>
                <th className="text-right">Pared prom. / máx.</th>
              </tr>
            </thead>
            <tbody>
              {bundle.per_round.map((r) => (
                <tr key={r.round} className="border-b last:border-0 font-mono">
                  <td className="py-1.5 px-2 font-sans font-semibold">{r.round}</td>
                  <td className="text-right">{r.train_ms_avg.toFixed(0)}</td>
                  <td className="text-right">
                    {r.pbs_ms_avg.toFixed(0)}
                    <span className="text-slate-400"> / {r.pbs_ms_max.toFixed(0)}</span>
                  </td>
                  <td className="text-right text-emerald-700 font-semibold">
                    {(r.submit_pbs_verify_ms_avg ?? 0).toFixed(1)}
                  </td>
                  <td className="text-right">{(r.fetch_pubkey_rtt_ms_avg ?? 0).toFixed(1)}</td>
                  <td className="text-right">{(r.query_global_model_ms_avg ?? 0).toFixed(1)}</td>
                  <td className="text-right">{r.submit_ms_avg.toFixed(0)}</td>
                  <td className="text-right text-red-700">{r.block_commit_ms_avg.toFixed(0)}</td>
                  <td className="text-right">
                    {r.round_wall_ms_avg.toFixed(0)}
                    <span className="text-slate-400"> / {r.round_wall_ms_max.toFixed(0)}</span>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        <dl className="text-[10px] text-slate-500 mt-3 grid grid-cols-1 md:grid-cols-2 gap-x-4 gap-y-1">
          <div><strong>PBS (cliente):</strong> cegado, descegado, verificación y 2 viajes al AS</div>
          <div><strong>Verif. PBS:</strong> verificación en el contrato (sin red)</div>
          <div><strong>Obtener X_AS:</strong> mTLS al AS: más de 100 ms en la ronda 1, después sale de la caché</div>
          <div><strong>Consulta MG:</strong> lectura del modelo global de la ronda previa</div>
          <div><strong>Confirmación:</strong> tiempo de la Etapa 3 del líder menos el cómputo del contrato</div>
        </dl>
      </Card>

    </div>
  );
}

function Stat({
  label,
  value,
  hint,
  tone,
}: {
  label: string;
  value: string;
  hint?: string;
  tone: "emerald" | "blue" | "slate" | "amber" | "red";
}) {
  const palette: Record<string, string> = {
    emerald: "border-emerald-300 bg-emerald-50 text-emerald-900",
    blue: "border-blue-300 bg-blue-50 text-blue-900",
    slate: "border-slate-300 bg-slate-50 text-slate-900",
    amber: "border-amber-300 bg-amber-50 text-amber-900",
    red: "border-red-300 bg-red-50 text-red-900",
  };
  return (
    <div className={`rounded-md border px-3 py-2 ${palette[tone]}`}>
      <div className="text-[10px] uppercase font-semibold opacity-70">{label}</div>
      <div className="text-xl font-bold mt-0.5">{value}</div>
      {hint && <div className="text-[10px] opacity-70 mt-0.5">{hint}</div>}
    </div>
  );
}
