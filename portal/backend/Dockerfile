# Compilación de wheels
FROM python:3.11-slim AS builder

WORKDIR /build
RUN apt-get update && apt-get install -y --no-install-recommends \
    gcc g++ && rm -rf /var/lib/apt/lists/*

COPY pyproject.toml ./
RUN pip install --no-cache-dir --upgrade pip setuptools wheel \
 && pip wheel --no-cache-dir --wheel-dir=/wheels \
    fastapi>=0.115 \
    "uvicorn[standard]>=0.32" \
    pydantic>=2.9 \
    pydantic-settings>=2.6 \
    httpx>=0.27 \
    pyyaml>=6.0.2 \
    sse-starlette>=2.1 \
    pandas>=2.2 \
    numpy>=1.26 \
    matplotlib>=3.9 \
    plotly>=5.24 \
    kaleido==0.2.1 \
    reportlab>=4.2 \
    sqlmodel>=0.0.22 \
    docker>=7.1 \
    python-multipart>=0.0.12

# Imagen de ejecución
FROM python:3.11-slim

# bash + curl + docker CLI (sin daemon) para invocar `docker compose` y orquestar
# preflight automático (recrear AS, escalar fl-client a K, etc.). El daemon
# vive en el host y se accede vía /var/run/docker.sock mount.
RUN apt-get update && apt-get install -y --no-install-recommends \
    bash curl ca-certificates gnupg \
 && install -m 0755 -d /etc/apt/keyrings \
 && curl -fsSL https://download.docker.com/linux/debian/gpg | gpg --dearmor -o /etc/apt/keyrings/docker.gpg \
 && chmod a+r /etc/apt/keyrings/docker.gpg \
 && echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/debian bookworm stable" \
    > /etc/apt/sources.list.d/docker.list \
 && apt-get update && apt-get install -y --no-install-recommends docker-ce-cli docker-compose-plugin \
 && rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY --from=builder /wheels /wheels
RUN pip install --no-cache-dir --no-index --find-links=/wheels /wheels/*.whl \
 && rm -rf /wheels

COPY app/ ./app/
COPY pyproject.toml ./

# Corre como root porque el portal necesita acceso al docker.sock del host para
# orquestar `docker compose` desde el preflight. Aceptable para uso local del
# operador, documentado como limitación de seguridad.
EXPOSE 8000

HEALTHCHECK --interval=10s --timeout=5s --start-period=20s --retries=5 \
    CMD curl -fsS http://localhost:8000/api/health || exit 1

ENV PYTHONUNBUFFERED=1 \
    PYTHONDONTWRITEBYTECODE=1

CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]
