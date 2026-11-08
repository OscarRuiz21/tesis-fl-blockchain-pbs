"""ExperimentSpec: espejo del ExperimentSpec Java y de los bloques YAML.

Se modela en subsecciones (ModelSpec, DatasetSpec, FederationSpec, etc.) para
que el asistente del frontend pueda recorrerlas paso a paso.
"""
from __future__ import annotations

from typing import Any, Literal

from pydantic import BaseModel, Field, model_validator


class ModelSpec(BaseModel):
    name: str = Field(description="Clave en MODEL_REGISTRY (ej. logistic_regression)")
    num_params: int = Field(ge=1, description="Cantidad de pesos del modelo (flat)")
    hyperparams: dict[str, Any] = Field(default_factory=dict)


class DatasetSpec(BaseModel):
    type: str = Field(description="Tipo de dataset (clave en DATASET_LOADERS)")
    base_path: str = Field(description="Path padre de partitions/client-i/")
    target_column: str
    test_set_path: str


class PartitionSpec(BaseModel):
    strategy: Literal["iid", "dirichlet"] = "iid"
    alpha: float | None = Field(
        default=None, gt=0, description="Required if strategy=dirichlet"
    )

    @model_validator(mode="after")
    def check_alpha(self) -> "PartitionSpec":
        if self.strategy == "dirichlet" and self.alpha is None:
            raise ValueError("partition.alpha required when strategy=dirichlet")
        return self


class FederationSpec(BaseModel):
    num_clients: int = Field(ge=1, description="K")
    num_rounds: int = Field(ge=1, description="R")
    aggregation: Literal["fedavg", "fedmedian", "fedprox"] = "fedavg"
    anonymity: Literal["pbs", "none"] = "pbs"


class TimeoutsSpec(BaseModel):
    t_train_seconds: int = Field(default=60, ge=1)
    t_round_seconds: int = Field(default=180, ge=1)
    t_experiment_seconds: int = Field(default=3600, ge=1)


class OutputSpec(BaseModel):
    results_dir: str = Field(description="Directorio absoluto para escribir results")
    per_client_csv: bool = True
    global_eval_csv: bool = True
    run_summary_json: bool = True


class ExperimentMetaSpec(BaseModel):
    label: str = Field(min_length=1, description="Identificador legible")
    description: str = ""
    seed: int = 42


class ExperimentSpec(BaseModel):
    """ExperimentSpec completo, serializable hacia y desde el YAML del coordinator.

    Bloques: `experiment / model / dataset / partition / federation / timeouts / output`.
    """

    experiment: ExperimentMetaSpec
    model: ModelSpec
    dataset: DatasetSpec
    partition: PartitionSpec = Field(default_factory=PartitionSpec)
    federation: FederationSpec
    timeouts: TimeoutsSpec = Field(default_factory=TimeoutsSpec)
    output: OutputSpec


class ValidationError(BaseModel):
    """Espejo de InvalidExperimentSpecException.ValidationError (Java)."""

    field: str
    message: str


class ValidateResponse(BaseModel):
    """Espejo de ExperimentDtos.ValidateResponse (Java)."""

    valid: bool
    errors: list[ValidationError] = Field(default_factory=list)
