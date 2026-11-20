FROM maven:3.9-eclipse-temurin-25 AS build

WORKDIR /workspace
COPY pom.xml .
COPY src ./src
RUN mvn -B package

FROM eclipse-temurin:25-jre-alpine

RUN addgroup -S ediary && adduser -S -G ediary ediary

WORKDIR /app
COPY --from=build /workspace/target/*.jar /app/app.jar

USER ediary:ediary
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
