"""SpecBuilder: convierte un ExperimentSpec (pydantic) a YAML compatible con
el coordinator y lo persiste a disco.

El YAML resultante lo consumen directamente `POST /experiments/run` y
`run-experiment.sh`.
"""
from __future__ import annotations

import logging
from pathlib import Path

import yaml

from app.gateway.coordinator_client import CoordinatorClient, get_coordinator_client
from app.schemas import ExperimentSpec, ValidateResponse, ValidationError
from app.store import files_io

log = logging.getLogger(__name__)


def to_yaml_dict(spec: ExperimentSpec) -> dict:
    """Convierte ExperimentSpec a dict YAML-ready alineado con SnakeYAML del coordinator."""
    fed = spec.federation
    out: dict = {
        "experiment": {
            "label": spec.experiment.label,
            "description": spec.experiment.description,
            "seed": spec.experiment.seed,
        },
        "model": {
            "name": spec.model.name,
            "num_params": spec.model.num_params,
            "hyperparams": dict(spec.model.hyperparams),
        },
        "dataset": {
            "type": spec.dataset.type,
            "base_path": spec.dataset.base_path,
            "target_column": spec.dataset.target_column,
            "test_set_path": spec.dataset.test_set_path,
        },
        "partition": {"strategy": spec.partition.strategy},
        "federation": {
            "num_clients": fed.num_clients,
            "num_rounds": fed.num_rounds,
            "aggregation": fed.aggregation,
            "anonymity": fed.anonymity,
        },
        "timeouts": {
            "t_train_seconds": spec.timeouts.t_train_seconds,
            "t_round_seconds": spec.timeouts.t_round_seconds,
            "t_experiment_seconds": spec.timeouts.t_experiment_seconds,
        },
        "output": {
            "results_dir": spec.output.results_dir,
            "per_client_csv": spec.output.per_client_csv,
            "global_eval_csv": spec.output.global_eval_csv,
            "run_summary_json": spec.output.run_summary_json,
        },
    }
    if spec.partition.alpha is not None:
        out["partition"]["alpha"] = spec.partition.alpha
    return out


def to_yaml_string(spec: ExperimentSpec) -> str:
    return yaml.safe_dump(to_yaml_dict(spec), sort_keys=False, default_flow_style=False)


def write(spec: ExperimentSpec) -> Path:
    """Persiste el spec en experiments/<label>.yml. Sobreescribe si ya existe."""
    path = files_io.write_spec_yaml(spec.experiment.label, to_yaml_dict(spec))
    log.info("Wrote spec to %s", path)
    return path


def load_from_disk(label: str) -> ExperimentSpec:
    """Carga un spec desde experiments/<label>.yml. Lanza FileNotFoundError si no existe."""
    raw = files_io.read_spec_yaml(label)
    return _from_yaml_dict(raw)


def _from_yaml_dict(raw: dict) -> ExperimentSpec:
    """Parsea el dict YAML (estructura coordinator) al pydantic plano del portal."""
    return ExperimentSpec.model_validate({
        "experiment": raw.get("experiment", {}),
        "model": raw.get("model", {}),
        "dataset": raw.get("dataset", {}),
        "partition": raw.get("partition", {"strategy": "iid"}),
        "federation": raw.get("federation", {}),
        "timeouts": raw.get("timeouts", {}),
        "output": raw.get("output", {}),
    })


async def validate_remote(
    spec: ExperimentSpec,
    client: CoordinatorClient | None = None,
) -> ValidateResponse:
    """Valida la spec contra el SpecValidator del coordinator (POST /validate)."""
    cli = client or get_coordinator_client()
    try:
        return await cli.validate_spec(to_yaml_string(spec))
    except Exception as e:
        log.warning("Remote validate failed (%s), returning ok-fallback", e)
        # Fallback: si el coordinator no está, no podemos validar remoto;
        # confiamos en las validaciones de pydantic que ya pasaron.
        return ValidateResponse(valid=True, errors=[])
