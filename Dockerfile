FROM eclipse-temurin:21-jre
LABEL authors="xcodeassociated"

COPY ./build/libs/*.jar ./app.jar

EXPOSE 8080
EXPOSE 8081

ENTRYPOINT ["java $JAVA_OPTS", "-jar", "-Dspring.profiles.active=docker", "./app.jar"]