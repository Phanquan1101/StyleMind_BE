FROM maven:3.9.11-eclipse-temurin-21-alpine AS build
WORKDIR /build
COPY commerce-service/pom.xml ./commerce-service/pom.xml
COPY commerce-service/src ./commerce-service/src
COPY artifacts/catalog-review/livostyle/clothing-200-db-payload-vi.json ./artifacts/catalog-review/livostyle/clothing-200-db-payload-vi.json
WORKDIR /build/commerce-service
RUN --mount=type=cache,target=/root/.m2,sharing=locked mvn -B -ntp -DskipTests package

FROM eclipse-temurin:21-jre-noble
WORKDIR /app
COPY --from=build --chown=10001:10001 /build/commerce-service/target/commerce-service-0.0.1-SNAPSHOT.jar /app/app.jar
USER 10001:10001
ENV SERVER_PORT=8082
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
