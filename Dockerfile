# --- Étape build ------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build

# Cache des dépendances : on copie d'abord le pom seul
COPY pom.xml .
RUN mvn -q -B dependency:go-offline

COPY src ./src
RUN mvn -q -B clean package -DskipTests

# --- Étape runtime ---------------------------------------------------------
FROM eclipse-temurin:17-jre
WORKDIR /app

COPY --from=build /build/target/*.jar app.jar

ENV GOOGLE_APPLICATION_NAME=sheets-export-poc

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
