# NMS Jar Sifting Workflow

Use this when MC updates rename or move an NMS class, method, or field and a React bridge stops resolving.

---

## Step 1 — Download the server jars

```
cd React/React
./gradlew downloadNmsJars
```

Jars land in `build/nms-jars/`:
- `paper-<version>.jar`
- `folia-<version>.jar`
- `purpur-<version>.jar`

To change which versions get downloaded, edit `nmsVersions` at the top of `nms-jars.gradle`.

To see what versions are available: `./gradlew listNmsJars`

> Paper jars are Mojang-remapped — NMS class and method names are human-readable (e.g. `net.minecraft.world.level.Level`). Use Paper as the canonical reference.

---

## Step 2 — Find the real signature with javap

```bash
JAR=build/nms-jars/paper-1.21.11.jar

# Check if tickFluid still lives on Level (or moved to ServerLevel)
javap -classpath $JAR -p net.minecraft.world.level.Level | grep -i tickFluid
javap -classpath $JAR -p net.minecraft.server.level.ServerLevel | grep -i tickFluid

# Inspect fluid tick signatures
javap -classpath $JAR -p net.minecraft.world.level.material.FluidState | grep -i tick
javap -classpath $JAR -p net.minecraft.world.level.material.Fluid | grep -i tick

# Inspect BlockPos — check if it moved or got renamed
javap -classpath $JAR -p net.minecraft.core.BlockPos | head -30
```

`-p` includes private members. Drop it if you only want public API.

---

## Step 3 — Translate javap output into descriptor candidates

`javap` output looks like:
```
public void tickFluid(net.minecraft.core.BlockPos, net.minecraft.world.level.material.Fluid);
```

That means the parameter types are:
- `net.minecraft.core.BlockPos`
- `net.minecraft.world.level.material.Fluid`

Add a new inner list to the `parameterTypeNames` of the affected descriptor in
`TweakFastFluids.fluidBridgeDescriptors()` (line 102):

```java
new NmsBridgeDescriptor(
    BRIDGE_WORLD_TICK_FLUID, BridgeKind.METHOD, levelClasses, "tickFluid",
    List.of(
        List.of("net.minecraft.core.BlockPos", "net.minecraft.world.level.material.Fluid"),  // pre-1.21.11
        List.of("net.minecraft.core.BlockPos", "net.minecraft.world.level.material.FluidType"), // 1.21.11 Paper
    ),
    "void",
    Optional.empty())
```

The registry (`NmsBridgeRegistry.doResolve`) tries every class candidate and every parameter-type candidate in order; it takes the first combination that resolves. Adding new candidates is always safe — the resolver just tries more options.

---

## Step 4 — Verify

Build and check `/react bridge status` on the target server:

```bash
./gradlew build && ./gradlew buildPsychoLT   # or your own deploy task
```

Then in-game: `/react bridge status`

All fluid bridges should show `available=true`. If any still fail, re-run javap with the `-verbose` flag to see full signatures including generics.

---

## Scope

Only paper/folia/purpur are auto-downloaded. For **Spigot**, run BuildTools locally
(`java -jar BuildTools.jar --rev <version>`) and drop the resulting jar into `build/nms-jars/` manually.

### Mapping eras and reflection viability

React's bridge resolver is reflection-by-string-name (`NmsBridgeRegistry#doResolve` uses
`Class.getDeclaredMethod(name, paramTypes)` with `setAccessible(true)`). That imposes a hard
floor at MC 1.20.5:

| MC range | Class names | Method names | Bridge resolvable? |
|----------|-------------|--------------|--------------------|
| 1.20.5 → 1.21.11 | Mojang (e.g. `FluidState`, `Fluid`, `ServerLevel`) | Mojang (`tick`, `tickFluid`) | Yes |
| pre-1.20.5 | Spigot-deobf (e.g. `FluidState`, `FluidType`, `WorldServer`) | **obfuscated per-version** (`a`, `b`, `c` …) | No |

Pre-1.20.5 jars expose Spigot-mapped class names but leave method names obfuscated; the
per-MC letter mapping changes each release, so string-name lookups cannot target them. If
support for 1.19.4–1.20.4 is ever required, the bridge needs either per-version obfuscation
maps (SpecialSource/Mojang-mappings artifact) or a compile-time shim module.

The descriptor fanout in `TweakFastFluids.fluidBridgeDescriptors()` is javap-verified against
the jars in `build/nms-jars/` for every target version:

- **ServerLevel.tickFluid(BlockPos, Fluid)** — stable from 1.20.6 through 1.21.11 (private, resolved via `getDeclaredMethod` + `setAccessible`).
- **FluidState.tick** — `(Level, BlockPos)` for 1.20.5–1.21.1; `(ServerLevel, BlockPos, BlockState)` for 1.21.4+.
- **Fluid.tick / FlowingFluid.tick** — `(Level, BlockPos, FluidState)` for 1.20.5–1.21.1; `(ServerLevel, BlockPos, BlockState, FluidState)` for 1.21.4+.

The registry tries every class × parameter-type combination and takes the first that resolves,
so adding new candidates is always safe.
