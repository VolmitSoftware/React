# NMS Bridges & Platform Notes

React has two separate NMS integration systems: a reflective descriptor registry and a compiled, version-tagged instrumentation bridge. Features fail passive or remain measurement-only when their required path is unavailable.

## Reflective descriptor registry

`NmsBridgeRegistry` resolves string-described methods, fields, and constructors into method or variable handles. Fast fluids, hopper indexing, pathfinder access, and several NMS accessors use this registry.

- `/react bridge status` reports only descriptor handles that have been resolved or attempted. It does not report all compiled instrumentation hooks.
- The registry tries candidate classes and parameter lists in order and caches the first available handle by logical id.
- A descriptor's current resolver does not validate `returnTypeName`, method staticness, or field staticness. Maintainers must verify those properties manually; adding a candidate or changing order is not automatically safe.
- `fast-fluids` requires its core descriptors plus at least one valid fluid-tick route. Individual alternative descriptors may remain unavailable without disabling the tweak.

## Compiled version bridge

The `bridge-api` module defines `art.arcane.react.nms.NmsBridge`; compiled implementations live under `nms/`. This tree ships `v26_2_R1`, selected for detected version strings `26.2`, `26.1.2`, and `1.21.11`.

The version bridge installs ByteBuddy redefinitions for eligible furnace, brewing-stand, falling-block, explosion, explosion-packet, and hopper paths when their owning features activate. These hooks support furnace/brewing batching, lazy gravity, explosion packet batching, and hopper-chain act mode. Unavailable or failed hooks leave those features passive or measurement-only as documented in the feature catalogs.

## Bytecode configuration and reporting

`unsafeBytecode = true` eagerly attaches the general `core.bridge.BytecodeAgent` during startup. The compiled version bridge manages a separate instrumentation reference and can attach it when an NMS-backed feature activates even when `unsafeBytecode = false`; neither attachment can be detached before JVM restart.

The bStats `bytecode_agent` chart checks only the general `BytecodeAgent`. It can therefore report `false` while compiled NMS transformations are active.

## Operator checks

- `/react bridge status` lists reflective descriptor status.
- `/react dev verify` checks for a compiled version bridge, reports the descriptor-registry snapshot, samples key metrics, and runs a live falling-block landing probe. The falling-block probe mutates the test world.
- Startup logs identify the compiled bridge class or the reason no matching implementation loaded.
- Run bridge verification on the exact server build targeted by the shaded React artifact. Paper and Folia use different scheduling ownership rules even when NMS signatures match.

## NMS jar workflow for maintainers

Run from `React/React` when a reflective descriptor stops resolving after an MC update.

### 1. Download inspection jars

```bash
./gradlew downloadNmsJars
./gradlew listNmsJars
```

Downloaded Paper, Folia, and Purpur jars are written to `build/nms-jars/`. The inspected versions come from `nmsVersions` in `nms-jars.gradle`; that list is a signature-research set, not a statement that one React artifact supports every listed runtime.

Paper jars are Mojang-remapped and are the primary signature reference. A Spigot jar must be produced separately with BuildTools and placed in the local inspection folder.

### 2. Inspect the real member

```bash
NMS_INSPECTION_JAR=build/nms-jars/paper-1.21.11.jar
javap -classpath "$NMS_INSPECTION_JAR" -p net.minecraft.world.level.Level | rg -i tickFluid
javap -classpath "$NMS_INSPECTION_JAR" -p net.minecraft.server.level.ServerLevel | rg -i tickFluid
javap -classpath "$NMS_INSPECTION_JAR" -p net.minecraft.world.level.material.FluidState | rg -i tick
```

`-p` includes private members. Record the declaring class, exact name, parameter list, return type, and staticness.

### 3. Update the descriptor

Add the verified class or parameter candidate to the owning descriptor, such as `TweakFastFluids.fluidBridgeDescriptors()`. Preserve ordering from newest/most specific to safe alternatives, and verify the chosen handle rather than assuming member-name resolution proves semantic compatibility.

For reference, current fluid research found:

- `ServerLevel.tickFluid(BlockPos, Fluid)` on the inspected 1.20.6–1.21.11 jars.
- `FluidState.tick(Level, BlockPos)` on 1.20.5–1.21.1 and `FluidState.tick(ServerLevel, BlockPos, BlockState)` on 1.21.4 and later inspected jars.
- `Fluid.tick`/`FlowingFluid.tick(Level, BlockPos, FluidState)` on 1.20.5–1.21.1 and the four-parameter `ServerLevel` form on 1.21.4 and later inspected jars.

These are inspection results, not runtime support guarantees. Pre-1.20.5 Spigot-mapped jars retain per-version obfuscated method names and cannot be handled by the current string-name descriptor set without additional mappings.

### 4. Verify

```bash
./gradlew test
./gradlew build
```

Install the resulting shaded jar only in an isolated target-version server, then check startup logs, `/react bridge status`, the affected feature path, reload, and shutdown. A green descriptor status proves that a member resolved; it does not prove the hook's gameplay behavior or Folia thread ownership.
