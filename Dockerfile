FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml ./
COPY core core
COPY web web
COPY worker worker
RUN mvn -B -ntp -DskipTests package
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/* && useradd --system --uid 10001 cloudshield && mkdir -p /app/data && chown -R cloudshield /app
COPY --from=build /workspace/web/target/cloudshield-web-1.0.0.jar app.jar
USER 10001
ENV SERVER_ADDRESS=0.0.0.0 CLOUDSHIELD_DATA_DIR=/app/data
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=45s --retries=3 CMD curl -fsS http://127.0.0.1:8080/health || exit 1
ENTRYPOINT ["java","-Xms128m","-Xmx512m","-jar","app.jar"]
