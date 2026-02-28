# syntax=docker/dockerfile:1

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /project

# Cache deps first (no Maven Wrapper inside Docker)
COPY pom.xml ./
RUN mvn -q -DskipTests dependency:go-offline

# Build
COPY . .
RUN mvn -q -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /work

COPY --from=build /project/target/quarkus-app/ /work/

EXPOSE 8080
ENV JAVA_OPTS=""
CMD ["java","-jar","quarkus-run.jar"]
