#!/usr/bin/env bash
# Levanta el portal (backend FastAPI y frontend Next.js) con el profile
# `portal` del compose. Requiere la red HLF y las imágenes de las aplicaciones
# (start-network.sh y build-all.sh).
set -euo pipefail

cd "$(dirname "$0")/.."

if ! docker compose ps -q coordinator | grep -q .; then
  echo "[start-portal] WARN: coordinator no parece estar arriba."
  echo "[start-portal] Levanta primero la red: ./scripts/start-network.sh"
fi

echo "[start-portal] Building portal images..."
docker compose --profile portal build fl-portal-backend fl-portal-frontend

echo "[start-portal] Starting portal services..."
docker compose --profile portal up -d --wait fl-portal-backend fl-portal-frontend

cat <<EOF

[start-portal] OK: Portal arriba

  Backend (API):  http://localhost:${PORTAL_BACKEND_PORT:-8000}/api/health
  Frontend (UI):  http://localhost:${PORTAL_FRONTEND_PORT:-3000}

EOF
