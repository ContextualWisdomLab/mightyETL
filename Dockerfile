FROM maven:3.9.13-eclipse-temurin-25@sha256:ade3c87ed2874c8b773ccb9b238cd66db8a7c56c77d99a1c825bf929f3afcb96 AS build

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

FROM eclipse-temurin:25-jre@sha256:f19dbf0dc677ae28efed04b8b99d3123d6aaf2e6b3c9d35c09274dd8b5d53a4f

WORKDIR /app
COPY --from=build --chown=65532:65532 /out/app.jar /app/app.jar

USER 65532:65532
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
