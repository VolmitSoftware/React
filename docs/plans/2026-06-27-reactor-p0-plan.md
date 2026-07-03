# Reactor P0 — "Prove the Pipe" Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Pair a single server, connect the new `reactor` web app to React's new embedded API, and render live **Overview / Performance / Memory** dashboards with uPlot charts — end-to-end, proving the charting + real-time + auth pipeline before scaling breadth.

**Architecture:** Tier-1 embedded Javalin (Jetty via SlimJar) inside the React plugin exposes token-authed REST (`/api/v1/identity`, `/metrics`, `/metrics/{id}/history`). Tier-3 `arcane_jaspr` SSG app (hydrated island) polls REST every 2s into per-metric 128-pt ring buffers and renders uPlot timeseries. Pairing is a bearer-token code minted by `/react web pair`. No WebSocket and no relay yet (P1/P5).

**Tech Stack:** Java 25 + Javalin 6 (SlimJar) + JUnit5/Mockito (plugin); Dart 3.10 + Jaspr 0.22 + arcane_jaspr 3.3.0 + `package:web` + `package:http` + uPlot (JS interop) + jaspr_test (app).

## Global Constraints

- App: `arcane_jaspr` 3.3.0, `ShadcnTheme.midnight`, dark-first, **no Material Design**.
- Plugin: Java 25, no `var`, explicit types, no wildcard imports, switch expressions, **no `@Deprecated`**, **no code comments unless requested**, no backward-compat shims.
- Embedded server via **SlimJar** (`slim('io.javalin:javalin:6.x')`) — never shade.
- **Folia-safety:** sampler/config/oshi reads run directly on Jetty workers; any world/entity/chunk/action touch marshals via `J.sResult`/`J.runRegionResult`. P0 routes touch only samplers/config/identity → off-thread safe.
- Plugin defaults: `web.toml` `enabled=false`, `bindAddress=127.0.0.1`, `port=9696`.
- Update `MasterChangelog.MD` React section for the new `/react web` commands + embedded API (operator-visible).
- **Never** deploy/copy JARs into server plugin folders. **Never** run git write commands.
- Test bar: new logic ≥80% coverage; full suite green before P0 is "done"; live browser verification of the three screens.

## File Structure

**Plugin (`React/React/src/main/java/art/arcane/react/`):**
- `api/web/WebConfiguration.java` — `web.toml` model (`@ConfigDoc`).
- `api/web/WebSecret.java` — load/create `plugins/React/web/secret.key`.
- `api/web/PairingToken.java` — HMAC token mint/verify, scopes.
- `api/web/PairingCode.java` — base64 self-describing pairing code (host/port/token/confirm).
- `api/web/WebAuth.java` — Javalin before-handler: Bearer verify + scope + replay counter.
- `api/web/AuditLog.java` — append-only JSONL (`plugins/React/web/audit.log`).
- `api/web/dto/SamplerDto.java`, `api/web/dto/IdentityDto.java` — wire DTOs.
- `api/web/MetricsSerializer.java` — sampler → `SamplerDto`.
- `api/web/resource/IdentityResource.java`, `MetricsResource.java`.
- `core/controller/WebController.java` — Javalin lifecycle (auto-discovered controller).
- `content/directorcommand/CommandWeb.java` — `/react web pair|list|revoke`; wired into `CommandReact`.
- Tests under `React/React/src/test/java/art/arcane/react/web/`.

**App (`React/reactor/`):** new arcane_jaspr project (replaces deleted `react_remote`).
- `pubspec.yaml`, `jaspr.yaml`, `web/index.html` (loads uPlot), `lib/main.client.dart`, `lib/main.server.dart`.
- `lib/app/reactor_app.dart` — `ArcaneApp` + router + scaffold shell.
- `lib/model/{sampler_sample,server_snapshot,ring_buffer,server_credential}.dart`.
- `lib/service/react_client.dart` — REST client.
- `lib/state/{connection_manager,server_scope,fleet_manager}.dart`.
- `lib/chart/{timeseries_chart,uplot_interop,svg_fallback_chart}.dart`.
- `lib/screen/{overview,performance,memory,add_server}.dart`.
- `lib/widget/{stat_tile,gauge,status_dot}.dart`.
- `test/` — jaspr_test + unit tests mirroring `lib/`.

