# Imagen JRE del cliente FL con el entorno Python de entrenamiento. El
# contexto de construcción es la raíz del proyecto, para alcanzar
# python/requirements.txt.

# Etapa de compilación
FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /build

COPY client/pom.xml .
RUN mvn -B -q dependency:go-offline || true

COPY client/src src
RUN mvn -B package -DskipTests

# Etapa de ejecución. El venv vive en /opt/venv/, dentro de la imagen y
# fuera del bind mount /opt/python/.
FROM eclipse-temurin:21.0.5_11-jre

RUN apt-get update && apt-get install -y --no-install-recommends \
        python3 python3-venv python3-pip libgomp1 \
    && rm -rf /var/lib/apt/lists/*

COPY python/requirements.txt /tmp/requirements.txt
RUN python3 -m venv /opt/venv \
    && /opt/venv/bin/pip install --no-cache-dir --upgrade pip wheel \
    && /opt/venv/bin/pip install --no-cache-dir -r /tmp/requirements.txt \
    && rm /tmp/requirements.txt

ENV PATH="/opt/venv/bin:${PATH}"

WORKDIR /app
COPY --from=builder /build/target/client-*.jar app.jar

# train.py se monta en /opt/python/train.py desde el repo. El gateway deriva
# PYTHONPATH como el abuelo de scriptPath (/opt/python/train.py da /opt).

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
