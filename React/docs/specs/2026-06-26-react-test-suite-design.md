# React Test Suite — Design Spec

Date: 2026-06-26
Status: Approved (design), pending implementation plan
Scope: React plugin only (not `react_remote` Flutter app)

## Goal

A comprehensive, rebuilt test suite for the React performance plugin that:

1. Replaces the existing 11-file JUnit4 suite (harvesting the strong cases).
2. Verifies **everything React does** — monitoring/samplers, maps/rendering, tools/commands/actions, optimization features, NMS bridge, settings/GUI, integrations — plus an error-finding pass.
3. Proves React can run with **~1000 players without being a major problem**, gated by hard SLOs and reported against a React-on-vs-off baseline.
4. Is exercised live across **Purpur, Spigot, and Paper** servers via the Minecraft Server multiplexor, one platform at a time, deleting and rebuilding the instance between platforms.

## Decisions (locked)

| Question | Decision |
|----------|----------|
| Scope | React plugin only. Three test layers + orchestration. |
| 1k-player load | Hybrid: synthetic server-side injection (authoritative, reaches 1000) + ~20 real mineflayer bots (protocol/login realism). |
| Platforms | Purpur, Spigot, Paper @ common MC **26.1.2** (only version cached for all three). Order: purpur -> spigot -> paper. |
| Pass bar | Hard SLO gate **and** React-on-vs-off baseline delta report. |
| Framework | Migrate JUnit4 -> **JUnit 5 (Jupiter) + Mockito + jqwik (PBT)**. |
| Load method | Synthetic React-path injection primary; NMS real fake-`ServerPlayer` is escalation only. |
| Reports | Machine-readable JSON written to files; verification reads files, never the console (Spigot console-spin trap). |
| Design doc | Written, **not** git-committed (user NEVER-COMMIT rule). |

## React subsystem map (test targets)

- **Bootstrap / Controller Registry** — `React.java`, `Registry`, classpath scan into controllers.
- **Async tick engine** — `Ticker` (50ms Looper over MultiBurst), `J` scheduler (Bukkit/Folia region+entity+global), `TickedObject`, `JobController` (main-thread budget executor).
- **Monitoring / Samplers (~75)** — `Sampler`, `ReactCachedSampler`/`ReactCachedRateSampler`/`ReactTickedSampler`, `SampleController`, `ObserverController` (per-chunk/world scores), `EventController` (reflective per-plugin handler timing).
- **Maps / Rendering** — `MapController` (filled-map dashboards, item-frame repair, megamap rebuild), `MegamapGrid` (tiling solver), HUD action-bar, `api/rendering`.
- **Optimization Features (~50)** — governors (random-tick, tracker-range, view/sim distance, AFK view-shedding), mob stacking, adaptive entity sleep / AI duty-cycle, item super-stack/backpressure, hopper/furnace/explosion/falling-block batching.
- **Tweaks (~20)** — fast fluids, hopper coalescing, etc.
- **Actions (~9)** — collect-garbage, prewarm/quarantine/purge chunks, hopper-network-normalize, trim/purge entities, incident playbook.
- **NMS bridge** — `bridge-api` (NmsBridge, tick hooks, explosion hooks, TickDecision), `nms/v26_2_R1` (Mojang-mapped ByteBuddy advice), `NmsBridgeRegistry`, `NmsBridges`, `MappingsLoader`.
- **Settings / GUI** — per-component TOML + hotload, categorized GUI, Off/Light/Balanced/High presets.
- **Integrations** — `IntegrationController` (Iris/Adapt/Wormholes metrics via VolmLib pipeline).
- **Support controllers** — `NearbyPlayerIndexController`, `PdcWriteBatcher`, `HotloadController`, `PlayerController`, `EntityController`, `JobController`, `ConfigInputController`.

## Known 1k-player risk hotspots (load test must target each)

