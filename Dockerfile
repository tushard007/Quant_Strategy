FROM maven:3.9.9-eclipse-temurin-21 AS builder

WORKDIR /workspace

COPY pom.xml ./

# Docker Desktop can occasionally reset long TLS downloads from Maven Central.
# Cache artifacts between builds and retry transient HTTP/TLS failures.
ENV MAVEN_OPTS="-Djava.net.preferIPv4Stack=true"
RUN --mount=type=cache,target=/root/.m2 \
    mvn dependency:go-offline -B -ntp \
      -Dmaven.wagon.http.retryHandler.count=5 \
      -Dmaven.wagon.httpconnectionManager.ttlSeconds=120 \
    || mvn dependency:go-offline -B -ntp \
      -Dmaven.wagon.http.retryHandler.count=5 \
      -Dmaven.wagon.httpconnectionManager.ttlSeconds=120

COPY frontend ./frontend
COPY src ./src

RUN --mount=type=cache,target=/root/.m2 \
    mvn package -DskipTests -B -ntp \
      -Dmaven.wagon.http.retryHandler.count=5 \
      -Dmaven.wagon.httpconnectionManager.ttlSeconds=120

FROM eclipse-temurin:21-jre

WORKDIR /app

RUN useradd --system --create-home quantapp \
    && mkdir -p /app/logs \
    && chown -R quantapp:quantapp /app

COPY --from=builder \
    --chown=quantapp:quantapp \
    /workspace/target/Quant_Strategy-1.0.2.jar \
    /app/app.jar

USER quantapp

EXPOSE 8080

ENV SPRING_PROFILES_ACTIVE=dev \
    SPRING_DATASOURCE_URL=jdbc:postgresql://host.docker.internal:5432/factor_investing \
    SPRING_DATASOURCE_USERNAME=tushardesarda \
    LOGGING_FILE_NAME=/app/logs/Quant_Strategy.log

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