---

## PART A — Plugin embedded API (Java)

### Task 1: `web.toml` configuration model

**Files:**
- Create: `React/React/src/main/java/art/arcane/react/api/web/WebConfiguration.java`
- Test: `React/React/src/test/java/art/arcane/react/web/WebConfigurationTest.java`

**Interfaces:**
- Produces: `WebConfiguration` with fields `enabled`, `bindAddress`, `port`, `tlsEnabled`, `keystorePath`, `keystorePassword`, `corsOrigins` (`List<String>`), `wsPushHz`, `requireTokenForReads`. Follows the `ReactConfiguration` `@ConfigDoc` pattern (read `React/React/src/main/java/art/arcane/react/.../ReactConfiguration.java` to match the exact annotation + load idiom).

- [ ] **Step 1: Read the existing config pattern** — open the React main configuration class and one feature config to copy the `@ConfigDoc`/`@ConfigDescription` + TOML load idiom exactly.

- [ ] **Step 2: Write the failing test**
```java
@Test
void defaultsAreSafeAndDisabled() {
  WebConfiguration c = new WebConfiguration();
  assertFalse(c.isEnabled());
  assertEquals("127.0.0.1", c.getBindAddress());
  assertEquals(9696, c.getPort());
  assertFalse(c.isRequireTokenForReads() == false && c.isEnabled());
}
```

- [ ] **Step 3: Run test — expect FAIL** (`WebConfiguration` does not exist).

- [ ] **Step 4: Implement** the class with explicit-typed fields, `@ConfigDoc` annotations matching the project idiom, and the defaults above (`enabled=false`, `bindAddress="127.0.0.1"`, `port=9696`, `tlsEnabled=false`, `wsPushHz=5`, `requireTokenForReads=true`, `corsOrigins=new ArrayList<>()`).

- [ ] **Step 5: Run test — expect PASS.** No git commit (user handles git).

### Task 2: Web secret + pairing token (HMAC)

**Files:**
- Create: `api/web/WebSecret.java`, `api/web/PairingToken.java`
- Test: `web/PairingTokenTest.java`

**Interfaces:**
- `WebSecret.load(File dataFolder): byte[]` — returns existing `web/secret.key` bytes or creates a 32-byte random key (`SecureRandom`) written 0600.
- `PairingToken`: `static String mint(byte[] secret, String id, String label, long issuedAt, Set<String> scopes)` → `"<id>.<base64url(hmacSha256)>"`; `static Optional<PairingToken> verify(byte[] secret, String bearer, TokenStore store)`; instance `hasScope(String)`. Scopes: `read`, `op:execute`, `admin`.

- [ ] **Step 1: Write the failing test**
```java
@Test
void mintedTokenVerifiesAndTamperFails() {
  byte[] secret = new byte[32]; new SecureRandom().nextBytes(secret);
  String t = PairingToken.mint(secret, "abc", "laptop", 1000L, Set.of("read", "op:execute"));
  TokenStore store = TokenStore.inMemory(new TokenRecord("abc", "laptop", 1000L, Set.of("read", "op:execute")));
  assertTrue(PairingToken.verify(secret, t, store).orElseThrow().hasScope("op:execute"));
  assertTrue(PairingToken.verify(secret, t + "x", store).isEmpty());
  byte[] other = new byte[32]; new SecureRandom().nextBytes(other);
  assertTrue(PairingToken.verify(other, t, store).isEmpty());
}
```

- [ ] **Step 2: Run — expect FAIL.**

