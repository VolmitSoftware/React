# React Test Suite Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a comprehensive, rebuilt test suite for the React plugin (server-free JUnit5 tests + an in-game functional self-test + a hybrid 1k-player load harness) and run it live across Purpur/Spigot/Paper via the Minecraft Server multiplexor.

**Architecture:** Four layers. Layer 1 = JUnit5+Mockito+jqwik unit/PBT tests (`./gradlew test`). Layer 2 = a `/react test run` in-game command that exercises every subsystem and writes a JSON report. Layer 3 = a `/react test loadtest` command that injects synthetic 1k-player load + world load, runs React-on/off passes, and evaluates a hard SLO gate into a JSON report; plus a ~20-bot mineflayer smoke. Layer 4 = a bash driver that cycles the `reacttest` instance across platforms, drives the commands over tmux, collects the JSON reports, and aggregates them.

**Tech Stack:** Java 25, Gradle (shadow + slimjar), JUnit 5 Jupiter, Mockito, jqwik, Bukkit/Paper API, React's Director command system, the `[Minecraft Server]` multiplexor (`./start.sh`), mineflayer (Node, in `/Users/brianfopiano/Developer/react-sim`).

## Global Constraints

- Build/run on Java 25+ only (gradle hard-exits otherwise). Build: `./gradlew shadowJar`; deploy: `./gradlew buildPsychoLT`. Run all gradle from `/Users/brianfopiano/Developer/RemoteGit/VolmitSoftware/React/React`.
- **NEVER run any git write command** (no add/commit/push). Tasks end at a "verify green" checkpoint, not a commit. The user commits.
- Java style (AGENTS.md): no `var`, explicit types everywhere, no wildcard imports, switch expressions over statements, no `@Deprecated`, no backward-compat shims.
- **Do not add code comments** (inline/block/Javadoc) unless explicitly requested. Test method names carry the intent.
- Reusable cross-plugin systems would go in VolmLib; everything here is React-test-specific and stays in React.
- Update `/Users/brianfopiano/Developer/RemoteGit/VolmitSoftware/MasterChangelog.MD` React section for the operator-visible `/react test` commands (dedupe; update in place; no date headers).
- In-game test/load commands are dev-gated (same posture as `CommandDev`, `DirectorOrigin`), and the synthetic load injector must be strictly inert unless explicitly invoked — zero production tick overhead.
- Reports are the source of truth. Read JSON report files, never the Spigot console (it busy-spins under JDK25 in the multiplexor).
- React API seams (verified): `React.controller(Class)`, `React.feature(Class)`, `React.sampler(String id)`, `React.bridgeRegistry().snapshotHealth()`, `React.reportError(Throwable)`. `FeatureController.getFeatures():Registry<Feature>`, `.getActiveFeatures():Map<String,?>`, `.activateFeature(Feature)`, `.deactivateFeature(Feature)`. `Feature.isEnabled()`, `.getId()`. `Sampler.sample():double`, `.format(double):String`. `Registry.get(String)`, `.all()`, `.size()`. Command pattern: `@Director(name,aliases,origin,sync,description)` + `@Param`, with `sender():VolmitSender` and `player():Player`. Delayed sync: `J.s(Runnable, longTicks)`.

---

## Phase 0 — Foundations

### Task 0.1: Migrate test framework to JUnit5 + Mockito + jqwik

**Files:**
- Modify: `React/React/build.gradle` (dependencies block lines ~117-170; add a `test` task config)

**Interfaces:**
- Produces: a `./gradlew test` that runs on the JUnit Platform with Mockito + jqwik available.

- [ ] **Step 1:** In `build.gradle` dependencies, remove `testImplementation('junit:junit:4.13.2')`. Add:
```groovy
    testImplementation('org.junit.jupiter:junit-jupiter:5.11.4')
    testRuntimeOnly('org.junit.platform:junit-platform-launcher:1.11.4')
    testImplementation('org.mockito:mockito-core:5.14.2')
    testImplementation('net.jqwik:jqwik:1.9.1')
```
(Keep `testImplementation('org.spigotmc:spigot-api:26.2-R0.1-SNAPSHOT')` and `testImplementation('it.unimi.dsi:fastutil:8.5.8')`.)
- [ ] **Step 2:** Add, after the `dependencies` block:
```groovy
tasks.named('test', Test).configure {
    useJUnitPlatform {
        includeEngines 'junit-jupiter', 'jqwik'
    }
    testLogging { events 'passed', 'skipped', 'failed' }
}
```
- [ ] **Step 3:** Run `./gradlew help` to confirm the build still configures (no compile yet). Expected: BUILD SUCCESSFUL.

