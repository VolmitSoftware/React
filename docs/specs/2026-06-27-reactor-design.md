# Reactor — Design Spec

**Status:** Approved (design). Implementation not started.
**Date:** 2026-06-27
**Owner:** psycho@arcane.art

> **Reactor** is a web-based, multi-server control plane for the **React** Minecraft performance plugin. It exposes everything React can do — monitor, control, operate, and manage a whole fleet — from a browser styled like shadcn, with full write control behind guardrails.

---

## 1. Goal & Scope

Replace the dead `React/react_remote` Flutter scaffold (Material-based, empty) with a brand-new **`arcane_jaspr`** web app, and build the **plugin-side API it requires** (React has no external interface today). The app must surface React's full operator surface:

- **~79 metric samplers** (each with 128-point rolling history)
- **48 runtime features** + **19 tweaks** (toggle + typed config)
- **8 mitigation actions** (destructive ones guardrailed)
- **10 spatial heatmaps / cost maps**
- **15 cross-plugin integration dimensions** (Iris / Adapt / Wormholes)
- **Multi-server fleet** management with aggregate rollups and alerts

**In scope:** all three tiers (plugin API, optional cloud relay, web app), full control with guardrails, multi-server fleet.
**Out of scope (non-goals):** rewriting React's optimization logic; replacing the in-game item-frame map dashboards (they remain); a mobile-native app (web is responsive, usable in a mobile browser); public/anonymous access (operator-authenticated only).

## 2. Global Constraints

- **App stack:** `arcane_jaspr` 3.3.0 (Jaspr ≥0.22.1), Dart SDK ≥3.10, `ShadcnTheme.midnight`, dark-first. **No Material Design.**
- **Plugin stack:** Java 25, Paper/Folia, React `2.0.0-26.2`, `api-version 26.2`. Embedded server via **SlimJar** (no extra shaded weight). No `var`, explicit types, no wildcard imports, switch expressions. No `@Deprecated`, no backward-compat shims, no code comments unless requested.
- **Relay stack:** Dart (`shelf` + `dart:io` WebSocket) so it shares the wire codec with the app.
- **Shared transport skeleton** lives in `VolmLib/shared` (`art.arcane.volmlib.web`); must tolerate relocation (BileTools never relocates `art.arcane.volmlib`) — registration hooks only, no static cross-plugin singletons.
- **MasterChangelog:** operator-visible plugin changes (the new `/react web` commands, the embedded API) update the React section in `MasterChangelog.MD` at implementation time.
- **Folia-safety:** sampler/config/oshi reads run off-thread; any world/entity/chunk/action touch marshals through `J.sResult` / `J.runRegionResult`; actions `.queue()` onto `JobController`.

## 3. Architecture — Three Tiers

```
┌─────────────────────────┐     direct WSS (LAN/VPN)      ┌──────────────────────────┐
│  arcane_jaspr web app   │ ◄───────────────────────────► │  React plugin (Tier 1)   │
│  (Tier 3, static SSG +  │                                │  embedded Javalin/Jetty  │
│   hydrated island)      │ ◄──┐  relay WSS (NAT, P5) ┌──► │  REST /api/v1 + /ws/*    │
└─────────────────────────┘    │                      │   └──────────────────────────┘
                               ▼                      ▼
                        ┌──────────────────────────────────┐
                        │  Dart relay broker (Tier 2, P5)  │
                        │  zero-trust switch by server-id  │
                        └──────────────────────────────────┘
```

### 3.1 Tier 1 — Plugin API (new, inside React)

- **Server:** **Javalin** (Jetty-backed) loaded via React's existing SlimJar block (`slim('io.javalin:javalin:6.x')`) → ~0 shaded-jar cost; Jetty's thread pool is decoupled from Folia region threads.
- **Location:**
  - `art.arcane.react.core.controller.WebController` — auto-discovered by `Registry<IController>` (same package as `SampleController`/`JobController`); boots Javalin in `postStart()` on `J.a`, stops in `stop()`.
  - `art.arcane.react.api.web.*` — `WebConfiguration`, `WebAuth`, `PairingToken`, `AuditLog`, `WebSockets`, and resources: `MetricsResource`, `FeatureResource`, `TweakResource`, `ActionResource`, `ConfigResource`, `EnvironmentResource`, `IdentityResource`.
