"""Catálogo de opciones expuestas por el coordinator vía GET /catalog."""
from __future__ import annotations

from pydantic import BaseModel, Field


class CatalogResponse(BaseModel):
    """Espejo del CatalogController.CatalogResponse del coordinator (Java)."""

    models: list[str] = Field(default_factory=list)
    aggregations: list[str] = Field(default_factory=list)
    anonymity_modes: list[str] = Field(
        default_factory=list, alias="anonymityModes"
    )
    partition_strategies: list[str] = Field(
        default_factory=list, alias="partitionStrategies"
    )
    dataset_types: list[str] = Field(
        default_factory=list, alias="datasetTypes"
    )

    model_config = {"populate_by_name": True}
