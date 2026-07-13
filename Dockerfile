# syntax=docker/dockerfile:1

# --- Stage 1: build the React/Vite frontend ------------------------------
FROM node:20-alpine AS frontend
WORKDIR /app/frontend
# Copy manifests first so `npm ci` is cached until dependencies change.
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
# Then the sources, and build into a local dist/ (overriding the dev outDir).
COPY frontend/ ./
RUN npm run build -- --outDir dist --emptyOutDir

# --- Stage 2: build the Spring Boot jar (with the built UI embedded) ------
FROM eclipse-temurin:21-jdk AS backend
WORKDIR /app
# Copy the Gradle wrapper + build files first for dependency-layer caching.
COPY gradlew ./
COPY gradle ./gradle
COPY settings.gradle build.gradle ./
# Warm the wrapper (downloads the pinned Gradle distribution) as a cached layer.
RUN ./gradlew --version --no-daemon
# Backend sources, then the pre-built UI from the frontend stage.
COPY src ./src
COPY --from=frontend /app/frontend/dist ./src/main/resources/static
# The frontend is already built, so skip the Gradle-driven frontend build.
RUN ./gradlew bootJar -PskipFrontend --no-daemon

# --- Stage 3: minimal runtime --------------------------------------------
FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app
COPY --from=backend /app/build/libs/app.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
