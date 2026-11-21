FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /work
COPY pom.xml .
COPY src src
RUN mvn -B -DskipTests package
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /work/target/*.jar app.jar
USER 10001
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
