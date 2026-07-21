FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -B dependency:go-offline
COPY src ./src
RUN mvn -B clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+UseSerialGC -Xss256k -XX:TieredStopAtLevel=1"
EXPOSE 8080
ENTRYPOINT ["sh","-c","java $JAVA_OPTS -jar app.jar"]