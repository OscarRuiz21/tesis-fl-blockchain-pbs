#!/bin/bash
#
# start-network.sh: levanta la red HLF completa.
#
# Encadena generate-artifacts, network-up, gen-certs, deploy-chaincode y
# apply-anchor-peers. Si el chaincode ya está confirmado en el canal, no hace
# nada; si quedó material de un arranque anterior, intenta restaurar esa red.
#
set -euo pipefail

cd "$(dirname "$0")/.."

if [ -f .env ]; then
    set -a
    # shellcheck disable=SC1091
    source .env
    set +a
fi

NETWORK_SERVICES=(
    orderer0.fl.network
    orderer1.fl.network
    orderer2.fl.network
    peer0.org1.fl.network
    peer0.org2.fl.network
    cli
)

chaincode_committed() {
    docker ps --format '{{.Names}}' | grep -q "^fl-network-cli$" &&
        docker exec fl-network-cli peer lifecycle chaincode querycommitted \
            --channelID fl-channel --name flprotocol 2>/dev/null | grep -q "Sequence:"
}

chaincode_running() {
    docker ps --format '{{.Names}}' | grep -q "^chaincode.flprotocol.fl.network$"
}

if chaincode_committed && chaincode_running; then
    echo "==> La red ya está arriba con el chaincode confirmado."
    exit 0
fi

# Hay material criptográfico de un arranque anterior (por ejemplo, después de
# stop-all.sh): se levantan los contenedores sobre los volúmenes existentes y
# el chaincode con el CHAINCODE_ID que el peer tiene instalado. Si el ledger ya
# no tiene el chaincode, se borra todo y se regenera.
if [ -d network/organizations ]; then
    echo "==> Levantando la red existente..."
    docker compose up -d "${NETWORK_SERVICES[@]}"
    for _ in $(seq 1 30); do
        chaincode_committed && break
        sleep 2
    done
    if chaincode_committed; then
        CHAINCODE_ID=$(docker exec fl-network-cli peer lifecycle chaincode queryinstalled \
            | grep "flprotocol_1.0" | awk '{print $3}' | sed 's/,//' | head -n 1)
        CHAINCODE_ID="${CHAINCODE_ID}" docker compose --profile ccaas up -d \
            --wait --wait-timeout 60 chaincode.flprotocol.fl.network
        echo "==> Red restaurada con el chaincode confirmado."
        exit 0
    fi
    echo "==> El ledger no conserva el chaincode; se regenera la red desde cero."
    ./network/scripts/network-down.sh
fi

echo "==> [1/5] Generate crypto material + channel artifacts..."
./network/scripts/generate-artifacts.sh

echo "==> [2/5] Network up (3 orderers + 2 peers + cli + channel + joins)..."
./network/scripts/network-up.sh

echo "==> [3/5] Generate mTLS custom-CN certs (AS + coordinator)..."
./scripts/gen-certs.sh

echo "==> [4/5] Deploy chaincode via CCaaS..."
./network/scripts/deploy-chaincode.sh

echo "==> [5/5] Apply anchor peers..."
./scripts/apply-anchor-peers.sh

echo ""
echo "Done. Network completa lista. Estado:"
docker compose ps --format 'table {{.Name}}\t{{.Status}}\t{{.Ports}}' | head -20