### Task 0.2: Delete the old test tree, establish the new layout

**Files:**
- Delete: all 11 files under `React/React/src/test/java/...` (NMSTest, NmsBridgesTest, NmsBridgeRegistryTest, BridgeHealthReportTest, MappingsLoaderTest, DirectorCommandControllerLegacyParityTest, HopperPositionIndexTest, HopperItemIndexTest, TweakHopperIndexTest, TweakFastFluidsParityTest, MegamapGridTest)
- Create: `React/React/src/test/java/art/arcane/react/testutil/Fakes.java` (shared Bukkit test doubles)

**Interfaces:**
- Produces: `Fakes` — static factory for mocked `Server`/`World`/`Player`/`Chunk` and a `Fakes.bukkitStatic()` helper that mocks `org.bukkit.Bukkit` via `Mockito.mockStatic` returning a `MockedStatic` the caller closes.

- [ ] **Step 1:** Delete the 11 old test files.
- [ ] **Step 2:** Write `Fakes` with strongly-typed factory methods:
```java
public final class Fakes {
  private Fakes() {}
  public static World world(String name) {
    World world = Mockito.mock(World.class);
    Mockito.when(world.getName()).thenReturn(name);
    Mockito.when(world.getUID()).thenReturn(UUID.randomUUID());
    return world;
  }
  public static Player player(String name, World world) {
    Player player = Mockito.mock(Player.class);
    Mockito.when(player.getName()).thenReturn(name);
    Mockito.when(player.getWorld()).thenReturn(world);
    Mockito.when(player.getUniqueId()).thenReturn(UUID.randomUUID());
    return player;
  }
}
```
- [ ] **Step 3:** Run `./gradlew compileTestJava`. Expected: BUILD SUCCESSFUL (empty/near-empty test tree compiles).

---

## Phase 1 — Layer 1: JUnit code tests

Each task: write tests (RED), run to confirm fail/compile-error, the production code already exists so most tests should reach GREEN by reading the real class; where a test reveals a real bug, fix the bug in production and note it. All tasks end by running the named tests green.

### Task 1.1: Port the strong existing tests onto JUnit5

**Files:**
- Create: `src/test/java/.../core/controller/HopperPositionIndexTest.java`, `HopperItemIndexTest.java`, `api/rendering/MegamapGridTest.java`, `core/bridge/NmsBridgeRegistryTest.java`, `BridgeHealthReportTest.java`, `nms/NmsBridgesTest.java`, `core/bridge/MappingsLoaderTest.java`, `core/controller/DirectorCommandControllerLegacyParityTest.java`

**Interfaces:**
- Consumes: production classes unchanged.
- Produces: green ported tests (the reference patterns later tasks copy).

- [ ] **Step 1:** Recreate each test's cases from the originals (recovered from git history `git show HEAD:src/test/...` is read-only and allowed) translated to JUnit5: `org.junit.jupiter.api.Test`, `Assertions.assertEquals/assertTrue/assertThrows`, `@BeforeEach`. Keep the 4-thread concurrency cases in the two hopper index tests and all 21 megamap cases.
- [ ] **Step 2:** Convert PBT-amenable invariants to jqwik `@Property` where natural (megamap solver rejects holes; packed-long index round-trips coords). Keep example-based tests too.
- [ ] **Step 3:** Run `./gradlew test --tests "*HopperPositionIndexTest" --tests "*HopperItemIndexTest" --tests "*MegamapGridTest" --tests "*NmsBridgeRegistryTest" --tests "*BridgeHealthReportTest" --tests "*NmsBridgesTest" --tests "*MappingsLoaderTest" --tests "*DirectorCommandControllerLegacyParityTest"`. Expected: all pass.

### Task 1.2: Sampler memoization + rate math + value-domain tests

**Files:**
- Create: `src/test/java/.../api/sampler/ReactCachedSamplerTest.java`, `ReactCachedRateSamplerTest.java`, `SamplerDomainTest.java`
- Read first: `src/main/java/.../api/sampler/Sampler.java`, `ReactCachedSampler.java`, `ReactCachedRateSampler.java`, `ReactTickedSampler.java`

**Interfaces:**
- Produces: green tests; pattern: subclass the abstract cached sampler with a counting supplier, assert one underlying read per cache window, assert rate = delta/interval, assert NaN/Inf/negative never escape `sample()`.

