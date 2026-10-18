FROM maven:3.9.11-eclipse-temurin-21-alpine AS build
WORKDIR /build
COPY pom.xml ./
COPY src ./src
RUN --mount=type=cache,target=/root/.m2,sharing=locked mvn -B -ntp -DskipTests package

FROM eclipse-temurin:21-jre-noble
WORKDIR /app
COPY --from=build --chown=10001:10001 /build/target/identity-service-0.0.1-SNAPSHOT.jar /app/app.jar
USER 10001:10001
ENV SERVER_PORT=8081
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
