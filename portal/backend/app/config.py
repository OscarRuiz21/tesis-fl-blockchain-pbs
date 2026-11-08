"""Settings del portal, gobernadas por env vars vía pydantic-settings.

Todos los paths apuntan a binds compartidos con los servicios existentes
(experiments/, results/, data/, python/, scripts/) para que el portal pueda
leer/escribir lo mismo que el coordinator y los scripts shell.
"""
from __future__ import annotations

from pathlib import Path

from pydantic import Field
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    """Settings del portal: env vars con prefijo ``PORTAL_``."""

    model_config = SettingsConfigDict(
        env_prefix="PORTAL_",
        env_file=".env",
        case_sensitive=False,
        # El .env mounted contiene también vars del stack (COMPOSE_PROJECT_NAME,
        # FABRIC_VERSION, etc.). Ignorar las que no matchean PORTAL_*.
        extra="ignore",
    )

    # ---- Coordinator ----
    coordinator_url: str = Field(
        default="https://fl-coordinator:9100",
        description="Base URL del coordinator (mTLS).",
    )
    coordinator_tls_cert: Path | None = Field(
        default=None,
        description="Cert mTLS del portal (CN=portal). None = TLS verify desactivado (dev).",
    )
    coordinator_tls_key: Path | None = Field(default=None)
    coordinator_tls_ca: Path | None = Field(default=None)
    coordinator_verify_tls: bool = Field(
        default=False,
        description="Verificar TLS del coordinator. Dev=False, prod=True con CA bundle.",
    )

    # ---- Filesystem paths (bind mounts compartidos) ----
    project_root: Path = Field(default=Path("/app"))
    experiments_dir: Path = Field(default=Path("/app/experiments"))
    results_dir: Path = Field(default=Path("/app/results"))
    data_dir: Path = Field(default=Path("/app/data"))
    python_dir: Path = Field(default=Path("/app/python"))
    scripts_dir: Path = Field(default=Path("/app/scripts"))
    portal_data_dir: Path = Field(default=Path("/app/portal-data"))
    # Path en el HOST del project root (no del container). Necesario para que
    # el preflight pueda invocar `docker compose --project-directory <host_path>`:
    # el Docker daemon corre en el host y resuelve ahí los paths relativos del
    # compose. Setearlo desde compose con `PORTAL_HOST_PROJECT_DIR=${PWD}`.
    host_project_dir: Path | None = Field(default=None)
    # COMPOSE_PROJECT_NAME del stack original, necesario para que el preflight
    # use el mismo project que `start-network.sh` (de otro modo crearía un
    # stack paralelo con name conflict en los container_name explícitos).
    compose_project: str = Field(default="fl-pbs")

    # ---- Runner ----
    run_experiment_script: str = Field(default="scripts/run-experiment.sh")
    prepare_data_script: str = Field(default="scripts/prepare-data.sh")

    # ---- API ----
    cors_origins: list[str] = Field(
        default=["http://localhost:3000", "http://localhost:3001"],
        description="Origenes CORS permitidos (frontend Next.js).",
    )

    # ---- Logging ----
    log_level: str = Field(default="INFO")


_settings: Settings | None = None


def get_settings() -> Settings:
    """Cached singleton, compatible con FastAPI Depends."""
    global _settings
    if _settings is None:
        _settings = Settings()
    return _settings
