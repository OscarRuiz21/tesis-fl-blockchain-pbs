# Chaincode flprotocol en modo CCaaS (Chaincode as a Service), según el patrón
# de la documentación de Hyperledger Fabric 2.4.1+.
#
# No se usa tini: su binario es solo x86_64 y en imágenes linux/arm64 sobre
# Apple Silicon termina con exit 133 ("rosetta error"). El reenvío de señales
# lo hace `init: true` en el compose.
#
# Contexto de build: chaincode/flprotocol/
#   docker build -t flprotocol-chaincode:1.0 chaincode/flprotocol/

# Etapa de compilación
FROM gradle:8.6-jdk21 AS builder
WORKDIR /home/gradle

COPY --chown=gradle:gradle build.gradle settings.gradle* ./
COPY --chown=gradle:gradle src src
RUN gradle --no-daemon shadowJar -x test

# Etapa de ejecución: servidor gRPC del chaincode
FROM eclipse-temurin:21.0.5_11-jre

WORKDIR /app
COPY --from=builder /home/gradle/build/libs/chaincode.jar /app/chaincode.jar

# Puerto gRPC al que apunta el connection.json del paquete CCaaS.
EXPOSE 9999

# Variables que define el compose:
#   CHAINCODE_SERVER_ADDRESS=0.0.0.0:9999
#   CORE_CHAINCODE_ID_NAME=<package-id>
ENTRYPOINT ["java", "-jar", "/app/chaincode.jar"]
