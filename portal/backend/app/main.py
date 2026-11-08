"""FastAPI app del portal: entrypoint.

Wiring:
- Lifespan: warm coordinator client y ensure SQLite schema.
- Middleware: CORS para el frontend Next.js.
- Routers: catalog, experiments, runs, results, logs, health.
"""
from __future__ import annotations

import logging
from contextlib import asynccontextmanager

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app.config import get_settings
from app.gateway.coordinator_client import get_coordinator_client
from app.routers import catalog, experiments, health, logs, results, runs
from app.store.db import get_engine


def configure_logging() -> None:
    logging.basicConfig(
        level=get_settings().log_level,
        format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
    )


@asynccontextmanager
async def lifespan(app: FastAPI):
    configure_logging()
    log = logging.getLogger("portal.lifespan")
    log.info("Portal backend starting, coordinator=%s",
             get_settings().coordinator_url)
    get_engine()  # ensure SQLite schema + recover stuck runs
    yield
    log.info("Portal backend shutting down")
    await get_coordinator_client().close()


def create_app() -> FastAPI:
    app = FastAPI(
        title="FL Portal Backend",
        description="Tooling de operador para experimentos FL (UNAM tesis Ruiz)",
        version="0.1.0",
        lifespan=lifespan,
    )

    app.add_middleware(
        CORSMiddleware,
        allow_origins=get_settings().cors_origins,
        allow_credentials=True,
        allow_methods=["*"],
        allow_headers=["*"],
    )

    app.include_router(health.router, prefix="/api", tags=["health"])
    app.include_router(catalog.router, prefix="/api", tags=["catalog"])
    app.include_router(experiments.router, prefix="/api", tags=["experiments"])
    app.include_router(runs.router, prefix="/api", tags=["runs"])
    app.include_router(results.router, prefix="/api", tags=["results"])
    app.include_router(logs.router, prefix="/api", tags=["logs"])

    return app


app = create_app()