- **REST** (all `/api/v1`, JSON `{data}`/`{error}`):
  - `GET /identity` — plugin version, server brand, Folia flag, server-id.
  - `GET /metrics` — snapshot of all samplers (`SampleController.getSamplers().all()` → `sample()` + `sampleFormatted()`); cross-plugin via `RemoteSamplerBridge.snapshot(pluginId)`.
  - `GET /metrics/{id}/history` — `Graph.of(sampler)` 128-slot ring serialized.
  - `GET/PUT /features`, `/features/{id}`, `/features/{id}/config` — `React.feature(id)`, per-item TOML category, reload path.
  - `GET/PUT /tweaks`, `/tweaks/{id}`, `/tweaks/{id}/config`.
  - `POST /actions/{id}/execute` — body params → `React.action(id).createForceful(params).queue()`; `op:execute` scope + confirm token + audit; returns **202 Accepted** + ticket id.
  - `GET /environment` (oshi), `GET/PUT /config` (`config.toml`).
- **WebSocket** (`/ws/...`, server-push, coalesced, per-socket 4–10 Hz throttle, drop-oldest):
  - `/ws/metrics` — piggyback `SampleController` (3s) or `MapController` (250ms) tick; one serialized frame fan-out.
  - `/ws/incidents` — `SamplerIncidentScore` threshold crossings + `React.reportedErrorsSince()` deltas.
  - `/ws/logs` — bounded-ring `Handler` on React's logger, batched per tick.
- **Data contract (per sampler):** `{id, name, value, suffix, min, max, history[128]}`.
- **Config — `web.toml`** (`WebConfiguration`, `@ConfigDoc`): `enabled=false`, `bindAddress=127.0.0.1`, `port=9696`, `tlsEnabled`, `keystorePath/keystorePassword`, `corsOrigins`, `wsPushHz`, `requireTokenForReads`.

### 3.2 Tier 2 — Cloud Relay (P5, optional, NAT traversal)

- **Broker:** small hosted **Dart** service (`shelf` + WS). Servers dial **out** over `wss://relay.arcane.art/agent` (no inbound port-forward); app connects `wss://relay.arcane.art/app`. Broker is a **zero-trust switch** — routes frames by `server-id`, never decrypts payloads. Holds online-server registry, per-connection rate limits, metadata-only audit tap. Stateless, horizontally scalable; server-id→connection map in Redis.
- **Identity (reused verbatim from Wormholes):** `IdentityStore` (per-instance Ed25519 keypair, owner-only `server.key`), nonce mutual-signature `Handshake`, base64 `PortalCode` codec, `PeerTrustStore` (TOFU pubkey pinning), SHA-256 short fingerprints. Source: `WormholesPlugin/src/main/java/art/arcane/wormholes/network/{PortalCode,Handshake,IdentityStore,PeerTrustStore}.java`.
- **Direct-vs-relay:** app **races** direct-LAN WS vs relay (happy-eyeballs), keeps first to handshake; same pinned identity either way; caches last-good path.

### 3.3 Tier 3 — Web App (`arcane_jaspr`)

