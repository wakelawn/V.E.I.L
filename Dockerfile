# Build stage: Maven + JDK 25 (Maven is pre-installed in the base image)
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /app

COPY pom.xml ./
COPY src ./src
RUN mvn -B -q package -DskipTests

# Runtime stage: JRE only, non-root user
FROM eclipse-temurin:25-jre
WORKDIR /app

RUN groupadd --system veil && useradd --system --gid veil --home-dir /app veil
COPY --from=build /app/target/*.jar app.jar

USER veil
EXPOSE 8080

ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseContainerSupport"
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
