#!/bin/bash
#
# fabric-bin.sh: resuelve el PATH a los binarios de Hyperledger Fabric
# (cryptogen, configtxgen, peer).
#
# Usa FABRIC_BIN_PATH si está definido; si no, busca en ubicaciones comunes
# (Homebrew, /tmp, ~/dev/...).
#
# Uso:
#   source "$(dirname "$0")/lib/fabric-bin.sh"
#   export PATH="$FABRIC_BIN_PATH:$PATH"
#

ensure_fabric_bin() {
    if [ -n "${FABRIC_BIN_PATH:-}" ] && [ -x "${FABRIC_BIN_PATH}/cryptogen" ]; then
        return 0
    fi
    if command -v cryptogen >/dev/null 2>&1; then
        FABRIC_BIN_PATH="$(dirname "$(command -v cryptogen)")"
        export FABRIC_BIN_PATH
        return 0
    fi
    local candidates=(
        "/tmp/fabric-tools/bin"
        "/opt/fabric/bin"
        "/usr/local/opt/fabric/bin"
        "$HOME/dev/tools/fabric/bin"
        "$HOME/.local/share/fabric/bin"
        "$(brew --prefix 2>/dev/null)/bin"
    )
    for c in "${candidates[@]}"; do
        if [ -x "${c}/cryptogen" ]; then
            export FABRIC_BIN_PATH="${c}"
            return 0
        fi
    done
    cat >&2 <<EOF
ERROR: No se encontró cryptogen/configtxgen en el PATH.
Setear FABRIC_BIN_PATH=/path/to/fabric/bin manualmente o instalar:
  https://github.com/hyperledger/fabric/releases/tag/v2.5.10
  (descargar hyperledger-fabric-darwin-arm64-2.5.10.tar.gz)
EOF
    return 1
}
