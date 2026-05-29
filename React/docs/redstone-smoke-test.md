# Redstone Guardrails Smoke Test Checklist

Manual verification checklist for React redstone monitoring and event-level guardrails.
React does not replace `Blocks.REDSTONE_WIRE` or install redstone NMS bindings.

## Pre-conditions

1. Fresh server boot or clean React data folder.
2. React plugin installed.
3. React config at default values unless the scenario specifies otherwise.
4. Startup logs show no redstone registry install attempt.

## Scenarios

| # | Server | MC Version | Feature | Expected |
|---|--------|------------|---------|----------|
| 1 | Paper or Purpur | current target | Redstone samplers | Activity is visible without NMS replacement |
| 2 | Paper or Purpur | current target | `redstone-clock-governor` | High-frequency clocks away from players are throttled |
| 3 | Folia | current target | `redstone-clock-governor` | Event-level throttling runs without region scheduler exceptions |
| 4 | Paper or Purpur | current target | Existing server redstone implementation | Paper/Purpur redstone settings remain server-owned |

## Scenario 1 - Redstone Samplers

1. Start the server with React.
2. Confirm startup has no redstone NMS install attempt.
3. Build and power a redstone clock or medium redstone network.
4. Check `redstone`, `redstone-burst-rate`, and `redstone-tick-time` sampler output.
5. Check the redstone activity heatmap if map rendering is enabled.

Expected result: redstone activity is measured and displayed while vanilla/server-native wire behavior remains active.

## Scenario 2 - Redstone Clock Governor

1. Enable `redstone-clock-governor` in `plugins/React/feature/redstone-clock-governor.toml`.
2. Start or reload React.
3. Build a high-frequency redstone clock away from players.
4. Verify repeated transitions are clamped after the configured `maxTransitionsPerWindow` and `cooloffMS`.
5. Move a player within `bypassWithinPlayerRadius` and verify the nearby-player bypass preserves local contraptions when `onlyThrottleWithoutNearbyPlayers = true`.

Expected result: the clock governor throttles remote spam clocks using `BlockRedstoneEvent` only.

## Scenario 3 - Folia Safety

1. Start Folia with React and `redstone-clock-governor` enabled.
2. Build a high-frequency clock near a chunk or region boundary.
3. Verify the governor throttles via the redstone event path.
4. Confirm no region ownership, cross-thread, or registry replacement exceptions appear in the log.

Expected result: redstone guardrails remain event-level and do not mutate NMS registries.

## Scenario 4 - Server-Native Redstone Settings

1. Configure the server's native redstone implementation, such as Paper/Purpur redstone settings.
2. Start React.
3. Verify React does not inspect or override the setting for redstone wire replacement.
4. Power a redstone line and confirm behavior matches the server's configured implementation.

Expected result: server-native redstone behavior stays authoritative; React only monitors or throttles through Bukkit events.

## Additional Checks

| Check | Command / Method | Expected |
|-------|------------------|----------|
| No redstone NMS classes | `unzip -l React-*.jar \| grep 'art/arcane/react/nms/redstone'` | no output |
| Test suite | `./gradlew test` | `BUILD SUCCESSFUL` |
| Shaded build | `./gradlew shadowJar` | `BUILD SUCCESSFUL` |
| Startup logs | server console | no redstone wire install attempt |
| Fast fluids | server console | fluid acceleration remains handled by the `fast-fluids` tweak, not plugin-load registry replacement |
