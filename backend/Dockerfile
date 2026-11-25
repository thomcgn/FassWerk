FROM maven:3.9-eclipse-temurin-26@sha256:b2c1ad85954592f9928e84327c65201f308ad9b5d8ed7d5b823717c97bf23fbb AS build
WORKDIR /workspace

COPY pom.xml .
COPY src ./src
# Full verification is the mandatory CI predecessor; this stage only packages the verified commit.
RUN --mount=type=cache,target=/root/.m2 mvn -B -Dmaven.test.skip=true clean package

FROM eclipse-temurin:21-jre@sha256:d7051a45dd955e4d5d1db4d3f4269fe13d1c6dff8cc6b7ef89fc8577b96c1982 AS runtime
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
