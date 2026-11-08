#!/bin/bash
#
# apply-anchor-peers.sh: publica los anchor peers después de crear el canal.
#
# configtx.yaml los declara, pero Fabric 2.5 no los deja aplicados en el
# bloque génesis del canal. Sin ellos no hay gossip entre organizaciones y el
# primer submitUpdate falla con `FAILED_PRECONDITION: no peer combination can
# satisfy the endorsement policy`.
#
# Genera una actualización por organización con configtxgen
# -outputAnchorPeersUpdate y la aplica con peer channel update. Se puede
# repetir: si ya estaban aplicados, el update falla y el script lo tolera.
#
set -euo pipefail

cd "$(dirname "$0")/.."

# shellcheck disable=SC1091
source scripts/lib/fabric-bin.sh
ensure_fabric_bin
export PATH="${FABRIC_BIN_PATH}:${PATH}"
export FABRIC_CFG_PATH="${PWD}/network/configtx"

CHANNEL_NAME="fl-channel"
ARTIFACTS_DIR="network/channel-artifacts"
ORDERER_TLS_CA="/opt/fabric/organizations/ordererOrganizations/fl.network/orderers/orderer0.fl.network/msp/tlscacerts/tlsca.fl.network-cert.pem"

echo "==> Generando AnchorPeer updates con configtxgen..."
configtxgen -profile FlChannel -channelID "${CHANNEL_NAME}" \
    -outputAnchorPeersUpdate "${ARTIFACTS_DIR}/Org1MSPanchors.tx" \
    -asOrg Org1MSP

configtxgen -profile FlChannel -channelID "${CHANNEL_NAME}" \
    -outputAnchorPeersUpdate "${ARTIFACTS_DIR}/Org2MSPanchors.tx" \
    -asOrg Org2MSP

# Org2 con la identidad por defecto del cli.
echo "==> Aplicando Org2 anchor peers update..."
docker exec fl-network-cli peer channel update \
    -o orderer0.fl.network:7050 \
    -c "${CHANNEL_NAME}" \
    -f "/opt/fabric/channel-artifacts/Org2MSPanchors.tx" \
    --tls --cafile "${ORDERER_TLS_CA}" || echo "  (skip, anchor peers Org2 ya aplicados)"

# Org1 sobrescribiendo las variables CORE_PEER_* del cli.
echo "==> Aplicando Org1 anchor peers update..."
docker exec \
    -e CORE_PEER_LOCALMSPID=Org1MSP \
    -e CORE_PEER_ADDRESS=peer0.org1.fl.network:7051 \
    -e CORE_PEER_TLS_ROOTCERT_FILE=/opt/fabric/organizations/peerOrganizations/org1.fl.network/peers/peer0.org1.fl.network/tls/ca.crt \
    -e CORE_PEER_MSPCONFIGPATH=/opt/fabric/organizations/peerOrganizations/org1.fl.network/users/Admin@org1.fl.network/msp \
    fl-network-cli peer channel update \
    -o orderer0.fl.network:7050 \
    -c "${CHANNEL_NAME}" \
    -f "/opt/fabric/channel-artifacts/Org1MSPanchors.tx" \
    --tls --cafile "${ORDERER_TLS_CA}" || echo "  (skip, anchor peers Org1 ya aplicados)"

echo "Done. AnchorPeers aplicados (o no-op si ya estaban)."
