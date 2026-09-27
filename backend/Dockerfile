#=====================================================================
# Backend build: Java 21 + Spring Boot + Oracle JDBC/UCP
# Multi-stage: build with Maven, run with JRE.
#=====================================================================

# Stage 1: build
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
RUN mvn -q dependency:go-offline

COPY src ./src
# Tests are skipped on purpose: `rake qa:image:build` must stay
# deterministic and DB-free (unit/slice tests run separately in the QA
# pipeline via `mvn test`, not inside the image build).
RUN mvn -q package -DskipTests

# Stage 2: runtime
FROM eclipse-temurin:21-jre-jammy
LABEL maintainer="Sergio Sanchez" version="0.0.1"
LABEL org.opencontainers.image.title="enterprise-orders-backend" \
      org.opencontainers.image.description="Multi-tenant enterprise order platform backend (Spring Boot on Oracle)" \
      org.opencontainers.image.licenses="MIT" \
      org.opencontainers.image.vendor="Sergio Sanchez"
WORKDIR /app

COPY --from=build /workspace/target/backend-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8080

# NOTE: actuator base-path is /internal (see application.yml), not /actuator.
# start-period gives the JVM time to boot before curl failures count.
HEALTHCHECK --interval=30s --timeout=5s --retries=3 --start-period=60s \
    CMD curl -f http://localhost:8080/internal/health || exit 1

# NOTE: root is needed only to install curl; the final USER below is the
# non-root runtime user (also mirrored by runAsUser in the k8s manifest).
USER root
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*

# NOTE: numeric UID - the named `nonroot` user does not exist in this
# image's /etc/passwd and containerd fails with "no users found".
# 65532 is the conventional non-root UID.
USER 65532:65532

ENTRYPOINT ["java", \
    "-XX:MaxRAMPercentage=75.0", \
    "-XX:+UseContainerSupport", \
    "-jar", "/app/app.jar"]
