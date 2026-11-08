#!/bin/bash
#
# logs.sh: tail de logs de los servicios.
#
# Uso:
#   ./scripts/logs.sh                          # todos
#   ./scripts/logs.sh coordinator              # uno solo
#   ./scripts/logs.sh fl-auth-server fl-client # varios
#
set -euo pipefail

cd "$(dirname "$0")/.."

docker compose logs --tail 100 -f "$@"
