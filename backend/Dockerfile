FROM maven:3.9.10-eclipse-temurin-21@sha256:193ed19a4806b4bbad65b2e8a2a9bbf901b6e216eb0d5761c70c2c4537cd53d7 AS build
WORKDIR /workspace

COPY pom.xml .
COPY src ./src
# Full verification is the mandatory CI predecessor; this stage only packages the verified commit.
RUN --mount=type=cache,target=/root/.m2 mvn -B -Dmaven.test.skip=true clean package

FROM eclipse-temurin:24-jre@sha256:8cb2387a28af84cf0db0948d9c67d4480192f4e567027a3963f145d218e8b4f2 AS runtime
WORKDIR /app

ARG VCS_REF=unknown
ARG IMAGE_VERSION=unversioned
LABEL org.opencontainers.image.title="FassWerk backend" \
      org.opencontainers.image.revision="${VCS_REF}" \
      org.opencontainers.image.version="${IMAGE_VERSION}"

RUN groupadd --system app && useradd --system --gid app app

COPY --from=build --chown=app:app /workspace/target/*.jar /app/backend.jar

ENV TMPDIR=/tmp
USER app
EXPOSE 8080
STOPSIGNAL SIGTERM
HEALTHCHECK --interval=10s --timeout=3s --start-period=40s --retries=6 \
  CMD ["curl", "--fail", "--silent", "--show-error", "--max-time", "2", "http://127.0.0.1:8080/actuator/health"]
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-XX:+ExitOnOutOfMemoryError", "-Djava.io.tmpdir=/tmp", "-jar", "/app/backend.jar"]