- [ ] **Step 1 (RED):**
```java
@Test
void cachedSampler_readsUnderlyingOncePerWindow() {
  AtomicInteger reads = new AtomicInteger();
  ReactCachedSampler s = newCounting(reads, 50L);
  s.sample(); s.sample();
  Assertions.assertEquals(1, reads.get());
}
```
- [ ] **Step 2:** Run the test, confirm it compiles+fails or passes against the real class; if the real API differs, adjust the test to the real constructor/method names found in Step "read first".
- [ ] **Step 3:** Add `SamplerDomainTest`: a parameterized test over a representative list of derived sampler IDs asserting `sample()` is finite and not negative for a neutral fake server. Mock Bukkit via `Mockito.mockStatic(Bukkit.class)`.
- [ ] **Step 4:** Run `./gradlew test --tests "*Sampler*Test"`. Expected: pass.

### Task 1.3: Feature governor decision-logic tests

**Files:**
- Create: `src/test/java/.../content/feature/GovernorDecisionTest.java`, `MobStackMergeTest.java`, `AdaptiveSleepDecisionTest.java`, `ItemBackpressureTest.java`
- Read first: `content/feature/FeatureAfkViewShedding.java`, the random-tick/tracker-range/view-distance governor features, `FeatureAdaptiveEntitySleep.java`, the mob-stacking and item-backpressure features.

**Interfaces:**
- Produces: tests over the pure `load -> decision` functions. Where decision logic is entangled with Bukkit, extract a package-private static pure method `decide(double pressure, Config cfg)` in the feature (a refactor that does not change behavior) and test that. Note each extraction in the MasterChangelog only if behavior-visible (it is not — skip).

- [ ] **Step 1 (RED):** For the AFK view-shedding pressure curve:
```java
@Test
void afkPressure_engagesAboveThresholdAndRestoresBelow() {
  Assertions.assertTrue(FeatureAfkViewShedding.shouldEngage(0.95D, 0.80D));
  Assertions.assertFalse(FeatureAfkViewShedding.shouldEngage(0.50D, 0.80D));
}
```
- [ ] **Step 2:** If `shouldEngage` doesn't exist, extract it from `applyPressureCaps()` as a pure static method and call it from the original site (no behavior change). Run, expect pass.
- [ ] **Step 3:** Repeat the extract+test pattern for: random-tick governor (load -> tick-speed multiplier monotonic + clamped), tracker-range governor (load -> range, clamped to configured min/max), view/sim-distance governor, mob-stack merge predicate (same type + age window + radius => merge), adaptive-sleep duty-cycle (no nearby player + cycle phase => doze), item-backpressure (queue depth >= threshold => throttle).
- [ ] **Step 4:** Run `./gradlew test --tests "*Governor*" --tests "*MobStack*" --tests "*AdaptiveSleep*" --tests "*ItemBackpressure*"`. Expected: pass.

### Task 1.4: MeteredCache + config TOML round-trip + tweak runtime

**Files:**
- Create: `src/test/java/.../engine/framework/MeteredCacheTest.java`, `src/test/java/.../core/config/ConfigRoundTripTest.java`, `src/test/java/.../content/tweak/TweakRuntimeTest.java`
- Read first: `engine/framework/MeteredCache.java`, the TOML config codec (VolmLib `TomlCodec`/React config classes), `content/tweak/TweakFastFluids.java`, `TweakHopperIndex.java`.

**Interfaces:**
- Produces: `MeteredCacheTest` (hit/miss accounting, TTL expiry, eviction bound); `ConfigRoundTripTest` (serialize -> deserialize -> equals, including a world-keyed `Map` field — the silent-overwrite regression guard); `TweakRuntimeTest` (fast-fluid accel flags affect the decision; hopper-coalescing decision), not just descriptor shape.

- [ ] **Step 1 (RED):** MeteredCache:
```java
@Test
void cache_countsHitsAndMisses() {
  MeteredCache<String,Integer> c = new MeteredCache<>(10, 1000L);
  c.get("a", k -> 1);
  c.get("a", k -> 1);
  Assertions.assertEquals(1, c.misses());
  Assertions.assertEquals(1, c.hits());
}
```
- [ ] **Step 2:** Run, reconcile with the real `MeteredCache` API.
- [ ] **Step 3 (RED):** Config round-trip — build a React settings object with a `Map<String,Boolean>` world-keyed field set to two entries, serialize to TOML string, parse back, assert equal map. This reproduces the prior silent-overwrite bug; if it fails, fix the codec usage in production.
- [ ] **Step 4:** Run `./gradlew test --tests "*MeteredCacheTest" --tests "*ConfigRoundTripTest" --tests "*TweakRuntimeTest"`. Expected: pass.

