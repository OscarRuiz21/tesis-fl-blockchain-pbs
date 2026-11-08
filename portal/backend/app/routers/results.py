"""Resultados: métricas JSON, gráficas PNG, comparación, export PDF."""
from __future__ import annotations

from pydantic import BaseModel
from fastapi import APIRouter, HTTPException, Response

from app.schemas import RunMetrics, TimingsBundle
from app.services import chart_generator, pdf_exporter, presets, results_reader, timings_reader
from app.services.presets import PRESETS
from app.store import files_io, runs_repo

router = APIRouter()


def _bundle_for(rec) -> "RunMetrics":
    return results_reader.build_metrics_bundle(
        rec.spec_label,
        rec.run_id,
        run_started_at=rec.started_at,
        run_is_terminal=rec.state.is_terminal,
    )


@router.get("/runs/{run_id}/metrics", response_model=RunMetrics)
def get_metrics(run_id: str) -> RunMetrics:
    rec = runs_repo.get(run_id)
    if rec is None:
        raise HTTPException(status_code=404)
    return _bundle_for(rec)


@router.get("/runs/{run_id}/charts/{kind}.png")
def get_chart_png(run_id: str, kind: str) -> Response:
    rec = runs_repo.get(run_id)
    if rec is None:
        raise HTTPException(status_code=404)
    metrics = _bundle_for(rec)
    png = _render_chart(kind, metrics)
    return Response(content=png, media_type="image/png")


@router.get("/runs/{run_id}/charts/{kind}.plotly")
def get_chart_plotly(run_id: str, kind: str) -> dict:
    rec = runs_repo.get(run_id)
    if rec is None:
        raise HTTPException(status_code=404)
    metrics = _bundle_for(rec)
    if kind == "accuracy_vs_round":
        return chart_generator.accuracy_plotly_json(metrics)
    raise HTTPException(status_code=400, detail=f"Unsupported plotly kind: {kind}")


@router.get("/runs/{run_id}/timings", response_model=TimingsBundle)
def get_timings(run_id: str) -> TimingsBundle:
    """Bundle de análisis temporal: per-ronda + summary con %overhead + convergencia."""
    rec = runs_repo.get(run_id)
    if rec is None:
        raise HTTPException(status_code=404)
    return timings_reader.build_bundle(rec)


@router.get("/runs/{run_id}/timings/stacked.png")
def get_timings_stacked_png(run_id: str) -> Response:
    rec = runs_repo.get(run_id)
    if rec is None:
        raise HTTPException(status_code=404)
    bundle = timings_reader.build_bundle(rec)
    png = chart_generator.timings_stacked_png(bundle.per_round, label=rec.spec_label)
    return Response(content=png, media_type="image/png")


@router.get("/runs/{run_id}/timings/convergence.png")
def get_convergence_png(run_id: str) -> Response:
    rec = runs_repo.get(run_id)
    if rec is None:
        raise HTTPException(status_code=404)
    bundle = timings_reader.build_bundle(rec)
    png = chart_generator.convergence_delta_png(bundle.convergence, label=rec.spec_label)
    return Response(content=png, media_type="image/png")


@router.get("/runs/{run_id}/export.pdf")
def export_pdf(run_id: str) -> Response:
    rec = runs_repo.get(run_id)
    if rec is None:
        raise HTTPException(status_code=404)
    metrics = _bundle_for(rec)
    pdf = pdf_exporter.export_run_pdf(metrics)
    fname = f"{rec.spec_label}-{run_id[:8]}.pdf"
    return Response(
        content=pdf,
        media_type="application/pdf",
        headers={"Content-Disposition": f'attachment; filename="{fname}"'},
    )


class CompareRequest(BaseModel):
    run_ids: list[str]
    metric: str = "accuracy"


@router.post("/compare")
def compare(req: CompareRequest) -> dict:
    bundles = _load_bundles(req.run_ids)
    if not bundles:
        raise HTTPException(status_code=404, detail="No metrics found for given run_ids")
    # Devuelve tabla resumen para que el frontend pueda renderizarla.
    rows = []
    for label, m in bundles:
        s = m.summary
        rows.append({
            "label": label,
            "k": s.k if s else None,
            "total_rounds": s.total_rounds if s else None,
            "strategy": s.strategy if s else None,
            "anonymity_mode": s.anonymity_mode if s else None,
            "final_accuracy": s.final_accuracy if s else None,
            "final_loss": s.final_loss if s else None,
            "total_seconds": s.total_seconds if s else None,
        })
    return {"rows": rows, "metric": req.metric, "count": len(bundles)}


@router.post("/compare/chart.png")
def compare_chart(req: CompareRequest) -> Response:
    bundles = _load_bundles(req.run_ids)
    if not bundles:
        raise HTTPException(status_code=404)
    png = chart_generator.compare_overlay_png(bundles, req.metric)
    return Response(content=png, media_type="image/png")


@router.get("/compare/chart.png")
def compare_chart_get(run_ids: str, metric: str = "accuracy") -> Response:
    """Variante GET para usar directamente en <img src=...> del browser."""
    ids = [x for x in run_ids.split(",") if x.strip()]
    bundles = _load_bundles(ids)
    if not bundles:
        raise HTTPException(status_code=404)
    png = chart_generator.compare_overlay_png(bundles, metric)
    return Response(content=png, media_type="image/png")


@router.post("/compare/export.pdf")
def compare_pdf(req: CompareRequest) -> Response:
    bundles = _load_bundles(req.run_ids)
    if not bundles:
        raise HTTPException(status_code=404)
    pdf = pdf_exporter.export_compare_pdf(bundles, req.metric)
    return Response(
        content=pdf,
        media_type="application/pdf",
        headers={"Content-Disposition": 'attachment; filename="comparison.pdf"'},
    )


@router.get("/compare/presets")
def list_presets() -> list[dict]:
    return [
        {"key": k, "label": v["label"], "description": v["description"],
         "metric_default": v["metric_default"]}
        for k, v in PRESETS.items()
    ]


@router.get("/results")
def list_results() -> list[dict]:
    return files_io.list_result_dirs()


# ---------------- helpers ----------------

def _render_chart(kind: str, metrics: RunMetrics) -> bytes:
    if kind == "accuracy_vs_round":
        return chart_generator.accuracy_vs_round_png(metrics)
    if kind == "loss_vs_round":
        return chart_generator.loss_vs_round_png(metrics)
    if kind == "per_client_accuracy":
        return chart_generator.per_client_accuracy_png(metrics)
    raise HTTPException(status_code=400, detail=f"Unknown chart kind: {kind}")


def _load_bundles(run_ids: list[str]) -> list[tuple[str, RunMetrics]]:
    bundles: list[tuple[str, RunMetrics]] = []
    for rid in run_ids:
        rec = runs_repo.get(rid)
        if rec is None:
            continue
        bundles.append((rec.spec_label, _bundle_for(rec)))
    return bundles
