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
RUN mvn -q package -DskipTests

# Stage 2: runtime
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/target/backend-*.jar app.jar

EXPOSE 8080
USER nonroot
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
</content>