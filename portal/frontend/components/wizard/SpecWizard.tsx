"use client";
import { useEffect, useMemo, useState } from "react";
import { useRouter } from "next/navigation";
import { api } from "@/lib/api";
import type {
  CatalogResponse,
  ExperimentSpec,
  ValidateResponse,
} from "@/lib/types";
import { Button } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";
import { useToast } from "@/components/Toast";

const DEFAULT_SPEC: ExperimentSpec = {
  experiment: { label: "", description: "", seed: 42 },
  model: {
    name: "logistic_regression",
    num_params: 24,
    hyperparams: { epochs: 5, lr: 0.01, batch_size: 32 },
  },
  dataset: {
    type: "tabular_csv",
    base_path: "/opt/python/data/uci_credit/partitions/K2_seed42_iid",
    target_column: "default_next_month",
    test_set_path: "/opt/python/data/uci_credit/test.csv",
  },
  partition: { strategy: "iid" },
  federation: {
    num_clients: 2,
    num_rounds: 3,
    aggregation: "fedavg",
    anonymity: "pbs",
  },
  timeouts: {
    t_train_seconds: 60,
    t_round_seconds: 180,
    t_experiment_seconds: 3600,
  },
  output: {
    results_dir: "/results/exp",
    per_client_csv: true,
    global_eval_csv: true,
    run_summary_json: true,
  },
};

const STEPS = ["Experimento", "Modelo", "Dataset", "Federación", "Salida"];

