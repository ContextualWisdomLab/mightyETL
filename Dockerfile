FROM maven:3.9.9-eclipse-temurin-17 AS build

WORKDIR /workspace

COPY pom.xml pom.xml
COPY cdc-service/pom.xml cdc-service/pom.xml
COPY config-server/pom.xml config-server/pom.xml
COPY etl-service/pom.xml etl-service/pom.xml
COPY eureka-server/pom.xml eureka-server/pom.xml
COPY zuul-gateway/pom.xml zuul-gateway/pom.xml

RUN mvn -B -DskipTests dependency:go-offline

COPY . .

ARG SERVICE
RUN mvn -B -DskipTests -pl "${SERVICE}" -am package \
  && mkdir -p /out \
  && cp "${SERVICE}/target/${SERVICE}-"*.jar /out/app.jar

FROM eclipse-temurin:25-jre

WORKDIR /app
COPY --from=build /out/app.jar /app/app.jar

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
