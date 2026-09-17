FROM eclipse-temurin:25-jre

WORKDIR /app
COPY .artifacts/monolith.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
