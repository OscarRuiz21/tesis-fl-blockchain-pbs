"""Cliente HTTP mTLS al coordinator.

Encapsula la conexión al coordinator para `GET /catalog`, `POST /experiments/validate`
y `POST /experiments/run`. En dev (sin certs configurados) usa TLS verify=False;
en producción carga el cert/key del portal y verifica el CA del coordinator.
"""
from __future__ import annotations

import logging
import ssl
from typing import Any

import httpx

from app.config import Settings, get_settings
from app.schemas import CatalogResponse, ValidateResponse

log = logging.getLogger(__name__)


class CoordinatorClient:
    """Wrapper sobre httpx con configuración mTLS hacia el coordinator."""

    def __init__(self, settings: Settings | None = None) -> None:
        self.settings = settings or get_settings()
        self._client: httpx.AsyncClient | None = None

    def _build_ssl_context(self) -> ssl.SSLContext | bool:
        """Construye el SSLContext con el cert/key del portal si están presentes."""
        s = self.settings
        if not s.coordinator_tls_cert or not s.coordinator_tls_key:
            log.warning(
                "No mTLS configurado para el portal, coordinator_url=%s verify=%s",
                s.coordinator_url, s.coordinator_verify_tls,
            )
            return s.coordinator_verify_tls
        ctx = ssl.create_default_context()
        if s.coordinator_tls_ca:
            ctx.load_verify_locations(cafile=str(s.coordinator_tls_ca))
        ctx.load_cert_chain(
            certfile=str(s.coordinator_tls_cert),
            keyfile=str(s.coordinator_tls_key),
        )
        if not s.coordinator_verify_tls:
            ctx.check_hostname = False
            ctx.verify_mode = ssl.CERT_NONE
        return ctx

    async def _ensure_client(self) -> httpx.AsyncClient:
        if self._client is None:
            verify = self._build_ssl_context()
            self._client = httpx.AsyncClient(
                base_url=self.settings.coordinator_url,
                verify=verify,
                timeout=httpx.Timeout(30.0, connect=10.0),
            )
        return self._client

    async def close(self) -> None:
        if self._client is not None:
            await self._client.aclose()
            self._client = None

    async def get_catalog(self) -> CatalogResponse:
        client = await self._ensure_client()
        r = await client.get("/catalog")
        r.raise_for_status()
        return CatalogResponse.model_validate(r.json())

    async def validate_spec(self, yaml_body: str) -> ValidateResponse:
        client = await self._ensure_client()
        r = await client.post(
            "/experiments/validate",
            content=yaml_body,
            headers={"Content-Type": "application/x-yaml"},
        )
        r.raise_for_status()
        return ValidateResponse.model_validate(r.json())

    async def run_experiment(self, yaml_body: str) -> dict[str, Any]:
        """POST /experiments/run síncrono (bloquea durante el experimento).

        El portal envuelve esta llamada en un background task. Timeout extendido
        para experimentos largos.
        """
        client = await self._ensure_client()
        r = await client.post(
            "/experiments/run",
            content=yaml_body,
            headers={"Content-Type": "application/x-yaml"},
            timeout=httpx.Timeout(3600.0, connect=10.0),
        )
        r.raise_for_status()
        return r.json()


_singleton: CoordinatorClient | None = None


def get_coordinator_client() -> CoordinatorClient:
    """FastAPI Depends-friendly singleton."""
    global _singleton
    if _singleton is None:
        _singleton = CoordinatorClient()
    return _singleton