- **Structure:** single Jaspr project, `mode: static` SSG. `lib/main.client.dart` hydrates `ClientApp`; `lib/main.server.dart` SSG under `ShadcnStylesheet(theme: ShadcnTheme.midnight)`. Root `ArcaneApp(brightness: dark)`. The live dashboard is a hydrated `@client` island owning all sockets/charts. Routing via `jaspr_router`: `/` fleet, `/server/:id/:screen`. Dev: `dart run arcane_jaspr:serve`; prod: `jaspr build` static output.
- **Shell:** `ArcaneScaffold(sidebar:, body:, actions:, title:)` + `ArcaneSidebar` (fleet switcher header, session footer). `ArcaneSidebarGroup` "Fleet" + per-server group; each item a Lucide icon + live `ArcaneStatusBadge`. Sub-views via `ArcaneTabs`; grids via `ArcaneFlexiCards`/`Collection`; tables via `DataTable<T>`.
- **State / real-time:** per-server `ConnectionManager` (mirrors `ArcaneAuthProvider`) subscribing to `Stream<ServerSnapshot>` → `InheritedWidget` `ServerScope` (Pylon-equivalent via `dependOnInheritedComponentOfExactType`). Service layer: `package:web` `WebSocket` → plugin WS, `package:http` 2s polling fallback on error/close. 128-pt `RingBuffer` per metric; `setState` on snapshot. `FleetManager` fans out N managers; sidebar reads only status enum (`updateShouldNotify` on status/seq) so one server's churn doesn't re-render siblings.
- **Charting (highest risk):** `ArcaneChart` is static bars only. For dense live timeseries, wrap **uPlot** via `dart:js_interop` in a `<canvas>` island, fed the ring buffer via `setData` (no Dart rebuild); guard `kIsWeb` so SSG skips canvas. **Fallback:** custom SVG `<polyline>` Dart chart. Native `ArcaneChart` used only for static categorical bars.
- **Theming:** `ShadcnTheme.midnight`, dark-first; status via `ArcaneStatusBadge.success/.warning/.error/.offline` from TPS/MSPT thresholds; `Glass`/`ArcaneCard` surfaces; `Skeleton` while connecting.
- **Auth/session:** reuse `JasprAuthService` + `ArcaneAuthProvider` + `auth_guard`; bearer in `localStorage`; same bearer authorizes WS upgrade + REST; per-server credentials keyed by server-id.
- **Offline UX:** `ConnectionManager` state machine `connecting → live → degraded(polling) → offline`; `Sonner`/toast on transitions; freeze last snapshot, dim cards, exponential backoff (cap 30s) + manual Reconnect; never throw on socket close.
- **Responsive:** desktop-first; `ArcaneSidebar(collapsed:)` auto-collapse → `Drawer`/`Sheet` on narrow; `ArcaneFlexiCards` reflow single-column; `DataTable` in `ScrollArea`.

## 4. Security & Guardrails

- **Pairing:** operator runs `/react web pair [label=]` → mints single-use 10-min `PortalCode` (`RCT1.` + server-id + Ed25519 pubkey + relay URL + optional LAN hosts/port + 6-digit confirm word). Operator pastes into app "Add Server"; app generates keypair, runs mutual-signature handshake, both pin pubkeys (TOFU). **Displayed fingerprint must match `/react` console output** (defeats relay MITM). On success server issues a scoped credential; code is burned.
- **Tokens:** `Authorization: Bearer <id.sig>`, HMAC-SHA256 over `id|label|issuedAt|scopes` with per-install secret (`plugins/React/web/secret.key`); records in `plugins/React/web/tokens.toml`. Short TTL + silent refresh. Every control frame HMAC'd with monotonic counter + nonce → **replay protection**.
- **RBAC:** `viewer` (read), `operator` (toggle features/tweaks, presets), `admin` (config writes, destructive actions, reload). Role embedded in credential, enforced server-side.
- **Destructive-op guardrail:** purge-entities/chunks, quarantine, trim, GC, incident-playbook, reload require **admin + fresh re-typed confirm word**; rate-limited per token.
- **Audit:** append-only JSONL `plugins/React/web/audit.log` (token id, fingerprint, role, ip, route, params, result, ts), written via `J.a`; metadata-mirrored to relay (P5).
- **Revocation:** `/react web revoke <id>` / `/react web unpair <fingerprint>` drops the pinned key; refresh refused → token dies within one TTL; relay drops mapping immediately. `/react web list` enumerates active grants.

## 5. Capability Map — "Everything Possible"

Counts are the **real** controllable surface (the old "54/20" included placeholders + base classes).

