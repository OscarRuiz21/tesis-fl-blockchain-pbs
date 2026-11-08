"""Pydantic schemas: espejos de los DTOs Java del coordinator/AS."""
from app.schemas.catalog import CatalogResponse
from app.schemas.metrics import (
    ClientMetricRow,
    GlobalEvalRow,
    RunMetrics,
    RunSummary,
)
from app.schemas.run import RunRecord, RunState
from app.schemas.spec import (
    DatasetSpec,
    ExperimentSpec,
    FederationSpec,
    ModelSpec,
    OutputSpec,
    PartitionSpec,
    TimeoutsSpec,
    ValidateResponse,
    ValidationError,
)
from app.schemas.timings import (
    ConvergenceRow,
    RoundTimings,
    TimingsBundle,
    TimingsSummary,
)

__all__ = [
    "CatalogResponse",
    "ClientMetricRow",
    "ConvergenceRow",
    "DatasetSpec",
    "ExperimentSpec",
    "FederationSpec",
    "GlobalEvalRow",
    "ModelSpec",
    "OutputSpec",
    "PartitionSpec",
    "RoundTimings",
    "RunMetrics",
    "RunRecord",
    "RunState",
    "RunSummary",
    "TimeoutsSpec",
    "TimingsBundle",
    "TimingsSummary",
    "ValidateResponse",
    "ValidationError",
]
