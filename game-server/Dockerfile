# Game-server (Java 21 + Spring Boot). Build en dos etapas:
#  1) "build": imagen con Maven y el JDK completo para compilar.
#  2) imagen final solo con el JRE: más pequeña y con menos superficie de ataque.

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
# Descargamos dependencias primero: se cachean mientras no cambie pom.xml
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
# Los tests se ejecutan en CI / con "mvn test"; aquí solo empaquetamos
RUN mvn -q -B package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
# Usuario sin privilegios (buena práctica de seguridad en contenedores)
RUN addgroup -S app && adduser -S app -G app
COPY --from=build /src/target/*.jar app.jar
USER app

EXPOSE 8081
ENV SPRING_PROFILES_ACTIVE=docker \
    JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"

HEALTHCHECK --interval=10s --timeout=3s --start-period=40s --retries=3 \
  CMD wget -qO- http://localhost:8081/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
