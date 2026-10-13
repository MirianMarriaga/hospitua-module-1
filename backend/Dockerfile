# Imagen del backend de Módulo 1 (plan base, árbol del proyecto).
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN ./mvnw -B -q dependency:go-offline
COPY src/ src/
RUN ./mvnw -B -q package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --uid 1001 hospitua
COPY --from=build /app/target/*.jar app.jar
USER hospitua
ENV SPRING_PROFILES_ACTIVE=prod \
    TZ=America/Bogota
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
