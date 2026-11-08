#!/bin/bash
#
# run-experiment.sh: ejecuta un experimento FL completo.
#
# Pasos:
#   [1/6] Lee el YAML del experimento con yq.
#   [2/6] prepare-data.sh genera las particiones.
#   [3/6] start-network.sh levanta red, certificados y chaincode si faltan.
#   [4/6] Crea results/<label>.
#   [5/6] Levanta el AS, K clientes y el coordinator.
#   [6/6] Envía el YAML a POST /experiments/run. El coordinator inicializa
#         el contrato y el AS, manda el start y evalúa cada ronda; no
#         participa en el protocolo.
#
# Uso: ./scripts/run-experiment.sh experiments/<scenario>.yml
#
set -euo pipefail

cd "$(dirname "$0")/.."

if [ $# -lt 1 ]; then
    echo "Uso: $0 <experiments/<scenario>.yml>" >&2
    exit 1
fi

SPEC_FILE="$1"
if [ ! -f "${SPEC_FILE}" ]; then
    echo "ERROR: spec file no existe: ${SPEC_FILE}" >&2
    exit 1
fi

if ! command -v yq >/dev/null 2>&1; then
    echo "ERROR: 'yq' no instalado. Ver scripts/prepare-data.sh." >&2
    exit 1
fi

# Carga el .env sin pisar las variables ya definidas en el shell.
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

LABEL=$(yq -r '.experiment.label' "${SPEC_FILE}")
K=$(yq -r '.federation.num_clients' "${SPEC_FILE}")
COORDINATOR_PORT="${COORDINATOR_PORT:-9100}"
TIMEOUT_EXPERIMENT="${TIMEOUT_EXPERIMENT:-3600}"

if [ -z "${LABEL}" ] || [ "${LABEL}" = "null" ]; then
    echo "ERROR: spec.experiment.label vacío en ${SPEC_FILE}" >&2
    exit 1
fi

echo "==================================================================="
echo "==  Experiment: ${LABEL}  (K=${K})"
echo "==  Spec:       ${SPEC_FILE}"
echo "==================================================================="

echo "==> [2/6] prepare-data.sh..."
./scripts/prepare-data.sh "${SPEC_FILE}"

echo "==> [3/6] start-network.sh..."
./scripts/start-network.sh

RESULTS_DIR="results/${LABEL}"
mkdir -p "${RESULTS_DIR}" reports/

echo "==> [5/6] docker compose up apps (--scale fl-client=${K})..."
docker compose up -d --wait --wait-timeout 90 \
    --scale fl-client="${K}" \
    fl-auth-server fl-client coordinator

# Margen para que el coordinator termine de arrancar.
sleep 5

echo "==> [6/6] curl POST /experiments/run (timeout ${TIMEOUT_EXPERIMENT}s)..."
RESPONSE_FILE="${RESULTS_DIR}/run_response.json"
HTTP_STATUS=$(curl -sS -o "${RESPONSE_FILE}" -w "%{http_code}" \
    -X POST "http://localhost:${COORDINATOR_PORT}/experiments/run" \
    -H "Content-Type: application/x-yaml" \
    --data-binary "@${SPEC_FILE}" \
    --max-time "${TIMEOUT_EXPERIMENT}" \
    || true)

echo ""
if [ "${HTTP_STATUS}" = "200" ]; then
    echo "==> Experiment ${LABEL} OK (HTTP 200)."
    echo "    Reporte: ${RESPONSE_FILE}"
    cat "${RESPONSE_FILE}" 2>/dev/null | head -20
    EXIT_CODE=0
else
    echo "==> Experiment ${LABEL} FAILED (HTTP ${HTTP_STATUS})." >&2
    cat "${RESPONSE_FILE}" 2>/dev/null | head -30 >&2
    EXIT_CODE=1
fi

# Se detienen las aplicaciones; la red HLF queda arriba para la siguiente corrida.
echo ""
echo "==> Stopping apps (network HLF preservado)..."
docker compose stop fl-auth-server fl-client coordinator 2>/dev/null || true
docker compose rm -f fl-auth-server fl-client coordinator 2>/dev/null || true

exit ${EXIT_CODE}
