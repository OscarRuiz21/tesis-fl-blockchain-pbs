# Contexto de build: el directorio `as/`; no depende de otros módulos.

# Etapa de compilación
FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /build

# Copiar primero el POM deja las dependencias en una capa cacheable.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline || true

COPY src src
RUN mvn -B package -DskipTests

# Etapa de ejecución, con la JRE fijada por reproducibilidad.
FROM eclipse-temurin:21.0.5_11-jre
WORKDIR /app

COPY --from=builder /build/target/as-*.jar app.jar

# El healthcheck solo abre el puerto TCP: el AS exige certificado mTLS y
# curl/wget dentro del contenedor no lo presentan.
HEALTHCHECK --interval=10s --timeout=3s --start-period=20s --retries=5 \
    CMD bash -c 'echo > /dev/tcp/localhost/8443' || exit 1

EXPOSE 8443
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
