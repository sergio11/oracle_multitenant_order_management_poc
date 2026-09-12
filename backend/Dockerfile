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
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
</content>
<task_progress>
- [x] FASE 6: infraestructura backend base completa (UCP, filtros, tenant, DTOs, service, controller, ymls)
- [x] FASE 6: Dockerfile backend
- [ ] FASE 6: Revisar estado git
- [ ] FASE 6: Compilar backend (mvn compile) para validar
- [ ] FASE 6: Commit fase 6
- [ ] FASE 7 a 12 (resto del plan)
</task_progress>