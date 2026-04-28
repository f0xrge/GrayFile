FROM maven:3.9.11-eclipse-temurin-25 AS build
WORKDIR /workspace

COPY .mvn/ .mvn/
COPY mvnw mvnw
COPY mvnw.cmd mvnw.cmd
COPY pom.xml pom.xml
COPY ext-proc/pom.xml ext-proc/pom.xml
COPY ext-proc/src ext-proc/src

RUN mvn -f ext-proc/pom.xml -DskipTests package

FROM eclipse-temurin:25-jre
WORKDIR /app

COPY --from=build /workspace/ext-proc/target/grayfile-ext-proc-0.1.0-SNAPSHOT.jar /app/grayfile-ext-proc.jar

EXPOSE 18080

ENTRYPOINT ["java", "-jar", "/app/grayfile-ext-proc.jar"]