### 5.1 Monitoring — samplers (~79) by group

| Group | Count | IDs |
|---|---|---|
| Tick / TPS / MSPT | 9 | ticks-per-second, tick-time, tick-ms-p50, tick-ms-p95, tick-ms-p99, tick-spike-rate, per-world-tick-time, top-world-mspt, top-chunk-cost |
| Memory & GC | 7 | memory-used, memory-free, memory-garbage, memory-pressure, memory-used-after-gc, gc-time-percent, gc-pause-p95 |
| Entities / Players / Network | 6 | entities, entity-ai-active-count, entities-spawns, players, player-ping-p95, ping-jitter |
| Chunks & Worldgen | 5 | chunks, chunks-loaded, chunks-generated, chunk-load-ms, chunk-gen-ms |
| Redstone / Hopper / Physics / Fluids | 14 | redstone, redstone-burst-rate, redstone-tick-time, hopper, hopper-tick-time, hopper-chain-coalescing, physics, physics-tick-time, fluid, fluid-tick-time, crop-fast-forward, lazy-gravity-skipped, spawner-light-cache-skipped, explosion-packet-reduction |
| Events & Plugins | 3 | event-handles-per-tick, events-listeners, event-time |
| Incidents & Backlog | 3 | incident-score, backlog-growth-rate, scheduler-backlog |
| React Internals / Jobs / CPU | 8 | react-async-tick-time, react-sync-tick-time, react-jobs-queue, react-job-queue-time, react-job-budget, processor-system-load, processor-process-load, processor-outside-load |
| Persistence & Maintenance | 2 | world-save-duration, pdc-write-batcher |
| Cross-plugin Integrations | 15 | adapt-ability-checks-per-tick, adapt-ability-ops, adapt-session-load, adapt-world-policy-latency, iris-biome-cache-hit-rate, iris-chunk-stream-ms, iris-pregen-queue, wormholes-block-changes, wormholes-packets, wormholes-portals, wormholes-projection-observers, wormholes-projection-render-ms, wormholes-projections-active, wormholes-spoofed-entities, wormholes-traversals |
| Spatial Visualizations | 10 | entity-pressure-heatmap, chunk-load-gen-cost-map, chunk-sampler-map, redstone-activity-heatmap, hopper-container-throughput-map, tick-spike-origin-replay-map, plugin-event-impact-pie-map, plugin-event-impact-list-map, iris-biome-chunk-share-pie-map, iris-world-chunk-share-pie-map |

**Monitor screens (per server):** Overview · Performance/Tick · Memory & GC · Entities & Players · Chunks & Worldgen · Per-World drill-down · Redstone/Hopper/Mechanics · Events & Plugins · Incidents Timeline · React Internals/Jobs & CPU · Integrations (conditional on detected plugin) · Spatial Heatmaps.

### 5.2 Control — features (48) + tweaks (19)

