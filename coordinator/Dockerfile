# Imagen del coordinator: JRE más un venv de Python para correr eval.py en
# cada ronda. Como en fl-client, el venv vive en la imagen (/opt/venv) y los
# scripts llegan por bind mount a /opt/python.
#
# El contexto de build es la raíz del proyecto, para alcanzar
# python/requirements.txt.

# Etapa de compilación
FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /build

COPY coordinator/pom.xml .
RUN mvn -B -q dependency:go-offline || true

COPY coordinator/src src
RUN mvn -B package -DskipTests

# Etapa de ejecución
FROM eclipse-temurin:21.0.5_11-jre

RUN apt-get update && apt-get install -y --no-install-recommends \
        python3 \
        python3-venv \
        python3-pip \
        libgomp1 \
        curl \
        ca-certificates \
    && rm -rf /var/lib/apt/lists/*

COPY python/requirements.txt /tmp/requirements.txt
RUN python3 -m venv /opt/venv \
    && /opt/venv/bin/pip install --no-cache-dir --upgrade pip wheel \
    && /opt/venv/bin/pip install --no-cache-dir -r /tmp/requirements.txt \
    && rm /tmp/requirements.txt

ENV PATH="/opt/venv/bin:${PATH}"

WORKDIR /app
COPY --from=builder /build/target/coordinator-*.jar app.jar

# Montajes (docker-compose.yml):
#   /opt/python                 eval.py y conjuntos de prueba (ro)
#   /opt/results                CSV y run_summary.json (rw)
#   /opt/fabric/organizations   material MSP de Org2 (ro)
#   /etc/fabric/coordinator     certificado, llave y CA para el mTLS con el AS
EXPOSE 9100

# /actuator/health se sirve por HTTP sin mTLS.
HEALTHCHECK --interval=10s --timeout=3s --start-period=20s --retries=5 \
    CMD curl -fsS http://localhost:9100/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
