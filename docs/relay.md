# Nearby-sync relay (sync over the Internet)

Orbin's nearby sync normally runs device-to-device over Wi-Fi or Bluetooth. When the devices are
far apart, or on mobile data, they can meet on a **relay**: a small server you host that forwards
encrypted frames between two paired devices. Orbin ships no public relay and contacts none unless
you enter an address in **Settings → Nearby devices → Relay server**.

## What the relay sees and stores

- It stores nothing on disk and keeps no accounts, logs of channel ids, or activity.
- It forwards WebSocket binary frames between two connections that name the same **channel**.
  Channel ids are SHA-256 values that only the two devices can derive: from the one-time selector
  in the pairing QR code while pairing, and afterwards from the receiving device's private id plus
  a ten-minute window, so they rotate.
- Every frame is the same AES-GCM–authenticated nearby protocol used over Wi-Fi. The keys (the
  QR code's one-time secret, then the pair key) never reach the relay. The relay can see that two
  devices met, when, from which IP addresses, and roughly how many bytes moved. It can delay or
  drop frames, but it can't read or change them without detection.
- Limits: 8 MiB per frame, 40 MiB and 2 minutes per session, 10-minute idle listeners, 5-second
  waits for connectors, 32 waiting listeners per client IP, 10,000 listeners and 1,000 sessions in
  total (see `RelayConfig`).

## Deploy

The relay is a plain Kotlin/Ktor server in `relay/` (AGPL-3.0, like the app). It speaks HTTP and
WebSocket on port 8080, and it needs **TLS in front**: the apps only accept `https://` relays,
except for loopback and private LAN addresses.

Docker, from the repository root:

```sh
docker build -f relay/Dockerfile -t orbin-relay . && \
  docker run -d --name orbin-relay --restart unless-stopped -p 127.0.0.1:8080:8080 orbin-relay
```

Without Docker (JDK 17+):

```sh
./gradlew :relay:installDist && ORBIN_RELAY_PORT=8080 relay/build/install/orbin-relay/bin/orbin-relay
```

Then terminate TLS with a reverse proxy that passes WebSocket upgrades. Caddy does both
(certificate included) in two lines:

```caddyfile
relay.example.org {
    reverse_proxy 127.0.0.1:8080
}
```

With nginx, forward `Upgrade`/`Connection` headers and raise the body and timeout limits:

```nginx
location / {
    proxy_pass http://127.0.0.1:8080;
    proxy_http_version 1.1;
    proxy_set_header Upgrade $http_upgrade;
    proxy_set_header Connection "upgrade";
    proxy_read_timeout 15m;
    client_max_body_size 10m;
}
```

`GET https://relay.example.org/health` should answer `ok`.

## Use it

1. On the inviting device, open **Settings → Nearby devices**, set **Relay server** to
   `https://relay.example.org`, then choose **Add a device**. The QR code now includes the relay.
2. Scan it on the other device. If that device has no relay set, it adopts the one from the code.
3. Afterwards **Sync now** tries Wi-Fi, then Bluetooth, then the relay. Both apps must be open
   and unlocked: each holds one listening socket on the relay while Nearby sync is on.

Automatic (trusted) sync stays on the local network; the relay is only used when you tap
**Sync now** or pair. Clearing the relay address closes its connection.

## Protocol

- `GET /health` → `ok`
- `WS /v1/{channel}/listen` waits up to 10 minutes for one connector, then closes (`expired`).
  The client reconnects at once. A newer listener on the same channel replaces an older one.
- `WS /v1/{channel}/connect` meets the waiting listener, or closes with policy violation
  `no listener` after 5 seconds.
- On a match both sides receive the text frame `paired`; binary frames are then forwarded until
  either side closes or a limit is hit. `{channel}` is 43 characters of unpadded base64url.