- [ ] **Step 3: Implement** `WebSecret`, `TokenStore`/`TokenRecord` (in-memory + TOML-backed `plugins/React/web/tokens.toml`), and `PairingToken` using `javax.crypto.Mac` HMAC-SHA256, constant-time compare (`MessageDigest.isEqual`), base64url no-padding. `verify` parses `id.sig`, looks up the record, recomputes HMAC over `id|label|issuedAt|sortedScopes`, compares.

- [ ] **Step 4: Run — expect PASS.**

### Task 3: Replay-protected auth before-handler

**Files:**
- Create: `api/web/WebAuth.java`
- Test: `web/WebAuthTest.java`

**Interfaces:**
- `WebAuth(byte[] secret, TokenStore store)`; `void handle(Context ctx)` (Javalin `Handler`) — reads `Authorization: Bearer`, verifies, sets `ctx.attribute("token", PairingToken)`, throws `UnauthorizedResponse` on failure. `requireScope(Context, String)` helper for routes.
- Replay: control routes carry header `X-React-Counter` (monotonic per token); `WebAuth` rejects a counter `<=` the last seen for that token id (in-memory `ConcurrentHashMap<String,Long>`).

- [ ] **Step 1: Write failing tests** — (a) missing header → unauthorized; (b) valid read token → passes a `read` route; (c) read-only token → `requireScope("op:execute")` rejects; (d) replayed counter rejected, higher counter accepted. Mock Javalin `Context` with Mockito.

- [ ] **Step 2: Run — expect FAIL.**

- [ ] **Step 3: Implement** `WebAuth` per the interface; throw `io.javalin.http.UnauthorizedResponse`/`ForbiddenResponse`.

- [ ] **Step 4: Run — expect PASS.**

### Task 4: Sampler wire DTO + serializer (the contract)

**Files:**
- Create: `api/web/dto/SamplerDto.java`, `api/web/dto/IdentityDto.java`, `api/web/MetricsSerializer.java`
- Test: `web/MetricsSerializerTest.java`

**Interfaces:**
- `SamplerDto`: `String id; String name; double value; String suffix; double min; double max; double[] history` (length ≤128). This is the **canonical contract** the Dart app decodes — keep field names stable.
- `MetricsSerializer.toDto(Sampler s)` — reads `s.sample()`, `s.sampleFormatted()` (name/suffix), and the 128-slot history via `Graph.of(s)` (`get(0..n-1)`, `getMin`, `getMax`). **Read `api/sampler/Sampler.java` + the `Graph`/`RollingSequence` source to bind the exact accessors.**

- [ ] **Step 1: Write the failing test** using a fake `Sampler` whose history is a known 8-value sequence:
```java
@Test
void serializesValueHistoryAndExtremes() {
  Sampler fake = new FakeSampler("tick-time", 42.0, "ms", new double[]{40,41,42,43,42,41,40,42});
  SamplerDto d = new MetricsSerializer().toDto(fake);
  assertEquals("tick-time", d.id);
  assertEquals(42.0, d.value, 1e-9);
  assertEquals("ms", d.suffix);
  assertEquals(8, d.history.length);
  assertEquals(43.0, d.max, 1e-9);
  assertEquals(40.0, d.min, 1e-9);
}
```

- [ ] **Step 2: Run — expect FAIL.**

- [ ] **Step 3: Implement** `SamplerDto`, `IdentityDto` (`version`, `serverBrand`, `folia`, `serverId`), and `MetricsSerializer.toDto`. Use Gson (already a React dep) for JSON elsewhere; the DTO itself is a plain typed record/class.

- [ ] **Step 4: Run — expect PASS.**

### Task 5: Identity + Metrics resources

**Files:**
- Create: `api/web/resource/IdentityResource.java`, `api/web/resource/MetricsResource.java`
- Test: `web/MetricsResourceTest.java`

