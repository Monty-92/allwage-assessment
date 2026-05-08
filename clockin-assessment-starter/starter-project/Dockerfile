# ── Stage 1: Build & Test ──────────────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /app

# Copy pom first — allows Docker layer cache to skip dependency download
# when only source files change
COPY pom.xml .

# Download dependencies (cached separately from source compilation)
RUN --mount=type=cache,target=/root/.m2 \
    mvn dependency:go-offline -q

# Copy source and build (runs tests during package)
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 \
    mvn clean package -q

# ── Stage 2: Runtime ───────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine AS runtime

# Non-root user for security
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

WORKDIR /app

COPY --from=build /app/target/clockin-assessment-*.jar app.jar

RUN chown appuser:appgroup app.jar

USER appuser

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
