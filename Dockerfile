FROM maven:3.9.9-eclipse-temurin-21 AS builder

WORKDIR /workspace

COPY pom.xml ./
COPY frontend ./frontend
COPY src ./src

RUN mvn package -DskipTests -B

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
