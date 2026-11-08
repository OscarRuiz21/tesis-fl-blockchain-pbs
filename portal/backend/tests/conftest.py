"""Fixtures globales: apunta settings + DB a tmp dirs aislados."""
from __future__ import annotations

import os
from pathlib import Path

import pytest


@pytest.fixture(autouse=True)
def _isolate_portal(tmp_path: Path, monkeypatch: pytest.MonkeyPatch):
    """Cada test usa un portal_data_dir + experiments + results aislado."""
    portal_data = tmp_path / "portal-data"
    experiments = tmp_path / "experiments"
    results = tmp_path / "results"
    data = tmp_path / "data"
    portal_data.mkdir()
    experiments.mkdir()
    results.mkdir()
    data.mkdir()

    monkeypatch.setenv("PORTAL_PORTAL_DATA_DIR", str(portal_data))
    monkeypatch.setenv("PORTAL_EXPERIMENTS_DIR", str(experiments))
    monkeypatch.setenv("PORTAL_RESULTS_DIR", str(results))
    monkeypatch.setenv("PORTAL_DATA_DIR", str(data))
    monkeypatch.setenv("PORTAL_PROJECT_ROOT", str(tmp_path))

    # Reset cached singletons que leen settings al construir.
    import app.config as config
    import app.store.db as db
    import app.services.catalog_service as catalog_service

    config._settings = None
    db._engine = None
    catalog_service._singleton = None

    yield

    config._settings = None
    db._engine = None
    catalog_service._singleton = None