1. `MapController.onTick`/`refreshLoadedItemFrames` — scales players x loaded chunks.
2. `FeatureAfkViewShedding.applyPressureCaps` — reflective getSendViewDistance/setSendViewDistance per player per cycle; engages under stress.
3. `PlayerMoveEvent` fan-out — NearbyPlayerIndex + AfkViewShedding markActive, tens of thousands/sec at 1k.
4. `EntityController.onTick` — `world.getEntities()` full defensive copy per world per cycle.
5. `FeatureAdaptiveEntitySleep` — per-mob PDC set/remove + nearby-player queries + setAware reflection.
6. NMS tick hooks (Hopper/Furnace/Brewing/FallingBlock/Explosion) — inside vanilla hottest loops.
7. `J.sResult`/`J.runRegionResult` — block burst threads on latch/sleep-poll up to 5-15s.
8. `PdcWriteBatcher.flushAll` — every tick, per-chunk region dispatch on Folia.
9. `EventController` — reflective RegisteredListener swap wraps every server-wide event in nanoTime while a metric is viewed.
10. `IntegrationController.onTick` — per-second reflective provider metric sampling on main thread.
11. `Ticker.tick` — per-ticked-object lambda + slowTickKey String allocation every tick.

## Existing tests — trash & harvest

Port forward (strong): `HopperPositionIndexTest`, `HopperItemIndexTest`, `MegamapGridTest`, `NmsBridgeRegistryTest`, `BridgeHealthReportTest`, `NmsBridgesTest`, `MappingsLoaderTest`, `DirectorCommandControllerLegacyParityTest`.

Replace with real behavior tests: `TweakHopperIndexTest`, `TweakFastFluidsParityTest` (descriptor-shape only today). Drop/absorb: `NMSTest` (trivial).

Existing in-game self-test to extend: `/react dev verify` and `/react dev test-all` (`CommandDev.java`).
Existing load harness to reuse: `/Users/brianfopiano/Developer/react-sim/` (mineflayer sim.js/run.sh/smoke.js, MC 1.21.11 protocol cap).

## Layer 1 — JUnit code tests (`./gradlew test`, server-free)

- Framework: JUnit 5 Jupiter, Mockito (+ mockito-inline if needed for static/final Bukkit seams), jqwik for PBT.
- build.gradle: replace `junit:junit:4.13.2` test deps with `org.junit.jupiter:junit-jupiter`, `org.mockito:mockito-core`, `net.jqwik:jqwik`; add `test { useJUnitPlatform() }`.
- Reorganize `src/test/java` to mirror main packages.
- Coverage additions (pure logic / extracted decision functions / Bukkit-mocked seams):
  - Samplers: cached-sampler memoization + rate math, NaN/Inf/negative guards, representative derived samplers.
  - Features: governor `load -> decision` functions (random-tick, tracker-range, view/sim distance, AFK pressure curve), mob-stack merge predicate, adaptive-sleep duty-cycle decision, item-backpressure thresholds.
  - `MeteredCache`: TTL, eviction, hit/miss accounting.
  - Config: TOML round-trip regression (silent-overwrite bug, world-keyed maps survive).
  - Tweaks: runtime decisions (hopper coalescing, fast-fluid accel) — not just descriptor shape.
  - NMS: hook install/uninstall accounting via fake bridge; TickDecision contract.
  - Commands: CommandReact/CommandDev tree + arg parsing.
  - GUI: preset -> settings mapping (Off/Light/Balanced/High).
- PBT (jqwik) invariants: megamap solver, packed-long indices, version parser, config round-trip.

## Layer 2 — In-game functional self-test

- New command `/react test run [--full] [--json]` (extends, does not duplicate, `/react dev verify`).
- Writes report to `plugins/React/test-reports/<timestamp>-<platform>.json` + human summary; PASS/FAIL/WARN/SKIP per check.
- Checks:
  - Monitoring: all ~75 samplers finite + in range; rate samplers advance; integration samplers present-or-cleanly-absent.
  - Maps/rendering: dashboard map renders non-blank + stable palette; megamap tiling solve on synthetic wall; HUD action-bar.
  - Features: each Feature engages -> disengages -> restores baseline (generalized "no persistent state left changed after disable"); mob-stack spawn+merge; adaptive sleep doze/undoze; item backpressure.
  - Actions: each Action runs without exception, reports sane deltas.
  - NMS bridge: health snapshot; per-hook live behavior (sand fall, hopper coalesce, furnace, explosion suppress); install/uninstall accounting.
  - Settings/GUI: apply each preset -> persist -> round-trip -> restore.
  - Error finding: scan server log + React incident/slow-tick recorders -> zero React-attributable exceptions / "Tick task crashed" / React-caused slow tick.
  - Platform + bridge-bind state reported.