**Interfaces:**
- `MetricsResource(SampleController samplers, MetricsSerializer serializer)`; `void snapshot(Context ctx)` → `{data: SamplerDto[]}` over **all** registered samplers (`samplers.getSamplers().all()` — confirm accessor against `core/controller/SampleController.java`); `void history(Context ctx)` → single `SamplerDto` for `ctx.pathParam("id")`, 404 if unknown.
- `IdentityResource(React react)`; `void get(Context ctx)` → `IdentityDto`.

- [ ] **Step 1: Read** `SampleController` to confirm the registry accessor + `React.sampler(String)` lookup.

- [ ] **Step 2: Write failing test** — mock `SampleController` returning two fake samplers; assert `snapshot` writes a 2-element data array; assert `history("nope")` → 404 (`NotFoundResponse`). Use Mockito + a captured `Context.json(...)` argument.

- [ ] **Step 3: Run — expect FAIL.**

- [ ] **Step 4: Implement** both resources; `history` resolves via `React.sampler(id)` then `serializer.toDto`.

- [ ] **Step 5: Run — expect PASS.**

### Task 6: `WebController` — Javalin lifecycle + integration test

**Files:**
- Create: `core/controller/WebController.java`
- Modify: `React/React/build.gradle` (add `slim('io.javalin:javalin:6.x')` to the existing slim block)
- Test: `web/WebControllerIntegrationTest.java`

**Interfaces:**
- `WebController` is auto-discovered by `Registry<IController>` (place in the same package as `SampleController`; confirm the controller base/interface + lifecycle hooks by reading `core/controller/JobController.java`). Boots Javalin in `postStart()` on `J.a` (off-thread), binds `WebConfiguration.bindAddress:port`, registers `WebAuth` before-handler + routes, only when `enabled`. `stop()` → `app.stop()`.
- Routes: `GET /api/v1/identity` (read), `GET /api/v1/metrics` (read, gated by `requireTokenForReads`), `GET /api/v1/metrics/{id}/history` (read).

- [ ] **Step 1: Read** `JobController.java` for the exact controller contract (`start`/`postStart`/`stop`, `Registry` discovery).

- [ ] **Step 2: Add the dependency** to `build.gradle`'s slim block; run `./gradlew help` to confirm resolution (do not run server-deploy tasks).

- [ ] **Step 3: Write the failing integration test** — construct `WebController` with `enabled=true`, `port=0` (ephemeral), a mock `SampleController`, and a known token; start it; using `java.net.http.HttpClient`:
  - `GET /api/v1/identity` no token → 401.
  - `GET /api/v1/identity` with `Authorization: Bearer <token>` → 200 + JSON has `version`.
  - `GET /api/v1/metrics` with token → 200 + `data` array length 2.
  - Stop the controller; assert port closed.

- [ ] **Step 4: Run — expect FAIL.**

- [ ] **Step 5: Implement** `WebController`. Read the ephemeral bound port back from Javalin/Jetty for the test. Guard all Bukkit access out of P0 routes (none needed).

- [ ] **Step 6: Run — expect PASS.**

### Task 7: `AuditLog` + `/react web` command

**Files:**
- Create: `api/web/AuditLog.java`, `api/web/PairingCode.java`, `content/directorcommand/CommandWeb.java`
- Modify: `content/directorcommand/CommandReact.java` (add `private CommandWeb web;` sub-executor field)
- Test: `web/AuditLogTest.java`, `web/PairingCodeTest.java`

**Interfaces:**
- `AuditLog(File dataFolder)`; `void append(String actor, String op, String detail, String result)` → appends one JSON line to `web/audit.log` via `J.a` (never on a Jetty worker). `List<String> tail(int n)`.
- `PairingCode`: `static String encode(String host, int port, String tokenId, String tokenSig, String confirmWord)` → `"RCT1." + base64url(json)`; `static PairingCode decode(String code)`. (Ed25519 mutual handshake is **P5**; P0 pairing is bearer-token + visible confirm word.)
- `CommandWeb` Decree sub-commands (keyed `@Param` optionals per the Director keyed-args rule):
  - `pair label=<string>` — mint token (`read`+`op:execute` scopes), build `PairingCode`, print the code **and** a SHA-256 fingerprint + 6-digit confirm word to console; audit `pair`.
  - `list` — print active token ids/labels/issuedAt.
  - `revoke id=<string>` — remove token from `TokenStore`; audit `revoke`.

