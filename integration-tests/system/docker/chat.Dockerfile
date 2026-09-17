FROM eclipse-temurin:25-jre

WORKDIR /app
COPY .artifacts/chat.jar app.jar

EXPOSE 8081
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
