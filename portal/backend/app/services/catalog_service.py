"""Catalog service: proxy al GET /catalog del coordinator con cache local.

Si el coordinator no está disponible (modo dev local sin docker compose),
cae al fallback hardcoded basado en lo que sabe el SpecValidator Java.
"""
from __future__ import annotations

import logging
from datetime import datetime, timedelta, timezone

from app.gateway.coordinator_client import CoordinatorClient, get_coordinator_client
from app.schemas import CatalogResponse

log = logging.getLogger(__name__)

# TTL del cache: si el operador agrega un modelo nuevo a MODEL_REGISTRY,
# necesita reiniciar el coordinator. 5 minutos es buen balance.
_CACHE_TTL = timedelta(minutes=5)

_FALLBACK = CatalogResponse(
    models=["logistic_regression", "mlp"],
    aggregations=["fedavg", "fedmedian", "fedprox"],
    anonymity_modes=["none", "pbs"],
    partition_strategies=["dirichlet", "iid"],
    dataset_types=["tabular_csv"],
)


class CatalogService:
    def __init__(self, client: CoordinatorClient | None = None) -> None:
        self.client = client or get_coordinator_client()
        self._cached: CatalogResponse | None = None
        self._cached_at: datetime | None = None

    async def get(self, *, force_refresh: bool = False) -> CatalogResponse:
        now = datetime.now(timezone.utc)
        fresh = (
            self._cached is not None
            and self._cached_at is not None
            and now - self._cached_at < _CACHE_TTL
        )
        if fresh and not force_refresh:
            return self._cached  # type: ignore[return-value]

        try:
            self._cached = await self.client.get_catalog()
            self._cached_at = now
            return self._cached
        except Exception as e:
            log.warning("Catalog fetch failed (%s), using fallback", e)
            if self._cached is not None:
                return self._cached
            return _FALLBACK


_singleton: CatalogService | None = None


def get_catalog_service() -> CatalogService:
    global _singleton
    if _singleton is None:
        _singleton = CatalogService()
    return _singleton
