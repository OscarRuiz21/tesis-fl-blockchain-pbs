"""PDF multi-página: portada con resumen y gráficas por run.

Usa reportlab para el layout y PNG de matplotlib embebido. Salida pensada para
anexar directo al manuscrito.
"""
from __future__ import annotations

import io
from datetime import datetime, timezone

from reportlab.lib import colors
from reportlab.lib.pagesizes import LETTER
from reportlab.lib.styles import getSampleStyleSheet, ParagraphStyle
from reportlab.lib.units import inch
from reportlab.platypus import (
    Image,
    PageBreak,
    Paragraph,
    SimpleDocTemplate,
    Spacer,
    Table,
    TableStyle,
)

from app.schemas import RunMetrics
from app.services import chart_generator


def export_run_pdf(metrics: RunMetrics) -> bytes:
    buf = io.BytesIO()
    doc = SimpleDocTemplate(buf, pagesize=LETTER,
                            leftMargin=0.6 * inch, rightMargin=0.6 * inch,
                            topMargin=0.5 * inch, bottomMargin=0.5 * inch)

    styles = getSampleStyleSheet()
    h1 = ParagraphStyle("h1", parent=styles["Heading1"], fontSize=18)
    body = styles["BodyText"]

    story = []
    label = metrics.summary.experiment_label if metrics.summary else metrics.run_id
    story.append(Paragraph(f"Reporte de experimento: {label}", h1))
    story.append(Spacer(1, 0.2 * inch))

    if metrics.summary is not None:
        s = metrics.summary
        rows = [
            ["Label", s.experiment_label],
            ["K (clientes)", str(s.k)],
            ["R (rondas)", str(s.total_rounds)],
            ["Estrategia", s.strategy],
            ["Anonymity mode", s.anonymity_mode],
            ["Seed", str(s.seed)],
            ["Final accuracy", f"{s.final_accuracy:.4f}" if s.final_accuracy is not None else "n/a"],
            ["Final loss", f"{s.final_loss:.4f}" if s.final_loss is not None else "n/a"],
            ["Tiempo total (s)", f"{s.total_seconds:.1f}"],
            ["Rondas completadas", str(s.rounds_completed)],
            ["Started at", s.started_at.isoformat()],
            ["Finished at", s.finished_at.isoformat()],
        ]
        t = Table(rows, colWidths=[2 * inch, 4 * inch])
        t.setStyle(TableStyle([
            ("GRID", (0, 0), (-1, -1), 0.4, colors.grey),
            ("BACKGROUND", (0, 0), (0, -1), colors.lightgrey),
            ("FONTNAME", (0, 0), (-1, -1), "Helvetica"),
            ("FONTSIZE", (0, 0), (-1, -1), 9),
        ]))
        story.append(t)
        story.append(Spacer(1, 0.3 * inch))

    story.append(Paragraph(
        f"Generado por FL Portal el {datetime.now(timezone.utc).isoformat(timespec='seconds')}", body))
    story.append(PageBreak())

    # Charts
    for title, png in [
        ("Accuracy vs ronda", chart_generator.accuracy_vs_round_png(metrics)),
        ("Loss vs ronda", chart_generator.loss_vs_round_png(metrics)),
        ("Accuracy por cliente", chart_generator.per_client_accuracy_png(metrics)),
    ]:
        story.append(Paragraph(title, styles["Heading2"]))
        story.append(Image(io.BytesIO(png), width=6.5 * inch, height=4 * inch))
        story.append(PageBreak())

    doc.build(story)
    return buf.getvalue()


def export_compare_pdf(
    bundles: list[tuple[str, RunMetrics]],
    metric: str = "accuracy",
) -> bytes:
    buf = io.BytesIO()
    doc = SimpleDocTemplate(buf, pagesize=LETTER)
    styles = getSampleStyleSheet()
    story = [
        Paragraph(f"Comparación entre experimentos: {metric}", styles["Heading1"]),
        Spacer(1, 0.2 * inch),
    ]
    rows = [["Label", "K", "R", "Strategy", "Anonymity", "Final acc", "Final loss", "Time (s)"]]
    for label, m in bundles:
        s = m.summary
        if s is None:
            rows.append([label, "?", "?", "?", "?", "?", "?", "?"])
        else:
            rows.append([
                label, str(s.k), str(s.total_rounds), s.strategy, s.anonymity_mode,
                f"{s.final_accuracy:.4f}" if s.final_accuracy is not None else "n/a",
                f"{s.final_loss:.4f}" if s.final_loss is not None else "n/a",
                f"{s.total_seconds:.1f}",
            ])
    t = Table(rows, repeatRows=1)
    t.setStyle(TableStyle([
        ("GRID", (0, 0), (-1, -1), 0.4, colors.grey),
        ("BACKGROUND", (0, 0), (-1, 0), colors.lightblue),
        ("FONTSIZE", (0, 0), (-1, -1), 8),
    ]))
    story.append(t)
    story.append(Spacer(1, 0.3 * inch))
    png = chart_generator.compare_overlay_png(bundles, metric)
    story.append(Image(io.BytesIO(png), width=7 * inch, height=4.3 * inch))
    doc.build(story)
    return buf.getvalue()
