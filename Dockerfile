# Build stage
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY . .
RUN mvn clean package -DskipTests

# Runtime stage
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /build/target/tg-gallery-self-contained.jar app.jar

ENTRYPOINT ["java", "-jar", "app.jar"]