FROM node:lts-alpine3.24 AS frontend
WORKDIR /fe
# .npmrc scopes @insurance-portal to Verdaccio and must be in place before the install layer.
# It names host.docker.internal, which resolves both on the host and in here.
COPY levels-of-ai-help-frontend/package.json levels-of-ai-help-frontend/package-lock.json levels-of-ai-help-frontend/.npmrc ./
RUN npm ci
COPY levels-of-ai-help-frontend/ ./
RUN npm run build

FROM maven:3.9-eclipse-temurin-21 AS backend
WORKDIR /app
# The build container has no ~/.m2/settings.xml, and the pom's repository URL says localhost -
# which in here is the container itself. This overrides it with host.docker.internal.
COPY docker/settings.xml /root/.m2/settings.xml
# Pom first, so the dependency layer caches until it actually changes.
COPY levels-of-ai-help-app/pom.xml ./
RUN mvn -q -e -B dependency:go-offline
COPY levels-of-ai-help-app/src ./src
# The SPA is served by Spring Boot out of static/.
COPY --from=frontend /fe/dist/ ./src/main/resources/static/
# -U forces a snapshot re-check: Maven caches snapshots for a day, so without it a freshly
# published portal is silently ignored and the image ships against yesterday's library.
RUN mvn -q -B -U clean package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=backend /app/target/*.jar app.jar
# No seed files to copy: the portal's fixtures and deployment content travel inside its own jar.
COPY configuration ./configuration
ENV VOICE_CONFIG_DIR=/app/configuration
EXPOSE 8080
ENTRYPOINT ["java","-jar","app.jar"]
