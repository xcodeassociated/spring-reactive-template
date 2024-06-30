FROM eclipse-temurin:17-jre-alpine
LABEL authors="xcodeassociated"

COPY ./*.jar ./app.jar

EXPOSE 8080
EXPOSE 8081

ENTRYPOINT ["java", "-jar", "-Dspring.profiles.active=docker", "./app.jar"]