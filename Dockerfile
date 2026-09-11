FROM maven:3.9.13-eclipse-temurin-25@sha256:ade3c87e3cdfbe04932afa16b31814cbf60b0122d21d78a76530684a1eeb7cc2 AS build

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

FROM eclipse-temurin:25-jre@sha256:681c543d6f36c50f45e9b5226930a46203dcfa351d3670e9d0bdf0dabae53539

WORKDIR /app
COPY --from=build --chown=65532:65532 /out/app.jar /app/app.jar

USER 65532:65532
ENTRYPOINT ["java", "-jar", "/app/app.jar"]