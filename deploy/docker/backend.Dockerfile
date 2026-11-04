# Backend image: Spring Boot 3.3.4 + Java 21 (single module, Flowable embedded).
# Build context must be the repository root:
#   docker build -f deploy/docker/backend.Dockerfile -t icp-backend .
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY backend/pom.xml backend/pom.xml
RUN mvn -B -q -f backend/pom.xml -DskipTests dependency:go-offline
COPY backend/src backend/src
RUN mvn -B -q -f backend/pom.xml -DskipTests package \
    && cp backend/target/icp-platform-backend.jar /app.jar

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S icp && adduser -S icp -G icp \
    && mkdir -p /app/data/files /app/logs \
    && chown -R icp:icp /app
USER icp
WORKDIR /app
COPY --from=build /app.jar /app/app.jar

# Container profile: application.yml placeholders are filled by compose env.
ENV SPRING_PROFILES_ACTIVE=container \
    JAVA_OPTS="-Xms256m -Xmx1024m -Dfile.encoding=UTF-8" \
    SERVER_PORT=8080 \
    STORAGE_ROOT=/app/data/files
EXPOSE 8080

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
