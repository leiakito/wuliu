FROM maven:3.9.9-eclipse-temurin-17 AS builder
WORKDIR /app

COPY pom.xml .
COPY src ./src

RUN mvn -B -DskipTests clean package \
    && mv target/*-SNAPSHOT.jar app.jar

FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

ENV JAVA_OPTS=""

COPY --from=builder /app/app.jar app.jar

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
