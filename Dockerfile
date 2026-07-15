# syntax=docker/dockerfile:1.7@sha256:a57df69d0ea827fb7266491f2813635de6f17269be881f696fbfdf2d83dda33e

FROM eclipse-temurin:25-jdk-alpine@sha256:5ecfde8e5ecde5954ea3721155b345ef56c1d579b940c761318ad4c05959a151 AS build

WORKDIR /workspace

COPY gradlew gradlew.bat build.gradle.kts settings.gradle.kts ./
COPY gradle ./gradle
COPY config ./config

RUN chmod +x gradlew && ./gradlew --no-daemon dependencies

COPY src ./src

RUN ./gradlew --no-daemon bootJar

FROM eclipse-temurin:25-jre-alpine@sha256:28db6fdf60e38945e43d840c0333aeaec66c15943070104f7586fd3c9d1665b0

LABEL org.opencontainers.image.title="transfer-service" \
      org.opencontainers.image.description="Transfer service" \
      org.opencontainers.image.source="https://github.com/atoussec-ctrl/PicPay-BackEnd-Challenge"

RUN apk upgrade --no-cache \
    && addgroup -S application \
    && adduser -S -D -H -u 10001 -G application application

WORKDIR /app

COPY --from=build --chown=application:application /workspace/build/libs/transfer-service-*.jar /app/application.jar

USER application:application

EXPOSE 8080

HEALTHCHECK --interval=10s --timeout=3s --start-period=30s --retries=5 \
    CMD wget -q -O - http://localhost:8080/actuator/health/liveness >/dev/null || exit 1

ENTRYPOINT ["java", "-jar", "/app/application.jar"]
