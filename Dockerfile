# Build stage. Dependencies are resolved in their own layer so a source-only
# change does not re-download the world on every image build.
# Built from the repository root, not from backend/. Railway analyses the root of the repo and
# could not pick between two applications there ("Railpack could not determine how to build the
# app"), and its Root Directory setting proved unreliable in practice. Building from the root works
# with no platform setting at all.
#
# One Dockerfile rather than a root copy alongside backend/Dockerfile: two files that must agree
# is a defect waiting for the day they stop agreeing, and the image docker compose builds has to
# be the image that ships, or the tests verified something else.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY backend/pom.xml .
COPY backend/src ./src
# One `package`, not a separate dependency:go-offline pre-fetch. go-offline resolves every
# transitive BOM rather than what the build needs: flyway-community-db-support declares the whole
# Google Cloud catalogue, and the step was still downloading after 1,865 artifacts. It bought a
# cacheable layer and cost a build that does not finish inside a platform's timeout.
RUN mvn -B -DskipTests -Dmaven.test.skip=true package

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
# Runs unprivileged: least privilege, so a compromise of the process is not a
# compromise of the container.
RUN addgroup -S bookly && adduser -S bookly -G bookly
COPY --from=build /build/target/*.jar app.jar
USER bookly
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
