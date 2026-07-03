# reactor_relay

reactor_relay is a zero-trust WebSocket relay broker for the React Minecraft plugin: it routes `RelayFrame` envelopes between `/agent` (plugin) connections and `/app` (dashboard) connections, keyed by server-id and secured via Ed25519 pubkey fingerprinting. Agents authenticate with a challenge-response handshake before frames are routed; the broker never interprets payload contents, treating them as opaque pass-through data.
