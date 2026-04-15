
FROM maven:3.8.5-openjdk-17 AS build
WORKDIR /app
COPY . .
RUN mvn clean package -DskipTests

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

COPY --from=build /app/target/ /app/target/

EXPOSE 8080

CMD ["sh", "-c", "java -jar $(ls -S /app/target/*.jar | head -n 1)"]
