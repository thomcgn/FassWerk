FROM maven:3.9.15-eclipse-temurin-26@sha256:029a8e2838ae68238ffb8be407cddbb3f07d4d839c60c6f26c619a69fd184531 AS build
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
