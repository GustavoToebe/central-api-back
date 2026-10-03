# syntax=docker/dockerfile:1
# Imagem da API da Central (27/09/2026): mesma receita do servire-api-back.
# Sobe pelo deploy/docker-compose.yml na VPS, atrás do Caddy.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
RUN --mount=type=secret,id=gh_token,required=false \
    set -eu; \
    trap 'rm -f /root/.m2/settings.xml' EXIT; \
    if [ -s /run/secrets/gh_token ]; then \
      mkdir -p /root/.m2; umask 077; \
      printf '<settings><servers><server><id>github</id><username>GustavoToebe</username><password>%s</password></server></servers></settings>' "$(cat /run/secrets/gh_token)" > /root/.m2/settings.xml; \
    fi; \
    mvn -q -B dependency:go-offline
COPY src ./src
RUN --mount=type=secret,id=gh_token,required=false \
    set -eu; \
    trap 'rm -f /root/.m2/settings.xml' EXIT; \
    if [ -s /run/secrets/gh_token ]; then \
      mkdir -p /root/.m2; umask 077; \
      printf '<settings><servers><server><id>github</id><username>GustavoToebe</username><password>%s</password></server></servers></settings>' "$(cat /run/secrets/gh_token)" > /root/.m2/settings.xml; \
    fi; \
    mvn -q -B clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S central && adduser -S central -G central
COPY --from=build /build/target/central-api-*.jar app.jar
USER central
EXPOSE 8081
ENV SPRING_PROFILES_ACTIVE=prod
ENTRYPOINT ["java", "-jar", "app.jar"]