- [ ] **Step 1: Write failing tests** — `PairingCode` encode/decode roundtrip; `AuditLog.append` then `tail(1)` returns the line with the fields.

- [ ] **Step 2: Run — expect FAIL.**

- [ ] **Step 3: Implement** `AuditLog`, `PairingCode`, `CommandWeb`; wire `web` field into `CommandReact` exactly like the existing `test`/`config` sub-executors (read `CommandReact.java`).

- [ ] **Step 4: Run — expect PASS.**

### Task 8: Changelog + plugin build green

**Files:** Modify `MasterChangelog.MD` (React section), verify build.

- [ ] **Step 1:** Append under React `### Added`: embedded web API (`/api/v1` metrics, token auth) + `/react web pair|list|revoke` commands. Deduplicate against existing entries.
- [ ] **Step 2:** Run `cd React/React && ./gradlew test` — **expect all green** (new web tests + existing suite). Fix any failure before proceeding.
- [ ] **Step 3:** Run `./gradlew shadowJar` — expect success (confirms Javalin SlimJar wiring builds).

---

## PART B — `reactor` web app (Dart / arcane_jaspr)

### Task 9: Scaffold the project (delete react_remote)

**Files:**
- Delete: `React/react_remote/` (entire dir)
- Create: `React/reactor/pubspec.yaml`, `jaspr.yaml`, `web/index.html`, `lib/main.client.dart`, `lib/main.server.dart`

**Interfaces:**
- `pubspec.yaml` deps: `jaspr: ^0.22.1`, `arcane_jaspr: ^3.3.0`, `arcane_jaspr_shadcn: ^3.x`, `jaspr_router`, `http: ^1.6.0`, `web: ^1.x`; dev: `build_runner`, `jaspr_builder`, `jaspr_test`. `jaspr.yaml`: `mode: static`.
- `web/index.html` includes uPlot via CDN `<script>` + `<link>` (pin a version) before the app bundle.

- [ ] **Step 1:** Delete `React/react_remote/`.
- [ ] **Step 2:** Create the project files. `main.client.dart` → `Jaspr.initializeApp(options: defaultClientOptions); runApp(const ReactorApp());`. `main.server.dart` → static SSG entrypoint under `ShadcnStylesheet(theme: ShadcnTheme.midnight)`.
- [ ] **Step 3:** Run `cd React/reactor && dart pub get` — expect success.
- [ ] **Step 4:** Run `dart run arcane_jaspr:serve` briefly; confirm it builds + serves (then stop). Commit nothing.

### Task 10: Core models + RingBuffer

**Files:**
- Create: `lib/model/sampler_sample.dart`, `lib/model/server_snapshot.dart`, `lib/model/ring_buffer.dart`, `lib/model/server_credential.dart`
- Test: `test/model/ring_buffer_test.dart`, `test/model/server_snapshot_test.dart`

**Interfaces:**
- `class SamplerSample { final String id, name, suffix; final double value, min, max; final List<double> history; SamplerSample.fromJson(Map<String,dynamic>); }` — field names must match `SamplerDto`.
- `class ServerSnapshot { final Map<String, SamplerSample> byId; final DateTime at; SamplerSample? sampler(String id); }`.
- `class RingBuffer { RingBuffer(this.capacity); void add(double v); List<double> toList(); }` capacity 128, drops oldest.
- `class ServerCredential { final String id, label, host; final int port; final String bearer; ... toJson/fromJson; }`.

- [ ] **Step 1: Write failing tests** — `RingBuffer(3)` after adding 1,2,3,4 → `[2,3,4]`; `ServerSnapshot.fromJson` of a 2-sampler `{data:[...]}` payload maps both by id and `value`/`history` decode correctly.
- [ ] **Step 2: Run — expect FAIL.**
- [ ] **Step 3: Implement** the models with explicit types.
- [ ] **Step 4: Run — expect PASS** (`dart test`).

