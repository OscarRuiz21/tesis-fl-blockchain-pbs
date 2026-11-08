#!/bin/bash
#
# build-all.sh: compila los tres módulos Maven, el chaincode y las imágenes Docker.
#
set -euo pipefail

cd "$(dirname "$0")/.."

echo "==> [1/4] Maven install: as + client + coordinator..."
for module in as client coordinator; do
    if [ -d "${module}" ] && [ -f "${module}/pom.xml" ]; then
        echo "  -> ${module}"
        (cd "${module}" && mvn -q clean install -DskipTests)
    else
        echo "  AVISO: ${module}/pom.xml no existe, skip"
    fi
done

echo "==> [2/4] Gradle shadowJar: chaincode..."
(cd chaincode/flprotocol && gradle --no-daemon shadowJar -x test -q)

echo "==> [3/4] Docker build: chaincode image..."
docker build -q -t flprotocol-chaincode:1.0 chaincode/flprotocol/

echo "==> [4/4] Docker compose build: fl-auth-server + fl-client + fl-coordinator..."
docker compose build fl-auth-server fl-client coordinator

echo ""
echo "Done. Build completo. Imágenes:"
docker images --format 'table {{.Repository}}\t{{.Tag}}\t{{.Size}}' | grep -E '^(REPOSITORY|fl-|flprotocol-)'