| Group | Count | IDs |
|---|---|---|
| Global & System | 5 | react-master-enable, feature-tweak-reconcile, config-persist-toml, integration-secrets, per-world-pressure-system |
| Load Governors | 9 | dynamic-view-distance, dynamic-activation-range, activation-range-governor, tracker-range-governor, random-tick-governor, pathfinder-budget, per-world-tick-budget, afk-view-shedding, adaptive-entity-sleep |
| Incident Orchestrators & Surge Guards | 5 | incident-mode, feature-trinity-incident-mode, circuit-manager, feature-adapt-runtime-surge-guard, feature-iris-terrain-surge-guard |
| Entity Population Optimizations | 7 | mob-stacking, entity-trimmer, item-super-stacker, item-backpressure, spawn-burst-limiter, spawner-light-cache, minecart-tether |
| Block & World-Tick Optimizations | 7 | fast-leaf-decay, fast-explosions, crop-fast-forward, farm-burst-smoother, world-save-staggering, lazy-gravity, explosion-packet-batching |
| Hopper & Container Optimizations | 4 | hopper-item-index, hopper-token-bucket, hopper-chain-coalescing, furnace-brew-batching |
| Redstone / Portal / Chunk Smoothers | 3 | redstone-clock-governor, portal-traffic-smoother, chunk-quarantine |
| Item & XP Tweaks | 3 | item-despawn-accelerator, experience-orb-merge, fast-drops |
| Entity Behavior Tweaks | 5 | entity-crowd-prevention, entity-hardstop, entity-bubbler, fast-entity-incineration, vehicle-idle-brake |
| Block & Fluid Tweaks | 5 | fast-fluids, fast-snow, fast-fire, fast-falling-blocks, fast-columns |
| Hopper / Projectile / Spawner / Server Tweaks | 6 | hopper-index, hopper-limit, projectile-limiter, spawner-player-radius, server-hibernator, reload-confirm |
| Diagnostics & Map Overlays (view-only) | 13 | tick-spike-origin-replay-map, entity-pressure-heatmap, redstone-activity-heatmap, chunk-load-gen-cost-map, chunk-sampler-map, hopper-container-throughput-map, adapt-runtime-pressure-overlay, iris-generation-pressure-overlay, player-impact-overlay, iris-biome-chunk-share-pie-map, iris-world-chunk-share-pie-map, plugin-event-impact-pie-map, plugin-event-impact-list-map |

**Mechanism:** every Feature extends `ReactFeature`, every Tweak `ReactTweak`, both with a boolean `enabled` reconciled live each tick by `FeatureController`/`TweakController`; typed knobs are `@ConfigDoc` fields persisted to per-item TOML.

**Control screens:** Optimization Grid (category-sectioned feature cards w/ master toggle + state) · Feature/Tweak Config Sheet (drawer rendering each `@ConfigDoc` knob with the right widget) · Tweaks List · Incident & Governor Dashboard (live governor state) · Per-World Overrides (NORMAL/PRESSURE/PANIC badges, per-world tick budgets) · Integrations & Capability Gating.

### 5.3 Operate — actions (8) + incidents + diagnostics

| Action | Destructive |
|---|---|
| purge-entities | yes |
| purge-chunks | yes |
| quarantine-hot-chunks | yes |
| trim-entities-by-age-priority | yes |
| collect-garbage | yes (disruptive) |
| incident-playbook | yes |
| hopper-network-normalize | no |
| prewarm-critical-chunks | no |

Each dispatched `React.action(id).createForceful(params).queue()` on the Folia-safe action scheduler.

**Operate screens:** Actions Console (guardrails + audit) · Incident Center (live incident-score gauge + timeline + contributing-factor breakdown + one-tap playbooks) · Environment & Diagnostics (oshi: CPU/mem/GPU/disk/NIC/sensors/power + server type/version + NMS bridge + integration status) · Benchmark & Loadtest Runner (cpu/drive/memory benchmarks + `/react test run` self-test render + `/react test loadtest`) · Live Config Editor (mirrors `ReactConfigGUI`: typed tree + inline docs + Off/Light/Balanced/High presets + atomic write/rollback) · Command Console · Logs tail.

### 5.4 Fleet (multi-server)

- **Pairing/inventory:** server list, add+pair wizard, connection-status, remove/rename, reconnect.
- **Aggregate overview:** TPS rollup, composite health score, player rollup, resource rollup, alert rollup, integration rollup, gen-pressure rollup, "needs attention" list.
- **Per-server health cards:** summary, module-state, integration badges, drilldown, quick-profile.
- **Cross-server comparison (v2):** metric-grid overlay, leaderboard, incident correlation.
- **Alerts inbox:** unified feed (threshold trips + lifecycle), filter, ack/resolve, thresholds, notify. Sourced from each server's `IntegrationController` timeline + React perf alerts.
- **Org:** tags, groups, bulk actions. **Account:** auth, roles, token management, app prefs.

## 6. Integration Contract Reuse

