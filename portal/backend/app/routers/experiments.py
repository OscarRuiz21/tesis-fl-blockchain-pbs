"""CRUD de specs YAML en experiments/."""
from __future__ import annotations

from fastapi import APIRouter, HTTPException

from app.schemas import ExperimentSpec, ValidateResponse, ValidationError
from app.services import spec_builder
from app.store import files_io

router = APIRouter()


@router.get("/experiments")
def list_experiments() -> list[dict]:
    return files_io.list_specs()


@router.get("/experiments/{label}", response_model=ExperimentSpec)
def get_experiment(label: str) -> ExperimentSpec:
    try:
        return spec_builder.load_from_disk(label)
    except FileNotFoundError as e:
        raise HTTPException(status_code=404, detail=str(e))


@router.post("/experiments", response_model=ExperimentSpec, status_code=201)
def create_experiment(spec: ExperimentSpec) -> ExperimentSpec:
    spec_builder.write(spec)
    return spec


@router.put("/experiments/{label}", response_model=ExperimentSpec)
def update_experiment(label: str, spec: ExperimentSpec) -> ExperimentSpec:
    if spec.experiment.label != label:
        raise HTTPException(
            status_code=400,
            detail=f"Path label '{label}' does not match body label '{spec.experiment.label}'",
        )
    spec_builder.write(spec)
    return spec


@router.delete("/experiments/{label}", status_code=204)
def delete_experiment(label: str) -> None:
    if not files_io.delete_spec(label):
        raise HTTPException(status_code=404, detail=f"Spec '{label}' not found")


@router.post("/experiments/{label}/validate", response_model=ValidateResponse)
async def validate_experiment(label: str) -> ValidateResponse:
    """Valida un spec guardado contra el SpecValidator del coordinator."""
    try:
        spec = spec_builder.load_from_disk(label)
    except FileNotFoundError as e:
        return ValidateResponse(
            valid=False,
            errors=[ValidationError(field="label", message=str(e))],
        )
    return await spec_builder.validate_remote(spec)


@router.post("/experiments/validate-inline", response_model=ValidateResponse)
async def validate_inline(spec: ExperimentSpec) -> ValidateResponse:
    """Valida un spec sin persistir, usado en vivo desde el wizard."""
    return await spec_builder.validate_remote(spec)
