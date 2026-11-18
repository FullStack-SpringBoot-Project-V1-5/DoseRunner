# syntax=docker/dockerfile:1
# One Dockerfile for every Spring Boot module:  docker build --build-arg MODULE=order-service -f deploy/compose/Dockerfile .
# (custody/dispatch get their own image later: they need libsodium23 + libroute.so for FFM.)

FROM maven:3.9.16-eclipse-temurin-25 AS build
WORKDIR /src
COPY . .
ARG MODULE
RUN --mount=type=cache,target=/root/.m2,sharing=locked \
    mvn -B -ntp -pl ${MODULE} -am package -DskipTests \
 && cp ${MODULE}/target/${MODULE}-*.jar /app.jar

FROM eclipse-temurin:25.0.5_8-jre
# curl: used by the compose/k8s healthchecks
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/* \
 && groupadd --system app && useradd --system --gid app --no-create-home app
WORKDIR /app
COPY --from=build /app.jar /app/app.jar
USER app
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