React's cross-plugin seam is VolmLib's `IntegrationServiceContract` (Bukkit `ServicesManager` service: handshake → heartbeat → `sampleMetrics(keys)` with `IntegrationMetricSample` envelopes + `IntegrationMetricSchema`). `RemoteSamplerBridge` is React's cache of *other* plugins' samples. React is currently a **consumer** (iris/adapt/wormholes) and an **empty provider** via `ReactIntegrationService`. The web API reads samplers directly; no change to the integration contract required, but the existing `PapiExpansion` (`%react_...%`) confirms the value vocabulary and may be cleaned up.

## 7. Phased Delivery

Each phase ships working, testable software and gets its **own implementation plan** when reached.

- **P0 — Prove the pipe:** `WebController` + Javalin boot + `GET /identity` + `GET /metrics` + `GET /metrics/{id}/history` + pairing-token auth; app shell (`ArcaneApp`/`ArcaneScaffold`/sidebar), single-server connect, **Overview + Performance + Memory** screens live with **uPlot** charts (de-risks charting + real-time first). Direct-embedded only.
- **P1 — Full monitoring:** remaining v1 metric screens + `/ws/metrics` live push + heatmap rendering (chunk-grid coordinate export).
- **P2 — Control plane:** `GET/PUT` features/tweaks + config sheets + Optimization Grid + Incident & Governor Dashboard + Per-World Overrides.
- **P3 — Operate:** actions + guardrails + audit + Incident Center + Live TOML Config Editor + Environment/Diagnostics + Logs (`/ws/logs`).
- **P4 — Fleet:** multi-server pairing/inventory + Fleet Dashboard + Alerts Inbox + RBAC roles + tags/groups.
- **P5 — Relay:** Dart broker + happy-eyeballs direct/relay racing for NAT'd fleets (no pairing/security changes) + cross-server comparison.

## 8. Testing Strategy

- **Plugin API (Java):** JUnit5 + Mockito unit tests for `WebAuth` (HMAC sign/verify, scope, replay counter), `PairingToken`, `AuditLog`, resource serializers (sampler→JSON contract). Integration test boots Javalin on an ephemeral port, asserts `/identity` + `/metrics` shape + 401 on bad token + 202 on action execute. Reuse the existing `/react test` harness for live verification. Folia-safety: assert world-touching routes marshal (no direct main-thread access off Jetty workers).
- **App (Dart):** `jaspr_test` widget tests for screens (render with a fake `ServerSnapshot`); unit tests for `ConnectionManager` state machine (connecting→live→degraded→offline, backoff), `RingBuffer`, snapshot decode. Mock the WebSocket/HTTP layer.
- **Relay (Dart, P5):** unit tests for routing-by-server-id, auth rejection, replay rejection.
- **E2E:** browser automation against a running plugin (`/react web pair` → app connect → charts render → toggle a feature → verify state) per the verification rules — UI claims require browser evidence.
- **Bar:** ≥80% coverage on new logic; full suite green before any phase is "done."

## 9. Top Risks

1. **Charting** — uPlot `dart:js_interop` lifecycle across SSG/hydrate/dispose is the #1 unknown; SVG `<polyline>` fallback designed in. Validated first in P0.
2. **Real-time** — hand-rolled `package:web` WS reconnection + selective re-render to avoid full-tree thrash under multi-server ~10 Hz streams.
3. **Folia thread-safety** — every world/entity/action touch from a Jetty worker must marshal; an unmarshalled call is an "Asynchronous … !" crash. Covered by tests + the existing `J` bridges.
4. **Shared web core relocation** — `VolmLib/shared` web skeleton must not assume non-relocated package (BileTools).

## 10. Project Layout

```
React/
  React/                      # plugin (Java) — add core/controller/WebController + api/web/*
  reactor/                    # NEW arcane_jaspr web app (replaces react_remote)
  reactor-relay/              # NEW Dart relay broker (P5)
  react_remote/               # DELETED at P0 start
  docs/specs/2026-06-27-reactor-design.md
  docs/plans/2026-06-27-reactor-p0-plan.md   # + one per phase
VolmLib/shared/               # art.arcane.volmlib.web transport/auth/audit skeleton
```
