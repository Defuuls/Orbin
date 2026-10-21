# Orbin nearby-sync relay. Build from the repository root:
#
#   docker build -f relay/Dockerfile -t orbin-relay .
#   docker run -d --name orbin-relay --restart unless-stopped -p 127.0.0.1:8080:8080 orbin-relay
#
# It speaks plain HTTP/WebSocket; put a TLS reverse proxy in front (see docs/relay.md).
FROM eclipse-temurin:17-jdk AS build
WORKDIR /src
COPY . .
# Only the relay is built; the Android modules are configured but never compiled.
RUN ./gradlew --no-daemon --no-configuration-cache :relay:installDist

FROM eclipse-temurin:17-jre
COPY --from=build /src/relay/build/install/orbin-relay /opt/orbin-relay
ENV ORBIN_RELAY_HOST=0.0.0.0 ORBIN_RELAY_PORT=8080
EXPOSE 8080
# No root, no writable state: the relay keeps everything in memory.
USER 65532:65532
HEALTHCHECK --interval=30s --timeout=3s CMD ["bash", "-c", "exec 3<>/dev/tcp/127.0.0.1/8080 && printf 'GET /health HTTP/1.0\\r\\n\\r\\n' >&3 && grep -q ok <&3"]
ENTRYPOINT ["/opt/orbin-relay/bin/orbin-relay"]
