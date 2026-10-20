package com.orbin.relay

import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer

/**
 * Runs the relay. Plain HTTP on purpose: put it behind a TLS-terminating reverse proxy (Caddy,
 * nginx, Traefik) so devices reach it over `https://` / `wss://`. See docs/relay.md.
 *
 * Environment: `ORBIN_RELAY_HOST` (default 0.0.0.0), `ORBIN_RELAY_PORT` (default 8080).
 */
fun main() {
    val host = System.getenv("ORBIN_RELAY_HOST") ?: "0.0.0.0"
    val port = System.getenv("ORBIN_RELAY_PORT")?.toIntOrNull() ?: DEFAULT_PORT
    embeddedServer(CIO, port = port, host = host) { relay() }.start(wait = true)
}

private const val DEFAULT_PORT = 8080