### Task 11: `ReactClient` REST service

**Files:**
- Create: `lib/service/react_client.dart`
- Test: `test/service/react_client_test.dart`

**Interfaces:**
- `class ReactClient { ReactClient(this.cred, {http.Client? client}); Future<IdentityInfo> identity(); Future<ServerSnapshot> metrics(); Future<SamplerSample> history(String id); }` — sends `Authorization: Bearer <cred.bearer>` to `http(s)://<host>:<port>/api/v1/...`. Throws `ReactAuthException` on 401, `ReactUnavailable` on socket error/timeout (2s).

- [ ] **Step 1: Write failing tests** with a `MockClient` (`package:http/testing.dart`): `metrics()` decodes a 2-sampler body; 401 → `ReactAuthException`; connection error → `ReactUnavailable`.
- [ ] **Step 2: Run — expect FAIL.**
- [ ] **Step 3: Implement** `ReactClient` (inject `http.Client` for tests; default real client).
- [ ] **Step 4: Run — expect PASS.**

### Task 12: `ConnectionManager` state machine

**Files:**
- Create: `lib/state/connection_manager.dart`, `lib/state/server_scope.dart`
- Test: `test/state/connection_manager_test.dart`

**Interfaces:**
- `enum ConnState { connecting, live, degraded, offline }`.
- `class ConnectionManager { ConnectionManager(this.client, {this.pollInterval = const Duration(seconds: 2), this.maxBackoff = const Duration(seconds: 30)}); Stream<ServerSnapshot> get snapshots; ConnState get state; void start(); void stop(); }` — polls `client.metrics()`; on success → `live`, push snapshot, seed 128-pt ring buffers per sampler; on `ReactUnavailable` → `degraded` then exponential backoff to `offline`; never throws on poll error.
- `ServerScope extends InheritedWidget` (Jaspr `InheritedComponent`) exposing the latest `ServerSnapshot` + `ConnState`; `updateShouldNotify` true only when `state` or snapshot `seq` changes.

- [ ] **Step 1: Write failing tests** with a fake client (success sequence → `connecting`→`live`; error sequence → `degraded`→backoff→`offline`; recovery → back to `live`). Use a controllable fake clock / injected `pollInterval` of `Duration.zero` + manual pump.
- [ ] **Step 2: Run — expect FAIL.**
- [ ] **Step 3: Implement** the manager + `ServerScope`.
- [ ] **Step 4: Run — expect PASS.**

### Task 13: uPlot chart island + SVG fallback (the #1 risk — validate early)

**Files:**
- Create: `lib/chart/uplot_interop.dart`, `lib/chart/svg_fallback_chart.dart`, `lib/chart/timeseries_chart.dart`
- Test: `test/chart/svg_fallback_chart_test.dart`

**Interfaces:**
- `uplot_interop.dart` — `@JS()` `external` bindings (`package:web` + `dart:js_interop`) for `uPlot` create/`setData`/`destroy`; a `UPlotHandle` wrapper.
- `class TimeseriesChart extends StatefulComponent { TimeseriesChart({required this.series, this.height = 160}); }` — on web + uPlot present, mounts a `<canvas>` island and feeds `series` (List of `(label, List<double>)`) via `setData` on update (no Dart subtree rebuild); disposes the handle on unmount; guards `kIsWeb` so SSG renders an empty placeholder. **If uPlot is absent/interop throws, render `SvgFallbackChart`.**
- `class SvgFallbackChart` — pure-Dart `<svg><polyline>` line chart, no interop.

