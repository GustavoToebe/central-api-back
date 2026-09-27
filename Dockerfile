# Imagem da API da Central (27/09/2026): mesma receita do servire-api-back.
# Sobe pelo deploy/docker-compose.yml na VPS, atrás do Caddy.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S central && adduser -S central -G central
COPY --from=build /build/target/central-api-*.jar app.jar
USER central
EXPOSE 8081
ENV SPRING_PROFILES_ACTIVE=prod
ENTRYPOINT ["java", "-jar", "app.jar"]
