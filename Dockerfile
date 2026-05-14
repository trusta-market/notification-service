# syntax=docker/dockerfile:1.7

FROM gradle:8.10-jdk21-alpine AS builder
WORKDIR /workspace

ARG GPR_USER
ARG GPR_TOKEN

COPY settings.gradle build.gradle ./
COPY gradle ./gradle
RUN GPR_USER="$GPR_USER" GPR_TOKEN="$GPR_TOKEN" gradle dependencies --no-daemon

COPY src ./src

RUN GPR_USER="$GPR_USER" GPR_TOKEN="$GPR_TOKEN" gradle bootJar --no-daemon \
 && cp build/libs/*.jar /workspace/app.jar

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -g 1000 -S spring && adduser -u 1000 -S spring -G spring

COPY --from=builder --chown=spring:spring /workspace/app.jar /app/app.jar

USER spring:spring

EXPOSE 8120

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
