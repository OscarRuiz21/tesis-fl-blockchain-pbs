"""Presets de comparación: arman bundles de runs comparables.

Cada preset combina una intención de comparación y una función que filtra runs
según su run_summary.json.
"""
from __future__ import annotations

from typing import Callable

from app.schemas import RunSummary


def _key_skel(s: RunSummary) -> tuple:
    """Llave de equivalencia (todo igual EXCEPTO la dimensión que se compara)."""
    return (s.k, s.total_rounds, s.strategy, s.anonymity_mode, s.seed)


def pbs_overhead(s1: RunSummary, s2: RunSummary) -> bool:
    """Mismo K/R/strategy, distinto anonymity_mode (pbs vs none)."""
    return (
        s1.k == s2.k and s1.total_rounds == s2.total_rounds
        and s1.strategy == s2.strategy
        and s1.anonymity_mode != s2.anonymity_mode
    )


def aggregation_comparison(s1: RunSummary, s2: RunSummary) -> bool:
    """Mismo K/R/anonymity, distinta aggregation strategy."""
    return (
        s1.k == s2.k and s1.total_rounds == s2.total_rounds
        and s1.anonymity_mode == s2.anonymity_mode
        and s1.strategy != s2.strategy
    )


def scale_comparison(s1: RunSummary, s2: RunSummary) -> bool:
    """Misma strategy/anonymity, distinto K."""
    return (
        s1.strategy == s2.strategy
        and s1.anonymity_mode == s2.anonymity_mode
        and s1.k != s2.k
    )


PRESETS: dict[str, dict] = {
    "pbs_overhead": {
        "label": "Costo de la PBS (con y sin anonimato)",
        "predicate": pbs_overhead,
        "metric_default": "accuracy",
        "description": "El mismo experimento con y sin PBS, para medir el costo del anonimato.",
    },
    "aggregation_comparison": {
        "label": "Estrategias de agregación",
        "predicate": aggregation_comparison,
        "metric_default": "accuracy",
        "description": "FedAvg, FedMedian y FedProx con K, R y anonimato fijos.",
    },
    "scale_comparison": {
        "label": "Escalado por K",
        "predicate": scale_comparison,
        "metric_default": "accuracy",
        "description": "Cómo varía la calidad al cambiar el número de clientes.",
    },
}


def predicate_for(preset: str) -> Callable[[RunSummary, RunSummary], bool]:
    return PRESETS[preset]["predicate"]