## Layer 3 — Load/stress harness (hybrid, hard SLO gate)

Primary — synthetic `/react test loadtest --players <n> --duration <s> [--profile <name>]`:
- Inject N synthetic player records into React player-scaling paths (NearbyPlayerIndex, synthetic PlayerMove fan-out, view-shedding/governors driven by simulated count) — stress real scaling code without N sockets.
- Plus real world load: mob herds, hopper networks, falling blocks, TNT, redstone, item floods — calibrated 1k-equivalent profile hitting every hotspot.
- Two passes: React-on and React-off (features disabled) for baseline delta.
- Sample MSPT/TPS/heap/GC/per-feature timing each tick; write time-series + summary JSON.
- Hard SLO gate (fail run on breach): sustained >=18 TPS (avg MSPT < 50ms) over steady window; no OOM; bounded heap (no monotonic growth past threshold over duration); no main-thread freeze > 1s; zero React-path exceptions.
- Escalation only: NMS real fake-`ServerPlayer` spawn if synthetic path proves insufficient.

Secondary — real bot smoke:
- Reuse `/Users/brianfopiano/Developer/react-sim/` (~20 bots) for login/protocol/network path.
- Protocol caveat: mineflayer caps at 1.21.11; if 26.1.2 rejects it, run bots against a transient side-instance or skip with a logged note. Synthetic injection remains authoritative for 1k.

## Layer 4 — Platform cycle orchestration (multiplexor)

Driver script (bash, in scratchpad/react-sim). For each platform in [purpur, spigot, paper] @ MC 26.1.2:

1. `./start.sh consumer use plugin`
2. `./start.sh server create reacttest --type <platform> --mc 26.1.2 --isolated`
3. Copy fresh `React.jar` into the instance `plugins/` (isolated = React-only signal; jar self-contained).
4. `./start.sh runtime start reacttest --no-console`
5. Poll the instance log file for the "Done" ready marker (not console).
6. Drive `/react test run --full --json` then `/react test loadtest --players 1000 --duration 600 ...` (+ optional bot smoke) via `tmux send-keys`.
7. Poll for and copy out the JSON report files to a results dir.
8. `./start.sh runtime stop reacttest` then `./start.sh instance delete reacttest`.
9. Next platform (same name, fresh).
10. Aggregate all platform reports into a cross-platform comparison (SLO pass/fail + baseline deltas per subsystem per platform).

Runs autonomously; file-based verification throughout; `--no-console` + log-file polling to avoid the Spigot console busy-spin freeze.

## Build sequencing

1. Layer 1 JUnit suite green (`./gradlew test`).
2. Layer 2 + Layer 3 in-game commands + harness in main source, gated inert until invoked; `buildPsychoLT` deploys.
3. Layer 4 orchestration script.
4. Run the platform cycle; aggregate results.

## Build / deploy facts

- Build: `./gradlew shadowJar` (or `build`); Java 25+ required. Output `build/libs/React-2.0.0-26.2.jar` (fat jar).
- Deploy: `./gradlew buildPsychoLT` copies to `…/[Minecraft Server]/consumers/plugin-consumers/dropins/plugins/React.jar`.
- One jar runs all platforms; platform adaptation is runtime (Folia detect, NMS reflective bind, graceful measurement-only degrade off-version).
- VolmLib + NMS `v26_2_R1` shaded/relocated into the jar; slim() deps runtime-downloaded.

## Risks / caveats

- React NMS targets 26.2; on 26.1.2 servers the bridge may run degraded (measurement-only). Layer 2 detects & reports per platform — this is expected, not a failure.
- mineflayer protocol cap (1.21.11) vs 26.1.2 servers — bot smoke is best-effort; synthetic load is authoritative.
- Synthetic player injection must be strictly gated so it never runs except on explicit command (no production overhead).
- Folia/Canvas not in the live cycle (region-threading not live-exercised); Layer 2 still handles detection; can add later.
- Spigot console busy-spins under JDK25 via multiplexor; never depend on console output for pass/fail — read written report files.
