# Multi-stage build for the Branch Teller REST API (com.branchteller.api.ApiServer).
#
# The Swing desktop app (com.branchteller.Main) is a GUI and isn't meant to run inside a
# container -- the API server is the natural containerized entry point, since it exposes
# the same service layer (accounts, deposits/withdrawals, customers) over HTTP/JSON with
# zero extra dependencies (built on the JDK's own com.sun.net.httpserver.HttpServer).

# ---- Build stage --------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY pom.xml .
# Warm the local repo cache in its own layer so code-only changes don't re-download deps.
RUN mvn -B dependency:go-offline
COPY src ./src
RUN mvn -B package -DskipTests

# ---- Runtime stage -------------------------------------------------------------
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
COPY --from=build /build/target/branch-teller-pos.jar app.jar
COPY --from=build /root/.m2/repository/com/mysql/mysql-connector-j/9.7.0/mysql-connector-j-9.7.0.jar mysql-connector-j.jar

ENV API_PORT=8082
EXPOSE 8082

ENTRYPOINT ["sh", "-c", "java -cp app.jar:mysql-connector-j.jar com.branchteller.api.ApiServer"]
