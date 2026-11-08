#!/bin/bash
#
# prepare-data.sh: genera las particiones de datos de un experimento.
#
# Lee el YAML del experimento con yq y ejecuta simulate_clients.py en el host
# (o en el contenedor fl-data-prep). Si las particiones ya existen, no hace nada.
#
# Uso: ./scripts/prepare-data.sh experiments/<scenario>.yml
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
    echo "ERROR: 'yq' no instalado. Instálalo con 'brew install yq' o desde https://github.com/mikefarah/yq/releases" >&2
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

K=$(yq -r '.federation.num_clients' "${SPEC_FILE}")
SEED=$(yq -r '.experiment.seed' "${SPEC_FILE}")
STRATEGY=$(yq -r '.partition.strategy' "${SPEC_FILE}")
ALPHA=$(yq -r '.partition.alpha // 0.5' "${SPEC_FILE}")
LABEL=$(yq -r '.experiment.label' "${SPEC_FILE}")
TARGET_COL=$(yq -r '.model.hyperparams.target' "${SPEC_FILE}")

DATASET_NAME=$(yq -r '.dataset.name // "uci_credit"' "${SPEC_FILE}")

# Archivo preprocesado de cada dataset y cómo generarlo si falta.
case "${DATASET_NAME}" in
    give_me_some_credit)
        PROCESSED_FILE="gmsc_processed.csv"
        SYNTH_HINT="python/.venv/bin/python python/tests/generate_synthetic_gmsc.py --rows 2000 --output python/data/give_me_some_credit/cs-training.csv"
        PREPROC_HINT="python/.venv/bin/python python/preprocess_gmsc.py --csv_path python/data/give_me_some_credit/cs-training.csv --output python/data/give_me_some_credit/gmsc_processed.csv"
        ;;
    uci_credit)
        PROCESSED_FILE="uci_credit_processed.csv"
        SYNTH_HINT="./scripts/download-uci.sh"
        PREPROC_HINT="python/.venv/bin/python python/preprocess_uci_credit.py --csv_path python/data/uci_credit/uci_credit_raw.csv --output python/data/uci_credit/uci_credit_processed.csv"
        ;;
    *)
        echo "ERROR: dataset.name='${DATASET_NAME}' desconocido en ${SPEC_FILE}" >&2
        echo "Datasets soportados: give_me_some_credit, uci_credit" >&2
        exit 1
        ;;
esac

DATA_BASE="python/data/${DATASET_NAME}"
SOURCE_CSV="${DATA_BASE}/${PROCESSED_FILE}"
TEST_CSV="${DATA_BASE}/test.csv"

if [ ! -f "${SOURCE_CSV}" ]; then
    echo "ERROR: dataset preprocesado no existe: ${SOURCE_CSV}" >&2
    echo "Genéralo con:" >&2
    echo "  ${SYNTH_HINT}" >&2
    echo "  ${PREPROC_HINT}" >&2
    exit 1
fi

# Nombre del directorio de la partición.
if [ "${STRATEGY}" = "dirichlet" ]; then
    SUFFIX="K${K}_seed${SEED}_dirichlet_alpha${ALPHA}"
else
    SUFFIX="K${K}_seed${SEED}_${STRATEGY}"
fi
PART_DIR="${DATA_BASE}/partitions/${SUFFIX}"

if [ -d "${PART_DIR}" ] && [ -f "${PART_DIR}/client-1.csv" ]; then
    echo "==> Particiones ya existen: ${PART_DIR} (skip)"
    exit 0
fi

# Por defecto usa el venv del host. El contenedor fl-data-prep, que el portal
# usa para preparar particiones, define $PYTHON=python3.
VENV_PYTHON="${PYTHON:-python/.venv/bin/python}"
if [ ! -x "${VENV_PYTHON}" ] && ! command -v "${VENV_PYTHON}" >/dev/null 2>&1; then
    echo "ERROR: Python no encontrado: ${VENV_PYTHON}" >&2
    echo "  En host: python3.11 -m venv python/.venv && python/.venv/bin/pip install -r python/requirements.txt" >&2
    echo "  En container: setear \$PYTHON=python3" >&2
    exit 1
fi

EXTRA_ARGS=()
if [ "${STRATEGY}" = "dirichlet" ]; then
    EXTRA_ARGS+=(--dirichlet_alpha "${ALPHA}")
fi

echo "==> Generando particiones K=${K} ${STRATEGY} -> ${PART_DIR}/"
PYTHONPATH=. "${VENV_PYTHON}" python/simulate_clients.py \
    --source "${SOURCE_CSV}" \
    --output_dir "${DATA_BASE}/partitions" \
    --K "${K}" \
    --seed "${SEED}" \
    --distribution "${STRATEGY}" \
    --target_col "${TARGET_COL}" \
    --test_ratio 0.2 \
    --test_out "${TEST_CSV}" \
    ${EXTRA_ARGS[@]+"${EXTRA_ARGS[@]}"}

echo "Done. Partitions en ${PART_DIR}/"
