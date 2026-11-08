#!/bin/bash
#
# gen-certs.sh: genera los certificados mTLS del AS y del coordinator.
#
# Los firma la CA TLS de Org2 que generó cryptogen, así toda la red comparte
# una sola PKI. Si un certificado ya existe, no lo regenera.
#
# Salida:
#   network/as-tls/           cert.pem, key.pem, tlsca.pem, keystores PKCS12 (CN=fl-auth-server)
#   network/coordinator-tls/  cert.pem, key.pem, ca.pem (CN=coordinator)
#
set -euo pipefail

cd "$(dirname "$0")/.."

ORG2_TLSCA_DIR="network/organizations/peerOrganizations/org2.fl.network/tlsca"
ORG2_TLSCA_CERT="${ORG2_TLSCA_DIR}/tlsca.org2.fl.network-cert.pem"
ORG2_TLSCA_KEY="$(find "${ORG2_TLSCA_DIR}" -name '*_sk' 2>/dev/null | head -n 1)"

if [ ! -f "${ORG2_TLSCA_CERT}" ] || [ ! -f "${ORG2_TLSCA_KEY:-}" ]; then
    echo "ERROR: TLS CA Org2 no disponible. Ejecuta ./network/scripts/generate-artifacts.sh primero." >&2
    exit 1
fi

# Genera llave, CSR y certificado firmado con el CN indicado.
generate_cert() {
    local cn="$1"
    local out_dir="$2"
    local san_dns="${3:-${cn}}"

    if [ -f "${out_dir}/cert.pem" ]; then
        echo "  Skip (ya existe): ${out_dir}/cert.pem"
        return 0
    fi

    mkdir -p "${out_dir}"

    # P-256, la misma curva que usa el TLS de Fabric.
    openssl ecparam -name prime256v1 -genkey -noout -out "${out_dir}/key.pem"

    openssl req -new \
        -key "${out_dir}/key.pem" \
        -out "${out_dir}/csr.pem" \
        -subj "/CN=${cn}/O=org2.fl.network" \
        -addext "subjectAltName=DNS:${san_dns},DNS:localhost"

    # El SAN se repite en un extfile porque algunas versiones de OpenSSL no
    # copian -addext al firmar.
    cat > "${out_dir}/ext.cnf" <<EOF
subjectAltName = DNS:${san_dns},DNS:localhost
EOF

    openssl x509 -req -days 365 \
        -in "${out_dir}/csr.pem" \
        -CA "${ORG2_TLSCA_CERT}" \
        -CAkey "${ORG2_TLSCA_KEY}" \
        -CAcreateserial \
        -out "${out_dir}/cert.pem" \
        -extfile "${out_dir}/ext.cnf"

    # openssl ecparam produce SEC1 y Java solo lee PKCS8.
    openssl pkcs8 -topk8 -nocrypt \
        -in "${out_dir}/key.pem" \
        -out "${out_dir}/key-pkcs8.pem"
    mv "${out_dir}/key-pkcs8.pem" "${out_dir}/key.pem"

    rm -f "${out_dir}/csr.pem" "${out_dir}/ext.cnf" "${ORG2_TLSCA_DIR}/.srl" 2>/dev/null || true

    # La CA junto al certificado, para que cada servicio arme su truststore.
    cp "${ORG2_TLSCA_CERT}" "${out_dir}/tlsca.pem" 2>/dev/null || true
    cp "${ORG2_TLSCA_CERT}" "${out_dir}/ca.pem" 2>/dev/null || true

    echo "  OK: ${out_dir}/cert.pem (CN=${cn})"
}

echo "==> Generando cert AS server (CN=fl-auth-server)..."
generate_cert "fl-auth-server" "network/as-tls" "fl-auth-server"

echo "==> Generando cert coordinator client (CN=coordinator)..."
generate_cert "coordinator" "network/coordinator-tls" "coordinator"

# El AS (Spring Boot) carga su TLS desde server.keystore.p12 y truststore.p12.
P12_PASS="changeit"
AS_DIR="network/as-tls"

if [ ! -f "${AS_DIR}/server.keystore.p12" ]; then
    echo "==> Generando AS server.keystore.p12 (cert + key del AS)..."
    openssl pkcs12 -export \
        -in "${AS_DIR}/cert.pem" \
        -inkey "${AS_DIR}/key.pem" \
        -out "${AS_DIR}/server.keystore.p12" \
        -password "pass:${P12_PASS}" \
        -name fl-auth-server
    echo "  OK: ${AS_DIR}/server.keystore.p12"
fi

if [ ! -f "${AS_DIR}/truststore.p12" ]; then
    echo "==> Generando AS truststore.p12 (CA Org2)..."
    # keytool viene con la JDK del host.
    keytool -import -trustcacerts -noprompt \
        -alias org2-tlsca \
        -file "${AS_DIR}/tlsca.pem" \
        -keystore "${AS_DIR}/truststore.p12" \
        -storetype PKCS12 \
        -storepass "${P12_PASS}" \
        2>&1 | grep -v "^Warning" || true
    echo "  OK: ${AS_DIR}/truststore.p12"
fi

echo ""
echo "Done. Certs generados en network/as-tls/ y network/coordinator-tls/."
