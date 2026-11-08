#!/usr/bin/env bash
# Detiene los servicios del Portal sin tocar la red HLF ni las apps.
set -euo pipefail
cd "$(dirname "$0")/.."

docker compose --profile portal stop fl-portal-backend fl-portal-frontend
docker compose --profile portal rm -f fl-portal-backend fl-portal-frontend
echo "[stop-portal] OK: Portal detenido"