### Task 1.5: NMS hook accounting + command/GUI mapping

**Files:**
- Create: `src/test/java/.../core/bridge/HookInstallAccountingTest.java`, `src/test/java/.../content/directorcommand/CommandTreeTest.java`, `src/test/java/.../core/gui/PresetMappingTest.java`
- Read first: bridge install/uninstall in the NMS bridge registry + `FeatureLazyGravity.reset()`, `content/command/CommandReact.java`, `core/gui/` preset classes.

**Interfaces:**
- Produces: a fake `NmsBridge` whose hook install/uninstall increments counters; assert install N hooks then `reset()` uninstalls all N (the reload-leak guard). Command tree test asserts `/react test` and subcommands resolve. Preset test asserts Off/Light/Balanced/High map to the documented enabled-feature sets.

- [ ] **Step 1 (RED):** Hook accounting with a fake bridge; assert `installedCount()==0` after `reset()`.
- [ ] **Step 2:** Command tree — build the Director runtime (pattern from the ported `DirectorCommandControllerLegacyParityTest`) and assert the `test`/`run`/`loadtest` paths resolve once Phase 2/3 add them (this task's command assertions are added after Phase 2/3 land; stub now with `@Disabled` and enable in Task 3.6).
- [ ] **Step 3:** Preset mapping test against the real preset enum/config.
- [ ] **Step 4:** Run `./gradlew test --tests "*HookInstallAccountingTest" --tests "*PresetMappingTest"`. Expected: pass.

### Task 1.6: Full Layer-1 green gate

- [ ] **Step 1:** Run `./gradlew test`. Expected: BUILD SUCCESSFUL, 0 failures. Record the test count.
- [ ] **Step 2:** If any test fails, fix per systematic-debugging (root cause; if it's a real production bug, fix it and note in MasterChangelog).

---

## Phase 2 — Layer 2: in-game functional self-test

### Task 2.1: Report model + writer

**Files:**
- Create: `src/main/java/.../api/test/TestReport.java`, `TestCheck.java`, `TestStatus.java`, `TestReportWriter.java`
- Read first: how React resolves its data folder (`React.instance().getDataFolder()` pattern in `React.java`).

**Interfaces:**
- Produces:
  - `enum TestStatus { PASS, FAIL, WARN, SKIP, INFO }`
  - `record TestCheck(String subsystem, String name, TestStatus status, String detail, Map<String,Object> data)`
  - `class TestReport { void add(TestCheck c); List<TestCheck> checks(); boolean passed(); /* false if any FAIL */ String platform(); String mcVersion(); long startedAtMillis(); }`
  - `class TestReportWriter { static Path write(TestReport r, String kind) }` -> writes `plugins/React/test-reports/<startedAt>-<platform>-<kind>.json` and returns the path; JSON shape: `{ "kind", "platform", "mcVersion", "foliaThreading", "bridgeAvailable", "bridgeUnavailable", "startedAt", "durationMs", "passed", "counts": {pass,fail,warn,skip}, "checks": [ {subsystem,name,status,detail,data} ] }`. Use the JSON writer already on the classpath (gson is compileOnly; prefer VolmLib JSON util or hand-roll a minimal writer to avoid a runtime gson dep — confirm gson availability at runtime first; if absent, hand-roll).

- [ ] **Step 1:** Implement the four classes with explicit types.
- [ ] **Step 2:** Add a tiny JUnit5 test `api/test/TestReportWriterTest.java` writing to a JUnit `@TempDir`, asserting the JSON parses back with the right counts and `passed` reflecting a seeded FAIL.
- [ ] **Step 3:** Run `./gradlew test --tests "*TestReportWriterTest"`. Expected: pass.

### Task 2.2: Test runner framework + subsystem check interface

**Files:**
- Create: `src/main/java/.../api/test/ReactTestRunner.java`, `ReactSubsystemCheck.java`

**Interfaces:**
- Consumes: `TestReport`.
- Produces:
  - `interface ReactSubsystemCheck { String subsystem(); void run(TestReport report); }` (synchronous checks)
  - `interface ReactAsyncSubsystemCheck { String subsystem(); void run(TestReport report, Runnable onDone); }` (live checks needing tick delays, e.g. the sand-fall)
  - `class ReactTestRunner { ReactTestRunner add(ReactSubsystemCheck); ReactTestRunner addAsync(ReactAsyncSubsystemCheck); void run(VolmitSender out, boolean full, Consumer<TestReport> onComplete); }` — runs sync checks, then chains async checks, wrapping each in try/catch so one failing check records a FAIL and never aborts the run; reports to `out` live and to the `TestReport`.

- [ ] **Step 1:** Implement the runner with strict try/catch-per-check (a thrown check => `TestStatus.FAIL` with the throwable class+message, then continue). Catch `Throwable`, call `React.reportError`.
- [ ] **Step 2:** Unit-test the runner with two fake checks (one throws) asserting both are recorded and the run completes. `src/test/java/.../api/test/ReactTestRunnerTest.java`.
- [ ] **Step 3:** Run `./gradlew test --tests "*ReactTestRunnerTest"`. Expected: pass.

### Task 2.3: Monitoring + maps/rendering checks

**Files:**
- Create: `src/main/java/.../api/test/checks/MonitoringCheck.java`, `MapsRenderingCheck.java`
- Read first: `SampleController.getSamplers()`, `MapController` render entry points, `MegamapGrid.solve`.

**Interfaces:**
- Produces: `MonitoringCheck` asserts every registered sampler returns finite, non-negative (or documented-signed) values; rate samplers advance across two reads 1 tick apart; integration samplers either present or cleanly absent. `MapsRenderingCheck` renders a dashboard map to a byte buffer and asserts non-blank + stable palette across two renders, and runs `MegamapGrid.solve` on a synthetic 2x2 wall.

- [ ] **Step 1:** Implement both checks reporting one `TestCheck` per logical assertion (subsystem `"monitoring"`, `"maps"`).
- [ ] **Step 2:** No new unit test (these are live checks); compile via `./gradlew compileJava`. Expected: SUCCESS.

### Task 2.4: Features + actions checks (engage/restore + run-clean)

**Files:**
- Create: `src/main/java/.../api/test/checks/FeatureLifecycleCheck.java`, `ActionSuiteCheck.java`
- Read first: `CommandDev.verifyWorldSave` (the activate/deactivate + baseline-restore pattern to generalize), `CommandDev.buildTestSteps`/`queueStep` (the action-suite pattern to reuse).

**Interfaces:**
- Produces:
  - `FeatureLifecycleCheck` (async): for every enabled `Feature`, capture a baseline snapshot of observable world/server state it touches (autosave flags, view distances, random-tick speed via Bukkit getters), `deactivateFeature` -> `activateFeature` -> `deactivateFeature`, then assert the observable state returns to baseline (generalized data-loss guard). Record PASS/FAIL/WARN per feature. Features with no observable Bukkit-visible state are recorded INFO ("no observable state").
  - `ActionSuiteCheck` (async): reuse the `TEST_ACTION_ORDER` + `prepareParams` logic from `CommandDev` (extract it to a shared helper `ActionTestSupport` so both `CommandDev` and this check use it — DRY), run each action forcefully, assert completion without exception and a sane (non-null) completed message; record per-action.

- [ ] **Step 1:** Extract `ActionTestSupport` (move `TEST_ACTION_ORDER`, `prepareParams`, `collectLoadedChunks`, step building from `CommandDev` into `api/test/ActionTestSupport.java`; update `CommandDev` to delegate). Run `./gradlew compileJava`.
- [ ] **Step 2:** Implement both checks.
- [ ] **Step 3:** `./gradlew compileJava`. Expected: SUCCESS.

### Task 2.5: NMS bridge + error-finding checks

**Files:**
- Create: `src/main/java/.../api/test/checks/BridgeCheck.java`, `ErrorScanCheck.java`
- Read first: `React.bridgeRegistry().snapshotHealth()`, `FeatureLazyGravity` sand-fall (generalize), React's incident/slow-tick recorders (`ObserverController`, any incident store), `React.reportError` sink.

**Interfaces:**
- Produces: `BridgeCheck` reports health snapshot (PASS if 0 unavailable on a matching MC, WARN/INFO if degraded off-version — degraded is expected on 26.1.2, recorded WARN not FAIL), and runs per-hook live behavior where safe (sand-fall landing within 5s). `ErrorScanCheck` reads React's internal error/incident counters captured during the run window and the server `logs/latest.log` tail for React-attributable `ERROR`/`Tick task crashed`/`slow tick`, recording FAIL if any are React-caused, INFO otherwise. Counts only deltas observed since runner start.

- [ ] **Step 1:** Implement both. The off-version bridge degrade must be WARN (expected), real hook-install failure on a matching version is FAIL.
- [ ] **Step 2:** `./gradlew compileJava`. Expected: SUCCESS.

### Task 2.6: The `/react test run` command

**Files:**
- Create: `src/main/java/.../content/directorcommand/CommandTest.java`
- Modify: register `CommandTest` under the `/react` tree (read `content/command/CommandReact.java` to see how `CommandDev` is wired and mirror it)
- Modify: `MasterChangelog.MD` React section

**Interfaces:**
- Consumes: `ReactTestRunner`, all checks from 2.3-2.5, `TestReportWriter`.
- Produces: `@Director(name="test", origin=DirectorOrigin.BOTH, description=...)` containing `@Director(name="run", aliases={"r"}, sync=true)` `run(@Param full, @Param json)` which builds the runner with all checks, runs it, writes the report, and prints the path + summary line `React test: PASS|FAIL p/f/w/s -> <reportPath>`.

- [ ] **Step 1:** Implement `CommandTest.run`, wiring every check. Default `full=true`, `json=true`.
- [ ] **Step 2:** Wire it into the command tree next to `dev`.
- [ ] **Step 3:** Add a MasterChangelog React entry: "Added `/react test run` in-game self-test (writes JSON report)."
- [ ] **Step 4:** `./gradlew shadowJar`. Expected: SUCCESS. (Live verification happens in Phase 4.)

---

## Phase 3 — Layer 3: load/stress harness

### Task 3.1: Synthetic player-load injector

**Files:**
- Create: `src/main/java/.../api/test/load/SyntheticPlayerLoad.java`, `SyntheticPlayerRecord.java`
- Read first: `NearbyPlayerIndexController` (how player records/positions are indexed + its `PlayerMoveEvent` handler), `FeatureAfkViewShedding` markActive path.

**Interfaces:**
- Produces:
  - `record SyntheticPlayerRecord(UUID id, String name, Location position)`
  - `class SyntheticPlayerLoad { void begin(int count, World world, long radius); void tickMovement(); void end(); int active(); }` — generates `count` synthetic records spread across `world`, and on each `tickMovement()` perturbs positions and drives React's player-scaling paths directly (feed the nearby-player index + fire the same internal update calls `PlayerMoveEvent` would, WITHOUT constructing fake Bukkit `Player` objects or real connections). Strictly inert until `begin` is called; `end()` fully clears injected state. Must touch only React-owned indices, never mutate vanilla player lists.

- [ ] **Step 1:** Implement. The injector calls into React's own controllers (e.g. an added package-private `NearbyPlayerIndexController.injectSynthetic(record)` / `clearSynthetic()`); add those injection hooks guarded so they no-op unless a synthetic session is active.
- [ ] **Step 2:** Unit-test `SyntheticPlayerLoadTest` (server-free with mocked World) asserting `active()==count` after begin and `0` after end, and that `tickMovement` mutates positions. `src/test/java/.../api/test/load/SyntheticPlayerLoadTest.java`.
- [ ] **Step 3:** Run `./gradlew test --tests "*SyntheticPlayerLoadTest"`. Expected: pass.

### Task 3.2: World-load generator

**Files:**
- Create: `src/main/java/.../api/test/load/WorldLoadGenerator.java`, `LoadProfile.java`

**Interfaces:**
- Produces:
  - `record LoadProfile(int mobHerds, int mobsPerHerd, int hopperNetworks, int fallingBlocks, int tntBursts, int itemFloodPerTick, int redstoneClocks)`; static `LoadProfile.forPlayers(int n)` scales each field to an n-player-equivalent profile.
  - `class WorldLoadGenerator { void begin(World world, Location center, LoadProfile p); void tick(); void end(); }` — spawns/maintains the configured load (mobs to exercise stacking/entity-controller/adaptive-sleep, hopper networks, falling blocks, TNT, item floods, redstone), and `end()` removes everything it spawned (tracked by UUID). Every spawn/removal goes through `J` schedulers (Folia-safe via region dispatch).

- [ ] **Step 1:** Implement with explicit spawned-entity tracking + full teardown.
- [ ] **Step 2:** `./gradlew compileJava`. Expected: SUCCESS.

### Task 3.3: Metrics recorder + SLO evaluator

**Files:**
- Create: `src/main/java/.../api/test/load/LoadRecorder.java`, `SloGate.java`, `SloResult.java`
- Read first: the TPS/MSPT/memory samplers to read each tick.

**Interfaces:**
- Produces:
  - `class LoadRecorder { void sampleTick(); TimeSeries series(); Summary summary(); }` — each `sampleTick()` reads MSPT, TPS, heap used, GC pause, React async/sync tick time, job backlog from React's samplers; accumulates min/avg/p95/p99/max + a downsampled time series.
  - `record SloResult(boolean passed, List<String> breaches, Map<String,Double> metrics)`
  - `class SloGate { static SloResult evaluate(LoadRecorder.Summary s, long durationMs) }` — fails if steady-window avg TPS < 18 (avg MSPT >= 50ms), any OOM flagged, heap monotonic growth beyond threshold over the window, any tick > 1000ms (freeze), or React-path exceptions > 0. Steady window = drop the first 15% warmup.

- [ ] **Step 1:** Implement recorder + gate with explicit types.
- [ ] **Step 2:** Unit-test `SloGateTest` with synthetic summaries: a healthy one passes; one with avg MSPT 80ms fails with a TPS breach; one with a 1500ms max-tick fails with a freeze breach; one with monotonic heap growth fails. `src/test/java/.../api/test/load/SloGateTest.java`.
- [ ] **Step 3:** Run `./gradlew test --tests "*SloGateTest"`. Expected: pass.

### Task 3.4: Load orchestrator (React-on/off passes)

**Files:**
- Create: `src/main/java/.../api/test/load/LoadTest.java`

**Interfaces:**
- Consumes: `SyntheticPlayerLoad`, `WorldLoadGenerator`, `LoadRecorder`, `SloGate`, `FeatureController` (to toggle all features off for the baseline pass), `TestReport`/`TestReportWriter`.
- Produces: `class LoadTest { void run(VolmitSender out, int players, int durationSeconds, World world, Consumer<TestReport> onComplete); }` — runs pass A (React features active) and pass B (all features deactivated) for `durationSeconds` each, records both, computes the delta (React-on minus React-off MSPT/TPS/heap), evaluates the SLO gate on pass A, writes a `loadtest` JSON report (passes + delta + SLO result + time series), and restores all features to their pre-test active set afterward (try/finally).

- [ ] **Step 1:** Implement with a tick driver (a `J`-scheduled repeating task at 1 tick) that advances movement + world load + recorder, ending each pass after `durationSeconds*20` ticks. Guarantee teardown + feature restore in a finally path even on exception.
- [ ] **Step 2:** `./gradlew compileJava`. Expected: SUCCESS.

### Task 3.5: The `/react test loadtest` command

**Files:**
- Create: append `loadtest` subcommand to `content/directorcommand/CommandTest.java`
- Modify: `MasterChangelog.MD`

**Interfaces:**
- Produces: `@Director(name="loadtest", aliases={"load"}, origin=DirectorOrigin.PLAYER, sync=true)` `loadtest(@Param players default 1000, @Param duration default 600, @Param profile default "default")` — runs in the player's world, requires op/dev gate + a typed confirmation arg `@Param confirm` (must be `true`) to prevent accidental invocation, calls `LoadTest.run`, prints the report path + SLO verdict.

- [ ] **Step 1:** Implement with the confirm gate (refuse + explain if `confirm != true`).
- [ ] **Step 2:** MasterChangelog: "Added `/react test loadtest` synthetic 1k-player load gate (JSON report, React-on/off baseline)."
- [ ] **Step 3:** Enable the previously-`@Disabled` `CommandTreeTest` assertions for `test run`/`test loadtest` (Task 1.5 Step 2). Run `./gradlew test --tests "*CommandTreeTest"`. Expected: pass.
- [ ] **Step 4:** `./gradlew shadowJar`. Expected: SUCCESS.

### Task 3.6: Mineflayer bot-smoke adapter

**Files:**
- Modify/Create under `/Users/brianfopiano/Developer/react-sim/`: `smoke-react.sh` (wraps `smoke.js` + a short `sim.js` burst), confirm `package.json` mineflayer present (`npm install` if node_modules missing).

**Interfaces:**
- Produces: `smoke-react.sh <host> <port> <bots> <seconds> <mc-version>` -> exits 0 if `bots` connect and stay online for `seconds`, non-zero otherwise; prints a one-line JSON `{"connected":N,"target":B,"ok":bool}` to stdout for the driver to capture. Protocol caveat: if the server rejects the 1.21.11 protocol, exit code 3 and JSON `{"ok":false,"reason":"protocol"}` (driver treats as a logged SKIP, not a failure).

- [ ] **Step 1:** Implement `smoke-react.sh` reusing `smoke.js` exit codes; emit the JSON line.
- [ ] **Step 2:** Local dry-run against a non-existent port to confirm it exits non-zero cleanly. Expected: non-zero, JSON `ok:false`.

---

## Phase 4 — Layer 4: platform-cycle orchestration + run

### Task 4.1: The orchestration driver script

**Files:**
- Create: `/Users/brianfopiano/Developer/RemoteGit/VolmitSoftware/React/React/scripts/reacttest-cycle.sh`
- Create: results dir under the session scratchpad (passed as `$RESULTS_DIR`).

**Interfaces:**
- Produces: `reacttest-cycle.sh [platforms...]` (default `purpur spigot paper`). For each platform it:
  1. `cd "[Minecraft Server]"; ./start.sh consumer use plugin`
  2. `./start.sh instance delete reacttest` (ignore-if-absent), then `./start.sh server create reacttest --type <p> --mc 26.1.2 --isolated`
  3. Copy the freshly built `React.jar` into the instance `plugins/` (resolve instance path via `./start.sh instance path reacttest`).
  4. `./start.sh runtime start reacttest --no-console`
  5. Poll the instance runtime log file (`consumers/plugin-consumers/state/runtime/reacttest.log`) for `Done (` / `For help, type` ready marker (timeout 180s).
  6. `tmux send-keys` to the `reacttest` session: `react test run --full --json`, wait for the report file to appear under the instance `plugins/React/test-reports/`, then `react test loadtest 1000 600 default true`, wait for its report (timeout = duration + 120s).
  7. Optionally run `smoke-react.sh 127.0.0.1 <port> 20 30 1.21.11`, capture JSON.
  8. Copy all `plugins/React/test-reports/*.json` to `$RESULTS_DIR/<platform>/`.
  9. `./start.sh runtime stop reacttest`; wait stopped; `./start.sh instance delete reacttest`.
- Resolve the tmux session name from the multiplexor (`consumer + instance`); read `runtime status` to confirm states. Verify everything via files, never console scrape.

- [ ] **Step 1:** Implement the script with `set -euo pipefail`, per-step logging to `$RESULTS_DIR/cycle.log`, and robust file-polling helpers (poll every 2s with timeouts).
- [ ] **Step 2:** Dry-run the script's first platform setup steps with an early `exit` after `runtime start` to confirm instance creation + jar copy + ready-marker detection work, then remove the early exit.

### Task 4.2: Build + deploy the final jar, then run the cycle

- [ ] **Step 1:** `./gradlew test` (full Layer-1 gate green) then `./gradlew buildPsychoLT` (builds + deploys `React.jar` to dropins; the cycle script copies from there or from `build/libs`).
- [ ] **Step 2:** Run `reacttest-cycle.sh purpur spigot paper`. Monitor `$RESULTS_DIR/cycle.log`.
- [ ] **Step 3:** For each platform, read the collected JSON reports (NOT console). Confirm `test run` passed (no FAILs except expected off-version bridge WARN) and `loadtest` SLO `passed:true`.

### Task 4.3: Aggregate + report

**Files:**
- Create: `$RESULTS_DIR/SUMMARY.md` (generated)

**Interfaces:**
- Produces: a cross-platform comparison: per platform, the `test run` pass/fail counts + the `loadtest` SLO verdict + React-on/off MSPT/TPS/heap deltas, plus a "1k-player verdict" per platform and overall.

- [ ] **Step 1:** Write a small `jq`/node aggregation (in `ctx_execute` or a script) that reads every `$RESULTS_DIR/<platform>/*.json` and emits `SUMMARY.md`.
- [ ] **Step 2:** Present the summary to the user with the per-platform 1k-player verdict and any SLO breaches or React-path errors found.

---

## Self-Review (coverage vs spec)

- Layer 1 (JUnit5/Mockito/jqwik, port strong + new coverage of samplers/features/cache/config/tweaks/NMS/commands/GUI): Tasks 0.1-1.6. Covered.
- Layer 2 (in-game `/react test run` over monitoring/maps/features/actions/NMS/settings/error-finding + JSON report): Tasks 2.1-2.6. Covered.
- Layer 3 (synthetic 1k injection + world load + React-on/off baseline + hard SLO gate + JSON report + bot smoke): Tasks 3.1-3.6. Covered.
- Layer 4 (purpur->spigot->paper @26.1.2 cycle, delete/rebuild each time, file-based verification, aggregate): Tasks 4.1-4.3. Covered.
- Constraints (no commits, Java style, no comments, dev-gating, report-file truth, MasterChangelog): in Global Constraints + Tasks 2.6/3.5. Covered.
- Open risk carried into execution: off-version NMS bridge on 26.1.2 (WARN not FAIL — encoded in Task 2.5); mineflayer protocol cap (SKIP not FAIL — Task 3.6).
