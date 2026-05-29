# Fast Fluids Smoke Test Checklist

Manual verification checklist for React's supported fluid acceleration path.

React does not replace vanilla fluid registry entries. Fluid acceleration is handled by the `fast-fluids` tweak, which queues bounded extra vanilla fluid ticks through runtime NMS bridge descriptors and fails passive if those bridge signatures are unavailable.

## Pre-conditions

1. Start the target server with React installed.
2. Leave `fast-fluids` enabled, or enable it in `plugins/React/tweak/fast-fluids.toml`.
3. Use defaults unless the scenario specifies otherwise:
   - `accelerateWater = true`
   - `accelerateLava = true`
   - `accelerateDrain = true`
   - `extraVanillaTicksPerEvent = 2`
   - `maxExtraVanillaTicksPerServerTick = 256`
   - `maxBurstTicksPerLocationPerServerTick = 16`

## Startup Checks

| Check | Expected |
|-------|----------|
| Plugin load | No fluid registry install attempt appears in the log. |
| Removed registry path | No load-time fluid registry install attempt or registry replacement class names appear in the log. |
| Bridge status | `/react bridge status` lists the `TweakFastFluids` fluid bridge descriptors. |
| Passive fallback | If no compatible bridge resolves, React logs one passive-mode warning and server fluid behavior remains vanilla. |

## Water Spread

1. Place a water source at the top of a flat 32 by 32 test platform.
2. Confirm the spread completes faster than vanilla stepping while preserving normal block interactions.
3. Break the source block and confirm drain acceleration around the active fluid cells.
4. Confirm no chunk-load or region-thread warnings appear while the spread is active.

Expected result: water uses bounded extra vanilla ticks, keeps vanilla state transitions, and stays inside the configured per-tick budget.

## Lava Spread

1. Place a lava source on a small flat platform in a disposable test area.
2. Confirm lava spread and retract transitions are accelerated when `accelerateLava = true`.
3. Set `accelerateLava = false`, reload the tweak, and confirm lava returns to vanilla timing while water can remain accelerated.

Expected result: lava acceleration is available through `fast-fluids` without any registry replacement.

## Drain Behavior

1. Fill a shallow trench with water and lava in separate test areas.
2. Remove the source block for each fluid.
3. Confirm neighboring cells drain without repeated long tail propagation.
4. Set `accelerateDrain = false`, reload, and confirm only direct spread events are accelerated.

Expected result: drain acceleration is controlled by `accelerateDrain` and does not require a restart.

## Runtime Safety

| Check | Expected |
|-------|----------|
| Budget clamp | `extraVanillaTicksPerEvent` clamps to 0 through 4. |
| Server-tick cap | `maxExtraVanillaTicksPerServerTick` clamps to 16 through 4096. |
| Per-location cap | `maxBurstTicksPerLocationPerServerTick` clamps to 1 through 16. |
| Unloaded chunks | Queued pulses skip unloaded chunks instead of forcing loads. |
| Folia ownership | Pulses dispatch through location-owned scheduling before touching world state. |
| Bridge failure threshold | Repeated runtime bridge failures disable acceleration and keep vanilla fluid behavior. |

## Build Checks

Run from `React/React`:

```bash
./gradlew test
./gradlew shadowJar
```

Inspect the shaded jar:

```bash
jar tf build/libs/React-2.0.0.jar | grep 'art/arcane/react/nms/fluid'
```

Expected result: the grep command prints no matches.
