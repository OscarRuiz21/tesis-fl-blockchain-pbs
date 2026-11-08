#!/bin/bash
#
# network-up.sh: levanta la red HLF, crea el channel y une los 2 peers.
#
# Topología: 3 orderers Raft, Org1 y Org2 con un peer cada una.
#
# Requiere haber corrido generate-artifacts.sh y Docker Compose 2.17 o
# posterior (por --wait). Los anchor peers se aplican después con
# scripts/apply-anchor-peers.sh. El cli usa la identidad de Org2; para Org1
# se sobrescriben las variables CORE_PEER_* en docker exec.
#
set -euo pipefail

# cwd = raíz del proyecto, donde está docker-compose.yml.
cd "$(dirname "$0")/../.."

if [ ! -d "network/organizations" ]; then
    echo "ERROR: network/organizations/ no existe, ejecuta ./network/scripts/generate-artifacts.sh primero" >&2
    exit 1
fi

# Solo servicios de Fabric; las aplicaciones las levanta run-experiment.sh.
HLF_SERVICES=(
    orderer0.fl.network
    orderer1.fl.network
    orderer2.fl.network
    peer0.org1.fl.network
    peer0.org2.fl.network
    cli
)

ORDERER_TLS_CA="/opt/fabric/organizations/ordererOrganizations/fl.network/orderers/orderer0.fl.network/msp/tlscacerts/tlsca.fl.network-cert.pem"

ORG1_ENV=(
    -e CORE_PEER_LOCALMSPID=Org1MSP
    -e CORE_PEER_ADDRESS=peer0.org1.fl.network:7051
    -e CORE_PEER_TLS_ROOTCERT_FILE=/opt/fabric/organizations/peerOrganizations/org1.fl.network/peers/peer0.org1.fl.network/tls/ca.crt
    -e CORE_PEER_MSPCONFIGPATH=/opt/fabric/organizations/peerOrganizations/org1.fl.network/users/Admin@org1.fl.network/msp
)

# --wait espera a que todos los servicios pasen su healthcheck; 90 s dan
# margen al arranque del clúster Raft.
echo "==> docker compose up -d --wait (servicios HLF)..."
docker compose up -d --wait --wait-timeout 90 "${HLF_SERVICES[@]}"

# En Docker Desktop para macOS, el bind mount ./network/organizations del cli
# puede aparecer vacío si el contenedor arrancó justo después de cryptogen
# (caché de virtiofs). Reiniciar el cli lo refresca.
echo "==> Refrescando bind mount del cli (workaround Docker Desktop)..."
docker compose restart cli >/dev/null 2>&1
docker compose exec -T cli ls /opt/fabric/organizations/peerOrganizations/ >/dev/null

# La solicitud va a orderer0, que la reenvía al líder Raft. Mientras no hay
# líder el orderer responde "no Raft leader"; se reintenta hasta 60 s.
echo "==> Creando el channel fl-channel (esperando líder Raft)..."
created=0
err_file=$(mktemp)
for _ in $(seq 1 30); do
    if docker exec fl-network-cli peer channel create \
        -o orderer0.fl.network:7050 \
        -c fl-channel \
        -f /opt/fabric/channel-artifacts/fl-channel.tx \
        --tls --cafile "${ORDERER_TLS_CA}" \
        --outputBlock /opt/fabric/channel-artifacts/fl-channel.block 2>"$err_file"; then
        created=1
        break
    fi
    grep -q "no Raft leader" "$err_file" || { cat "$err_file" >&2; exit 1; }
    sleep 2
done
if [ "$created" -ne 1 ]; then
    echo "ERROR: el orderer no eligió líder Raft en 60 s" >&2
    exit 1
fi

echo "==> Joining peer0.org2 al channel (identidad default Org2)..."
docker exec fl-network-cli peer channel join \
    -b /opt/fabric/channel-artifacts/fl-channel.block

echo "==> Joining peer0.org1 al channel (env-override Org1)..."
docker exec "${ORG1_ENV[@]}" fl-network-cli peer channel join \
    -b /opt/fabric/channel-artifacts/fl-channel.block

echo ""
echo "Done. Network up con 3 orderers + Org1 + Org2."
echo "Próximo paso: ./network/scripts/deploy-chaincode.sh"
