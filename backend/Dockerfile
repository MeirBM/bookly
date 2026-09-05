# Build stage. Dependencies are resolved in their own layer so a source-only
# change does not re-download the world on every image build.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -B dependency:go-offline
COPY src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
# Runs unprivileged: least privilege, so a compromise of the process is not a
# compromise of the container.
RUN addgroup -S bookly && adduser -S bookly -G bookly
COPY --from=build /build/target/*.jar app.jar
USER bookly
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
