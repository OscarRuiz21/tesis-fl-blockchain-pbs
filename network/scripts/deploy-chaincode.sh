#!/bin/bash
#
# deploy-chaincode.sh: despliega flprotocol como Chaincode-as-a-Service (CCaaS).
#
# El chaincode corre como servidor gRPC en su propio contenedor
# (chaincode.flprotocol.fl.network); el peer solo recibe un paquete con la
# dirección de ese servidor. Con la instalación clásica, Docker Desktop en
# macOS cortaba la conexión (broken pipe) al subir el shadowJar de 38 MB.
#
# Requiere que network-up.sh haya creado el canal y unido los dos peers.
# La política de endorsement sale de ENDORSEMENT_POLICY (.env de la raíz);
# por defecto OR('Org2MSP.peer').
#
set -euo pipefail

# cwd = raíz del proyecto.
cd "$(dirname "$0")/../.."

# Si se invoca por separado, toma COMPOSE_PROJECT_NAME y ENDORSEMENT_POLICY
# del .env sin pisar las variables ya definidas en el shell.
if [ -z "${COMPOSE_PROJECT_NAME:-}" ] || [ -z "${ENDORSEMENT_POLICY:-}" ]; then
    if [ -f .env ]; then
        while IFS='=' read -r k v; do
            [[ "$k" =~ ^[[:space:]]*# ]] && continue
            [ -z "$k" ] && continue
            v="${v%%#*}"
            v="${v%"${v##*[![:space:]]}"}"
            v="${v#\"}"; v="${v%\"}"
            v="${v#\'}"; v="${v%\'}"
            if [ -z "${!k:-}" ]; then export "$k=$v"; fi
        done < .env
    fi
fi

CC_NAME="flprotocol"
CC_VERSION="1.0"
CC_LABEL="${CC_NAME}_${CC_VERSION}"
CC_ENDPOINT="chaincode.flprotocol.fl.network:9999"
CC_IMAGE_TAG="flprotocol-chaincode:1.0"
ENDORSEMENT_POLICY="${ENDORSEMENT_POLICY:-OR('Org2MSP.peer')}"

# El peer rechaza approve/commit si la sequence no es exactamente la última
# confirmada + 1. Se detecta con querycommitted; CC_SEQUENCE=N la fuerza.
detect_next_sequence() {
    local last_seq
    last_seq=$(docker exec fl-network-cli peer lifecycle chaincode querycommitted \
        --channelID fl-channel --name "${CC_NAME}" 2>/dev/null \
        | grep -oE "Sequence: [0-9]+" | awk '{print $2}' | head -n 1 || true)
    if [ -z "${last_seq:-}" ]; then
        echo "1"
    else
        echo "$((last_seq + 1))"
    fi
}
CC_SEQUENCE="${CC_SEQUENCE:-$(detect_next_sequence)}"
echo "==> Deploy con sequence=${CC_SEQUENCE} (last committed + 1)"

ORDERER_TLS_CA="/opt/fabric/organizations/ordererOrganizations/fl.network/orderers/orderer0.fl.network/msp/tlscacerts/tlsca.fl.network-cert.pem"
ORG1_PEER_TLS_CA="/opt/fabric/organizations/peerOrganizations/org1.fl.network/peers/peer0.org1.fl.network/tls/ca.crt"
ORG2_PEER_TLS_CA="/opt/fabric/organizations/peerOrganizations/org2.fl.network/peers/peer0.org2.fl.network/tls/ca.crt"

ORG1_ENV=(
    -e CORE_PEER_LOCALMSPID=Org1MSP
    -e CORE_PEER_ADDRESS=peer0.org1.fl.network:7051
    -e CORE_PEER_TLS_ROOTCERT_FILE=/opt/fabric/organizations/peerOrganizations/org1.fl.network/peers/peer0.org1.fl.network/tls/ca.crt
    -e CORE_PEER_MSPCONFIGPATH=/opt/fabric/organizations/peerOrganizations/org1.fl.network/users/Admin@org1.fl.network/msp
)

# Compilación del jar e imagen Docker del chaincode, en el host.
echo "==> [1/7] Building chaincode JAR (gradle shadowJar)..."
(cd chaincode/flprotocol && gradle shadowJar -x test --no-daemon)

echo "==> [2/7] Building chaincode Docker image (${CC_IMAGE_TAG})..."
docker build -q -t "${CC_IMAGE_TAG}" chaincode/flprotocol/

# Paquete CCaaS: metadata.json (tipo y etiqueta) más code.tar.gz, que solo
# contiene connection.json con la dirección del servidor del chaincode.
echo "==> [3/7] Generando paquete CCaaS..."
PKG_DIR="$(mktemp -d -t ccaas-pkg-XXXXXX)"
trap 'rm -rf "$PKG_DIR"' EXIT

cat > "${PKG_DIR}/metadata.json" <<EOF
{
  "type": "ccaas",
  "label": "${CC_LABEL}"
}
EOF

mkdir -p "${PKG_DIR}/code"
cat > "${PKG_DIR}/code/connection.json" <<EOF
{
  "address": "${CC_ENDPOINT}",
  "dial_timeout": "10s",
  "tls_required": false
}
EOF

(cd "${PKG_DIR}/code" && tar -czf ../code.tar.gz connection.json)
PKG_PATH="network/channel-artifacts/${CC_LABEL}.tar.gz"
mkdir -p network/channel-artifacts
(cd "${PKG_DIR}" && tar -czf "${OLDPWD}/${PKG_PATH}" metadata.json code.tar.gz)
echo "    Paquete: ${PKG_PATH} ($(du -h "${PKG_PATH}" | cut -f1))"

# Ruta dentro del cli, que monta network/channel-artifacts/ en /opt/fabric/channel-artifacts/.
PKG_PATH_CLI="/opt/fabric/channel-artifacts/${CC_LABEL}.tar.gz"

# Instalación en ambos peers. El paquete pesa ~1 KB, así que no pasa por el
# problema de docker.sock.
echo "==> [4/7] Installing en peer0.org2..."
docker exec fl-network-cli peer lifecycle chaincode install "${PKG_PATH_CLI}"

echo "==> [4/7] Installing en peer0.org1..."
docker exec "${ORG1_ENV[@]}" fl-network-cli peer lifecycle chaincode install "${PKG_PATH_CLI}"

# CHAINCODE_ID (etiqueta:hash) del paquete instalado.
echo "==> [5/7] Query installed (CHAINCODE_ID)..."
CHAINCODE_ID=$(docker exec fl-network-cli peer lifecycle chaincode queryinstalled \
    | grep "${CC_LABEL}" | awk '{print $3}' | sed 's/,//' | head -n 1)
if [ -z "${CHAINCODE_ID}" ]; then
    echo "ERROR: no se obtuvo CHAINCODE_ID, ¿falló el install?" >&2
    exit 1
fi
echo "    CHAINCODE_ID=${CHAINCODE_ID}"

# El contenedor del chaincode arranca con el CHAINCODE_ID recién obtenido.
echo "==> [6/7] Levantando chaincode service (${CC_ENDPOINT})..."
CHAINCODE_ID="${CHAINCODE_ID}" docker compose \
    --profile ccaas up -d \
    --wait --wait-timeout 60 \
    chaincode.flprotocol.fl.network

echo "==> [7/7] Approve para Org2..."
docker exec fl-network-cli peer lifecycle chaincode approveformyorg \
    -o orderer0.fl.network:7050 \
    --channelID fl-channel \
    --name ${CC_NAME} \
    --version ${CC_VERSION} \
    --sequence ${CC_SEQUENCE} \
    --package-id "${CHAINCODE_ID}" \
    --signature-policy "${ENDORSEMENT_POLICY}" \
    --tls --cafile "${ORDERER_TLS_CA}"

echo "==> [7/7] Approve para Org1..."
docker exec "${ORG1_ENV[@]}" fl-network-cli peer lifecycle chaincode approveformyorg \
    -o orderer0.fl.network:7050 \
    --channelID fl-channel \
    --name ${CC_NAME} \
    --version ${CC_VERSION} \
    --sequence ${CC_SEQUENCE} \
    --package-id "${CHAINCODE_ID}" \
    --signature-policy "${ENDORSEMENT_POLICY}" \
    --tls --cafile "${ORDERER_TLS_CA}"

echo "==> Commit (endorsement: ${ENDORSEMENT_POLICY})..."
docker exec fl-network-cli peer lifecycle chaincode commit \
    -o orderer0.fl.network:7050 \
    --channelID fl-channel \
    --name ${CC_NAME} \
    --version ${CC_VERSION} \
    --sequence ${CC_SEQUENCE} \
    --signature-policy "${ENDORSEMENT_POLICY}" \
    --tls --cafile "${ORDERER_TLS_CA}" \
    --peerAddresses peer0.org1.fl.network:7051 \
    --tlsRootCertFiles "${ORG1_PEER_TLS_CA}" \
    --peerAddresses peer0.org2.fl.network:9051 \
    --tlsRootCertFiles "${ORG2_PEER_TLS_CA}"

echo ""
echo "Done. Chaincode '${CC_NAME}' deployado vía CCaaS con política:"
echo "  ${ENDORSEMENT_POLICY}"
echo ""
echo "Verificar:"
echo "  docker exec fl-network-cli peer chaincode query -C fl-channel -n ${CC_NAME} -c '{\"Args\":[\"queryRoundStatus\"]}'"
