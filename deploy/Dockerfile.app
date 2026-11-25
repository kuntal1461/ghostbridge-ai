# GhostBridge App: one executable Spring Boot jar. Build the jar first from the repository root:
#   mvn -B clean package -DskipTests      (produces ghostbridge-app/ghostbridge-app-web/target/ghostbridge-app-web-*.jar)
# Built and run with Docker Desktop on 2026-11-24 (see ghostbridge-app/tasks/GB-APP-001-runnable-app-integration/test-cases.md).
FROM eclipse-temurin:21-jre@sha256:d77e090ae1f6970c047b443c6f0a6c1358b0426d44432bb5a7753408cd43de0d
RUN groupadd --system ghostbridge && useradd --system --gid ghostbridge --no-create-home --shell /usr/sbin/nologin ghostbridge
WORKDIR /app
COPY ghostbridge-app/ghostbridge-app-web/target/ghostbridge-app-web-*.jar /app/app.jar
USER ghostbridge
EXPOSE 8080
# No JDK debug switches (jdk.httpclient.HttpClient.log, javax.net.debug): they can print request data.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