- [ ] **Step 1: Write a failing widget test** (jaspr_test) that renders `SvgFallbackChart` with `[1,2,3,2,1]` and asserts a `<polyline>` with 5 points is emitted (interop path is exercised manually in the browser, not unit-tested).
- [ ] **Step 2: Run — expect FAIL.**
- [ ] **Step 3: Implement** all three files. Keep `uplot_interop` lifecycle guarded (try/catch → fallback flag).
- [ ] **Step 4: Run — expect PASS.**
- [ ] **Step 5: Browser-validate** the interop path in Task 18's E2E (charts must actually draw + update).

### Task 14: App shell — `ArcaneApp` + sidebar + router

**Files:**
- Create: `lib/app/reactor_app.dart`, `lib/widget/status_dot.dart`
- Test: `test/app/shell_test.dart`

**Interfaces:**
- `class ReactorApp extends StatelessComponent` → `ArcaneApp(brightness: Brightness.dark, stylesheet: ShadcnStylesheet(theme: ShadcnTheme.midnight), home: ReactorShell())`.
- `ReactorShell` → `ArcaneScaffold(sidebar: ArcaneSidebar(...), body: <router outlet>)`; sidebar has a "Fleet" group (Overview, Add Server) and a per-connected-server group (Overview, Performance, Memory for P0) with a live `StatusDot` from `ConnState`. Routes via `jaspr_router`: `/`, `/server/:id/overview|performance|memory`, `/add-server`.

- [ ] **Step 1: Write a failing widget test** — render `ReactorApp` with no servers; assert the sidebar shows "Add Server" and the empty-state body.
- [ ] **Step 2: Run — expect FAIL.**
- [ ] **Step 3: Implement** shell + router + `StatusDot` (`ArcaneStatusBadge.success/.warning/.offline`).
- [ ] **Step 4: Run — expect PASS.**

### Task 15: Add-server pairing flow

**Files:**
- Create: `lib/screen/add_server.dart`, `lib/state/fleet_manager.dart`
- Test: `test/screen/add_server_test.dart`

**Interfaces:**
- `FleetManager` — holds `List<ServerCredential>` persisted in `localStorage` (`package:web`), creates/owns one `ConnectionManager` per server, exposes the active server.
- `AddServerScreen` — paste a `RCT1.` pairing code → decode (host/port/tokenId/tokenSig/confirmWord) → build `ServerCredential` → `FleetManager.add` → run an `identity()` probe → on success route to `/server/:id/overview`; show the decoded fingerprint/confirm word for the operator to match against console; surface errors via `Sonner` toast.

- [ ] **Step 1: Write a failing widget test** — paste a valid encoded code (built by the test), tap "Pair", with a stubbed client `identity()` success → asserts `FleetManager` now has 1 server. Invalid code → error toast, 0 servers.
- [ ] **Step 2: Run — expect FAIL.**
- [ ] **Step 3: Implement** `FleetManager` + `AddServerScreen` (decode mirrors the Java `PairingCode` format).
- [ ] **Step 4: Run — expect PASS.**

### Task 16: Stat tiles + gauge widgets

**Files:**
- Create: `lib/widget/stat_tile.dart`, `lib/widget/gauge.dart`
- Test: `test/widget/stat_tile_test.dart`

**Interfaces:**
- `StatTile({required String label, required SamplerSample? sample})` — `ArcaneCard` + value (NumberTicker-style) + suffix + sparkline (`TimeseriesChart` mini). Renders `--` when sample null.
- `Gauge({required String label, required double value, required double max, required (double,double) thresholds})` — colored arc (green/amber/red) using `ArcaneStatusBadge` semantics.

- [ ] **Step 1: Write failing widget tests** — `StatTile` with a fake sample renders label + formatted value + suffix; null sample renders `--`. `Gauge` value over the red threshold yields the error color class.
- [ ] **Step 2: Run — expect FAIL.**
- [ ] **Step 3: Implement** both widgets.
- [ ] **Step 4: Run — expect PASS.**

### Task 17: Overview + Performance + Memory screens

**Files:**
- Create: `lib/screen/overview.dart`, `lib/screen/performance.dart`, `lib/screen/memory.dart`
- Test: `test/screen/overview_test.dart`, `test/screen/performance_test.dart`, `test/screen/memory_test.dart`

