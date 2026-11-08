#!/bin/bash
#
# network-down.sh: apaga la red HLF y borra volúmenes y material criptográfico.
#
# Reinicio completo e irreversible. Solo afecta a los servicios de Fabric
# (orderers, peers, cli y chaincode CCaaS); el AS, los clientes y el
# coordinator los detiene run-experiment.sh.
#
set -euo pipefail

# cwd = raíz del proyecto.
cd "$(dirname "$0")/../.."

# Con el mismo COMPOSE_PROJECT_NAME que usó network-up.sh; con otro, los
# volúmenes nombrados quedarían huérfanos.
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

echo "==> Deteniendo la red..."
docker compose --profile ccaas stop "${HLF_SERVICES[@]}" 2>/dev/null || true

echo "==> Borrando los contenedores de la red..."
# -v borra los volúmenes: si el ledger sobreviviera, el siguiente experimento
# arrancaría con estado viejo del chaincode y del canal.
docker compose --profile ccaas rm -fv "${HLF_SERVICES[@]}" 2>/dev/null || true

# `rm -v` solo borra volúmenes anónimos; los del ledger tienen nombre y, si se
# conservaran, el orderer arrancaría con el bloque génesis de la CA anterior.
echo "==> Borrando los volúmenes del ledger..."
for v in orderer0.fl.network orderer1.fl.network orderer2.fl.network \
         peer0.org1.fl.network peer0.org2.fl.network; do
    docker volume rm -f "${COMPOSE_PROJECT_NAME:-fl-pbs}_${v}" >/dev/null 2>&1 || true
done

# Los certificados mTLS del AS y del coordinator los firma la CA TLS de Org2,
# que se regenera junto con organizations/; si se conservaran, quedarían
# firmados por una CA que ya no existe.
echo "==> Borrando el material criptográfico generado..."
rm -rf network/organizations network/channel-artifacts network/as-tls network/coordinator-tls

echo "Done. Red apagada y crypto material limpiado."
