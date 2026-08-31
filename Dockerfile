# syntax=docker/dockerfile:1
#
# Multi-stage build for the e-commerce showcase application.
# Stage 1 builds the Spring Boot fat jar (including the Angular frontend bundled as static
# resources); stage 2 copies only the resulting jar into a slim JRE runtime image.

FROM eclipse-temurin:25-jdk@sha256:e787e08ef76f4c16866108cd7f9fcd96a68eef3ac6cc76866897d4d02d5a2262 AS builder

WORKDIR /workspace

# Note: the builder stage intentionally isn't Alpine-based. The frontend build (Angular, via the
# Gradle node plugin) downloads official Node.js binaries, which are linked against glibc and
# don't run on Alpine's musl libc.
# Copy the Gradle wrapper and build scripts first so dependency/tooling downloads are cached
# independently of source code changes.
COPY gradlew build.gradle settings.gradle gradle.properties ./
COPY gradle ./gradle
RUN ./gradlew --version --no-daemon

COPY etc ./etc
COPY domain ./domain
COPY adapter ./adapter
COPY application ./application

RUN ./gradlew :application:ecommerce:bootJar --no-daemon -x test -x check

FROM eclipse-temurin:25-jre-alpine@sha256:3137541deb3cac6626b5d9a4a2187bc0d6a34312f858bd2c67dd01e732e6b682 AS runtime

RUN addgroup -S app && adduser -S app -G app
WORKDIR /app
COPY --from=builder /workspace/application/ecommerce/build/libs/*.jar app.jar
RUN chown app:app app.jar
USER app

# 9080 serves the application (context path /home), 9081 exposes actuator (health, metrics, ...).
EXPOSE 9080 9081

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD wget --quiet --tries=1 --spider http://localhost:9081/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
