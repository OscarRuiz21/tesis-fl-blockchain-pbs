#!/bin/bash
#
# stop-network.sh: detiene solo los servicios de Fabric.
#
# Conserva las aplicaciones y los volúmenes. Para borrarlo todo:
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

HLF_SERVICES=(
    orderer0.fl.network
    orderer1.fl.network
    orderer2.fl.network
    peer0.org1.fl.network
    peer0.org2.fl.network
    cli
    chaincode.flprotocol.fl.network
)

echo "==> Stopping HLF services..."
docker compose --profile ccaas stop "${HLF_SERVICES[@]}" 2>/dev/null || true
docker compose --profile ccaas rm -f "${HLF_SERVICES[@]}" 2>/dev/null || true

echo "Done. Apps preservadas. Volúmenes preservados."
