#!/bin/bash
#
# stop-all.sh: detiene todos los servicios conservando el estado.
#
# Sin -v, docker compose down conserva los volúmenes del ledger y el material
# criptográfico. Para borrarlo todo, correr después:
#   ./network/scripts/network-down.sh
#
set -euo pipefail

cd "$(dirname "$0")/.."

if [ -f .env ]; then
    set -a
    # shellcheck disable=SC1091
    source .env
    set +a
fi

echo "==> docker compose down --remove-orphans (preserva volúmenes named)..."
docker compose --profile ccaas down --remove-orphans

echo "Done. Para wipe completo del ledger: ./network/scripts/network-down.sh"
