#!/bin/bash
#
# generate-artifacts.sh: genera crypto material y bloques iniciales de la red.
#
# Requiere cryptogen y configtxgen de Fabric 2.5.
#
# Genera:
#   organizations/                     MSP y TLS de orderers y peers
#   channel-artifacts/genesis.block    bloque génesis del orderer
#   channel-artifacts/fl-channel.tx    transacción de creación del canal
#
# Si organizations/ ya existe, se detiene en lugar de sobrescribirlo.
#
set -euo pipefail

# La ruta del script se resuelve antes del cd: $(dirname $0) es relativa al
# directorio actual y dejaría de servir después.
_SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

cd "$_SCRIPT_DIR/.."

# shellcheck disable=SC1091
source "$_SCRIPT_DIR/../../scripts/lib/fabric-bin.sh"
ensure_fabric_bin
export PATH="${FABRIC_BIN_PATH}:${PATH}"

if [ -d "organizations" ]; then
    echo "ERROR: organizations/ ya existe. Bórralo primero: rm -rf organizations/ channel-artifacts/" >&2
    exit 1
fi

echo "==> 1/3 cryptogen: generando MSP/TLS..."
cryptogen generate \
    --config=./crypto-config.yaml \
    --output="./organizations"

echo "==> 2/3 configtxgen: bloque genesis del orderer..."
mkdir -p channel-artifacts
configtxgen \
    -profile FlOrdererGenesis \
    -channelID system-channel \
    -outputBlock ./channel-artifacts/genesis.block \
    -configPath ./configtx

echo "==> 3/3 configtxgen: transacción de creación de fl-channel..."
configtxgen \
    -profile FlChannel \
    -channelID fl-channel \
    -outputCreateChannelTx ./channel-artifacts/fl-channel.tx \
    -configPath ./configtx

echo "Done. Artifacts ready in organizations/ and channel-artifacts/"