export function SpecWizard({
  initial,
  onSaved,
  onLaunched,
}: {
  initial?: ExperimentSpec;
  onSaved?: (label: string) => void;
  onLaunched?: (runId: string) => void;
}) {
  const router = useRouter();
  const toast = useToast();
  const [step, setStep] = useState(0);
  const [spec, setSpec] = useState<ExperimentSpec>(initial ?? DEFAULT_SPEC);
  const isEditing = Boolean(initial);
  const [catalog, setCatalog] = useState<CatalogResponse | null>(null);
  const [validation, setValidation] = useState<ValidateResponse | null>(null);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    api.getCatalog().then(setCatalog).catch((e) => setError(String(e)));
  }, []);

  // Auto-genera label si el usuario no lo tocó
  const autoLabel = useMemo(() => {
    const f = spec.federation;
    return `${spec.model.name}-K${f.num_clients}-R${f.num_rounds}-${spec.partition.strategy}-${f.aggregation}-${f.anonymity}`;
  }, [spec.federation, spec.model.name, spec.partition.strategy]);

  useEffect(() => {
    if (!spec.experiment.label) {
      setSpec((s) => ({
        ...s,
        experiment: { ...s.experiment, label: autoLabel },
        output: { ...s.output, results_dir: `/results/${autoLabel}` },
      }));
    }
  }, [autoLabel, spec.experiment.label]);

  async function validateNow() {
    try {
      const r = await api.validateInline(spec);
      setValidation(r);
    } catch (e) {
      setError(String(e));
    }
  }

  async function save(launch: boolean) {
    setSaving(true);
    setError(null);
    try {
      if (isEditing && initial) {
        await api.updateExperiment(initial.experiment.label, spec);
      } else {
        await api.createExperiment(spec);
      }
      if (!launch) {
        toast.push({
          kind: "success",
          title: isEditing ? "Spec actualizada" : "Spec guardada",
          message: spec.experiment.label,
        });
        if (onSaved) {
          onSaved(spec.experiment.label);
        } else {
          router.push(`/`);
        }
        return;
      }
      // launch=true: encolar + notificar callback (o navegar)
      const loadingId = toast.push({
        kind: "loading",
        title: "Encolando experimento…",
        message: spec.experiment.label,
        durationMs: 0,
      });
      const run = await api.enqueueRun(spec.experiment.label);
      toast.update(loadingId, {
        kind: "success",
        title: "Experimento lanzado",
        message: `run_id: ${run.run_id.slice(0, 8)}…`,
        durationMs: 5000,
      });
      if (onLaunched) {
        onLaunched(run.run_id);
      } else {
        router.push(`/?run=${run.run_id}`);
      }
    } catch (e) {
      setError(String(e));
      toast.push({
        kind: "error",
        title: "Error",
        message: String(e),
        durationMs: 8000,
      });
    } finally {
      setSaving(false);
    }
  }

  function update<K extends keyof ExperimentSpec>(
    key: K,
    patch: Partial<ExperimentSpec[K]>,
  ) {
    setSpec((s) => ({ ...s, [key]: { ...s[key], ...patch } }));
  }

  return (
    <div className="space-y-4">
      <div className="flex gap-2 text-xs">
        {STEPS.map((s, i) => (
          <button
            key={s}
            onClick={() => setStep(i)}
            className={`px-3 py-1.5 rounded ${
              step === i
                ? "bg-slate-900 text-white"
                : "bg-slate-200 text-slate-700"
            }`}
          >
            {i + 1}. {s}
          </button>
        ))}
      </div>

      {step === 0 && (
        <Card title="Experimento">
          <Field label="Label">
            <input
              className="input"
              value={spec.experiment.label}
              onChange={(e) =>
                update("experiment", { label: e.target.value })
              }
            />
          </Field>
          <Field label="Description">
            <textarea
              className="input min-h-[60px]"
              value={spec.experiment.description}
              onChange={(e) =>
                update("experiment", { description: e.target.value })
              }
            />
          </Field>
          <Field label="Seed">
            <input
              type="number"
              className="input"
              value={spec.experiment.seed}
              onChange={(e) =>
                update("experiment", { seed: Number(e.target.value) })
              }
            />
          </Field>
        </Card>
      )}

      {step === 1 && (
        <Card title="Modelo">
          <Field label="Modelo">
            <select
              className="input"
              value={spec.model.name}
              onChange={(e) => update("model", { name: e.target.value })}
            >
              {catalog?.models.map((m) => (
                <option key={m} value={m}>
                  {m}
                </option>
              ))}
            </select>
          </Field>
          <Field label="num_params">
            <input
              type="number"
              className="input"
              value={spec.model.num_params}
              onChange={(e) =>
                update("model", { num_params: Number(e.target.value) })
              }
            />
          </Field>
          <Field label="Hyperparams (JSON)">
            <textarea
              className="input min-h-[80px] font-mono text-xs"
              value={JSON.stringify(spec.model.hyperparams, null, 2)}
              onChange={(e) => {
                try {
                  const parsed = JSON.parse(e.target.value);
                  update("model", { hyperparams: parsed });
                } catch {
                  // ignore mientras está parcial
                }
              }}
            />
          </Field>
        </Card>
      )}

      {step === 2 && (
        <Card title="Dataset">
          <Field label="Tipo">
            <select
              className="input"
              value={spec.dataset.type}
              onChange={(e) => update("dataset", { type: e.target.value })}
            >
              {catalog?.datasetTypes.map((t) => (
                <option key={t} value={t}>
                  {t}
                </option>
              ))}
            </select>
          </Field>
          <Field label="Base path (particiones)">
            <input
              className="input"
              value={spec.dataset.base_path}
              onChange={(e) =>
                update("dataset", { base_path: e.target.value })
              }
            />
          </Field>
          <Field label="Target column">
            <input
              className="input"
              value={spec.dataset.target_column}
              onChange={(e) =>
                update("dataset", { target_column: e.target.value })
              }
            />
          </Field>
          <Field label="Test set path">
            <input
              className="input"
              value={spec.dataset.test_set_path}
              onChange={(e) =>
                update("dataset", { test_set_path: e.target.value })
              }
            />
          </Field>
        </Card>
      )}

      {step === 3 && (
        <Card title="Federación">
          <div className="grid grid-cols-2 gap-3">
            <Field label="K (clientes)">
              <input
                type="number"
                min={1}
                className="input"
                value={spec.federation.num_clients}
                onChange={(e) =>
                  update("federation", {
                    num_clients: Number(e.target.value),
                  })
                }
              />
            </Field>
            <Field label="R (rondas)">
              <input
                type="number"
                min={1}
                className="input"
                value={spec.federation.num_rounds}
                onChange={(e) =>
                  update("federation", {
                    num_rounds: Number(e.target.value),
                  })
                }
              />
            </Field>
            <Field label="Agregación">
              <select
                className="input"
                value={spec.federation.aggregation}
                onChange={(e) =>
                  update("federation", {
                    aggregation: e.target.value as
                      | "fedavg"
                      | "fedmedian"
                      | "fedprox",
                  })
                }
              >
                {catalog?.aggregations.map((a) => (
                  <option key={a} value={a}>
                    {a}
                  </option>
                ))}
              </select>
            </Field>
            <Field label="Anonimato">
              <select
                className="input"
                value={spec.federation.anonymity}
                onChange={(e) =>
                  update("federation", {
                    anonymity: e.target.value as "pbs" | "none",
                  })
                }
              >
                {catalog?.anonymityModes.map((a) => (
                  <option key={a} value={a}>
                    {a}
                  </option>
                ))}
              </select>
            </Field>
            <Field label="Partition strategy">
              <select
                className="input"
                value={spec.partition.strategy}
                onChange={(e) =>
                  update("partition", {
                    strategy: e.target.value as "iid" | "dirichlet",
                  })
                }
              >
                {catalog?.partitionStrategies.map((p) => (
                  <option key={p} value={p}>
                    {p}
                  </option>
                ))}
              </select>
            </Field>
            {spec.partition.strategy === "dirichlet" && (
              <Field label="Dirichlet α">
                <input
                  type="number"
                  step="0.1"
                  min={0.1}
                  className="input"
                  value={spec.partition.alpha ?? 0.5}
                  onChange={(e) =>
                    update("partition", { alpha: Number(e.target.value) })
                  }
                />
              </Field>
            )}
          </div>
        </Card>
      )}

      {step === 4 && (
        <Card title="Salida y timeouts">
          <Field label="results_dir">
            <input
              className="input"
              value={spec.output.results_dir}
              onChange={(e) =>
                update("output", { results_dir: e.target.value })
              }
            />
          </Field>
          <div className="grid grid-cols-3 gap-3">
            <Field label="t_train (s)">
              <input
                type="number"
                className="input"
                value={spec.timeouts.t_train_seconds}
                onChange={(e) =>
                  update("timeouts", {
                    t_train_seconds: Number(e.target.value),
                  })
                }
              />
            </Field>
            <Field label="t_round (s)">
              <input
                type="number"
                className="input"
                value={spec.timeouts.t_round_seconds}
                onChange={(e) =>
                  update("timeouts", {
                    t_round_seconds: Number(e.target.value),
                  })
                }
              />
            </Field>
            <Field label="t_experiment (s)">
              <input
                type="number"
                className="input"
                value={spec.timeouts.t_experiment_seconds}
                onChange={(e) =>
                  update("timeouts", {
                    t_experiment_seconds: Number(e.target.value),
                  })
                }
              />
            </Field>
          </div>
        </Card>
      )}

      <div className="flex justify-between items-center">
        <Button
          variant="ghost"
          disabled={step === 0}
          onClick={() => setStep((s) => s - 1)}
        >
          ← Anterior
        </Button>
        <div className="flex gap-2">
          <Button variant="secondary" onClick={validateNow}>
            Validar contra coordinator
          </Button>
          {step < STEPS.length - 1 && (
            <Button onClick={() => setStep((s) => s + 1)}>Siguiente →</Button>
          )}
          {step === STEPS.length - 1 && (
            <>
              <Button
                variant="secondary"
                onClick={() => save(false)}
                disabled={saving}
              >
                Guardar
              </Button>
              <Button onClick={() => save(true)} disabled={saving}>
                Guardar y lanzar
              </Button>
            </>
          )}
        </div>
      </div>

      {validation && (
        <Card title="Validación">
          {validation.valid ? (
            <div className="text-sm text-emerald-700">✓ Spec válida</div>
          ) : (
            <ul className="text-xs text-red-700 space-y-1">
              {validation.errors.map((e, i) => (
                <li key={i}>
                  <strong>{e.field}:</strong> {e.message}
                </li>
              ))}
            </ul>
          )}
        </Card>
      )}

      {error && (
        <Card>
          <pre className="text-xs text-red-700 whitespace-pre-wrap">
            {error}
          </pre>
        </Card>
      )}

      <style jsx global>{`
        .input {
          width: 100%;
          padding: 0.45rem 0.6rem;
          border: 1px solid #cbd5e1;
          border-radius: 4px;
          font-size: 0.85rem;
          background: white;
        }
        .input:focus {
          outline: 2px solid #1e293b;
          outline-offset: -1px;
        }
      `}</style>
    </div>
  );
}

function Field({
  label,
  children,
}: {
  label: string;
  children: React.ReactNode;
}) {
  return (
    <label className="block mb-3">
      <span className="text-xs font-semibold text-slate-700 block mb-1">
        {label}
      </span>
      {children}
    </label>
  );
}
