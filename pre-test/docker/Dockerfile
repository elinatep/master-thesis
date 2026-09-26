# The image: Spring Boot serving the API and the pre-built SPA.
#
# The frontend is NOT built in here. It is built on the machine running scripts/release.sh, and
# this copies the resulting dist/ in. That is a deliberate trade, made after the containerised
# build failed in a way the host build does not:
#
#   Deploying to Azure Container Apps needs a linux/amd64 image, so on an Apple Silicon Mac the
#   build runs under QEMU emulation. In that environment Dart Sass computes different contrast
#   ratios inside Bootstrap's color-contrast(), which returns null and fails the stylesheet with
#   "$color1: null is not a color". Identical package versions - sass 1.104.0, bootstrap 5.3.8 -
#   build correctly on the host and fail emulated.
#
# Building on the host uses the toolchain that demonstrably works, and is much faster besides. What
# it gives up is a hermetic frontend build - but this build was never hermetic: it resolves the
# insurance portal from two registries running on the developer's own machine.
#
# scripts/release.sh builds the frontend before calling docker, and fails loudly if dist/ is
# missing, so a stale or absent bundle cannot be shipped by accident.

FROM maven:3.9-eclipse-temurin-21 AS backend
WORKDIR /app
# The build container has no ~/.m2/settings.xml, and the pom's repository URL says localhost -
# which in here is the container itself. This overrides it with host.docker.internal.
COPY docker/settings.xml /root/.m2/settings.xml
# Pom first, so the dependency layer caches until it actually changes.
COPY pretest-app/pom.xml ./
RUN mvn -q -e -B dependency:go-offline
COPY pretest-app/src ./src
# The SPA, built on the host, served by Spring Boot out of static/.
COPY pretest-frontend/dist/ ./src/main/resources/static/
# -U forces a snapshot re-check: Maven caches snapshots for a day, so without it a freshly
# published portal is silently ignored and the image ships against yesterday's library.
RUN mvn -q -B -U clean package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=backend /app/target/*.jar app.jar
# configuration/ carries the arm definitions, the task text AND the pre-test's portal seed. The
# portal's own fixtures travel inside its jar; this seed overrides them (app.seed.location).
COPY configuration ./configuration
ENV VOICE_CONFIG_DIR=/app/configuration
EXPOSE 8080
ENTRYPOINT ["java","-jar","app.jar"]
