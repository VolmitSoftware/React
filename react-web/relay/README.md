# React Web Relay

React Web Relay is an ephemeral WebSocket routing service between the React
Minecraft plugin and browser dashboard sessions. The plugin connects outbound
to `/agent`; a browser connects to `/app` and subscribes to one server identity.
`GET /healthz` reports aggregate in-memory connection counts for health checks.

The relay authenticates agents with an Ed25519 challenge-response proof and
derives each server ID from the SHA-256 fingerprint of its public key. For every
browser request it replaces the browser request ID with a broker-generated ID,
stores the originating connection, and restores the browser ID only when the
matching agent response arrives. Responses are never broadcast to other
subscribers, even when different browsers use identical request IDs.

All registrations, subscriptions, and pending requests are memory-only. A
restart disconnects every participant and clears all relay state.

## Security boundary

This service is a trusted pass-through broker, not a zero-trust or end-to-end
encrypted system. TLS protects traffic in transit, but the relay process can
read route payloads, including React bearer credentials. It does not persist or
log frame payloads. The current `/app` protocol has no independent browser
identity layer; React's bearer token still authorizes every forwarded API call.

`ALLOWED_APP_ORIGINS` restricts browser WebSocket handshakes to exact origins.
It mitigates cross-site browser use but is not client authentication, and
non-browser WebSocket clients can omit `Origin`. Production deployments should
set an exact origin allowlist, terminate TLS at the service, and keep
credentials short-lived and scoped. React Web's RCT2 payload pins each server
subscription to its public-key fingerprint, while the React API token still
authorizes every forwarded request.

The relay validates envelope shape and the route contract but does not parse
the JSON document carried in a request body. For `POST`, `PUT`, or `PATCH`, the
`payload.body` field must already be a JSON-encoded string. Structured objects
are rejected rather than silently re-encoded. `GET` and `DELETE` are bodyless,
matching the current Java relay agent.

## Protocol

An agent receives a `challenge`, signs its nonce, and responds with `register`:

```json
{
  "type": "register",
  "serverId": "<sha256 fingerprint>",
  "requestId": "hs",
  "payload": {
    "pubKey": "<base64url Ed25519 SubjectPublicKeyInfo>",
    "sig": "<base64url signature>"
  }
}
```

A browser first sends `subscribe`, then sends `route` frames with a unique
request ID for that browser connection:

```json
{
  "type": "route",
  "serverId": "<sha256 fingerprint>",
  "requestId": "browser-local-id",
  "payload": {
    "method": "PUT",
    "path": "/api/v1/features/example",
    "headers": {
      "Authorization": "Bearer ...",
      "Content-Type": "application/json",
      "X-React-Counter": "1"
    },
    "body": "{\"enabled\":true}"
  }
}
```

The agent sees a different broker request ID. Its correlated `data` or `error`
frame is delivered only to the originating browser with
`browser-local-id` restored. Unknown, expired, duplicated, disconnected, or
cross-server response IDs are dropped.

## Configuration

Run locally with `dart run bin/relay.dart`. Configuration uses environment
variables:

| Variable | Default | Purpose |
|---|---:|---|
| `PORT` | `8080` | HTTP/WebSocket listener port |
| `BIND_ADDRESS` | `0.0.0.0` | Listener address |
| `ALLOWED_APP_ORIGINS` | unset | Comma-separated exact `http://` or `https://` browser origins |
| `MAX_FRAME_BYTES` | `1048576` | Maximum UTF-8 bytes in one incoming text frame |
| `MAX_MESSAGES_PER_WINDOW` | `120` | Per-connection messages accepted each second |
| `MAX_PENDING_PER_APP` | `64` | Concurrent requests from one browser connection |
| `MAX_PENDING_GLOBAL` | `4096` | Concurrent requests across the process |
| `MAX_OUTBOUND_QUEUE_MESSAGES` | `128` | Relay-managed outbound queue bound per connection |
| `HANDSHAKE_TIMEOUT_SECONDS` | `10` | Agent registration deadline |
| `SUBSCRIPTION_TIMEOUT_SECONDS` | `10` | Browser subscription deadline |
| `REQUEST_TIMEOUT_SECONDS` | `10` | Broker correlation lifetime |
| `PING_INTERVAL_SECONDS` | `30` | WebSocket ping/pong failure interval |

Malformed, oversized, over-rate, unauthenticated-agent, and protocol-invalid
connections are closed. Pending requests are removed on browser disconnect and
failed when an agent disconnects or is replaced.

## Container and Cloud Run

Build the included container from this directory:

```bash
docker build -t react-web-relay .
docker run --rm -p 8080:8080 \
  -e ALLOWED_APP_ORIGINS=https://react.example.com \
  react-web-relay
```

Deploy this container as a separate Cloud Run service with a dedicated relay
hostname. Firebase Hosting should serve the static `react-web` application
only; do not proxy the WebSocket through a Firebase Hosting rewrite. Cloud Run
WebSocket connections still have a finite request timeout, so plugin and
browser clients must reconnect. This in-memory implementation is single-instance
routing: running multiple instances requires a shared connection-routing
backplane, not only session affinity.

Run verification with:

```bash
dart analyze
dart test
```
