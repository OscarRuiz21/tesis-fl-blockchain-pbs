"""Tail-follow async de los log files de los runs para SSE."""
from __future__ import annotations

import asyncio
from pathlib import Path
from typing import AsyncIterator


async def tail_log(path: Path, *, poll_interval: float = 0.5) -> AsyncIterator[str]:
    """Yield líneas del archivo a medida que aparecen. Termina cuando el run termina
    (heurística: 2 segundos sin nuevas líneas + sentinel `# done` o EOF estable).

    Para SSE simplicidad: el caller cierra cuando el RunState es terminal.
    """
    # Espera que el archivo aparezca (hasta 30s)
    waited = 0.0
    while not path.exists() and waited < 30.0:
        await asyncio.sleep(poll_interval)
        waited += poll_interval
    if not path.exists():
        yield f"# log file not found: {path}\n"
        return

    with path.open("r", encoding="utf-8", errors="replace") as f:
        while True:
            line = f.readline()
            if line:
                yield line
            else:
                await asyncio.sleep(poll_interval)


async def read_log_snapshot(path: Path, *, max_bytes: int = 256 * 1024) -> str:
    if not path.exists():
        return ""
    with path.open("r", encoding="utf-8", errors="replace") as f:
        f.seek(0, 2)
        size = f.tell()
        start = max(0, size - max_bytes)
        f.seek(start)
        return f.read()