**Interfaces:**
- Each screen reads `ServerScope.of(context).snapshot` and renders from named sampler ids:
  - **Overview:** gauges `ticks-per-second` (0–20), `incident-score` (0–100), `tick-time`; stat tiles `players`, `entities`, `chunks`, `memory-used`, `gc-time-percent`; mini incident strip.
  - **Performance:** `TimeseriesChart` of `tick-time` with `tick-ms-p50/p95/p99` overlays; `tick-spike-rate` sparkline; `top-world-mspt` + `top-chunk-cost` tiles.
  - **Memory:** stacked area `memory-used` / `memory-free` + `memory-used-after-gc` baseline; `memory-pressure` line; `gc-time-percent` gauge; `gc-pause-p95` line.

- [ ] **Step 1: Write failing widget tests** — render each screen wrapped in a `ServerScope` carrying a fake snapshot with the required sampler ids; assert the headline values/labels appear (e.g., Overview shows TPS "19.9", Memory shows the used-heap tile).
- [ ] **Step 2: Run — expect FAIL.**
- [ ] **Step 3: Implement** the three screens using `ArcaneFlexiCards`/`Collection` grids + the widgets from Tasks 13/16.
- [ ] **Step 4: Run — expect PASS** (`dart test` — full app suite green).

### Task 18: Offline UX + live E2E verification

**Files:**
- Modify: `lib/app/reactor_app.dart` (toast on `ConnState` transitions, dim cards when `degraded`/`offline`, manual Reconnect button)
- Test: `test/state/offline_ux_test.dart`

- [ ] **Step 1: Write a failing widget test** — drive a `ConnectionManager` to `offline`; assert the shell shows the offline badge + a Reconnect button; transition to `live` clears it.
- [ ] **Step 2: Run — expect FAIL.**
- [ ] **Step 3: Implement** the transition toasts + dimming + Reconnect.
- [ ] **Step 4: Run — expect PASS.**
- [ ] **Step 5: LIVE E2E (mandatory, browser):**
  1. Build the React plugin (`./gradlew shadowJar`), stand up a local test server with `web.toml` `enabled=true` (use the multiplexor `reacttest`-style instance on MC 26.2 — do **not** copy JARs into a real server plugins folder by hand; use the existing test harness).
  2. In console run `/react web pair label=dev`; copy the pairing code; note the console fingerprint.
  3. `dart run arcane_jaspr:serve` the app; open the browser; Add Server → paste code; verify fingerprint matches.
  4. Confirm Overview/Performance/Memory render and **the uPlot charts animate** as values update (poll cadence).
  5. Stop the server; confirm the app transitions to offline + Reconnect works when it returns.
  - Report what was seen (browser evidence), per the verification rules.

---

## Self-Review

- **Spec coverage (P0 slice):** identity + metrics snapshot + history endpoints, token auth + pairing command + audit, app shell, single-server connect, Overview/Performance/Memory, uPlot + SVG fallback, offline UX — all map to spec §3.1 / §3.3 / §7 (P0). WS push, control, operate, fleet, relay are correctly deferred to P1–P5.
- **Type consistency:** `SamplerDto` (Java) field names == `SamplerSample.fromJson` (Dart) keys: `id,name,value,suffix,min,max,history`. `PairingCode` encode (Java) format == decode (Dart) format. `ConnState`/`ServerScope` names consistent across Tasks 12/14/17/18.
- **Placeholders:** none — every step has concrete code or a concrete command + expected result. Tasks 1/4/5/6/7 include explicit "read the real source to bind exact accessors" steps because a few React internal signatures (`Graph.of`, `SampleController` registry accessor, controller lifecycle) must be verified against code rather than guessed.
- **Risk-first ordering:** charting interop (Task 13) and the auth/contract core (Tasks 2–6) land before the breadth of screens, so the riskiest unknowns are proven first.
