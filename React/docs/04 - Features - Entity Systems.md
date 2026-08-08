# Features — Entity Systems

Entity-side features for stacking, sleep, trim, items, spawns, vehicles, portals, and explosions. Config: `plugins/React/feature/<id>.toml`. Base field `enabled` defaults to `true`.

Stacking, trim, and sleep honor the protection API (`17 - API - Entity Protection.md`): `STACK`, `TRIM`, and `SLEEP` respectively.

### `mob-stacking`

Merges compatible living entities into stacks (count via health/`ReactEntity`), with optional custom names and vacuum collect packets. Processes dirty chunks on a batch interval.

- **Class:** `FeatureMobStacking`
- **Listener:** yes
- **Notes:** Honors `ReactProtection` / `STACK`. Folia uses region-aware dirty-chunk processing. Skips tamed pets; optional skip for custom mobs; optional spawner-only stacking. Default stackable types: all alive+spawnable minus `PLAYER`, `ARMOR_STAND`, `VILLAGER`, `WANDERING_TRADER`, `FALLING_BLOCK`.

| Field | Type | Default | Description |
|---|---|---|---|
| `enabled` | boolean | `true` | Enables or disables this feature. |
| `maxStackSize` | int | `10` | Maximum stack size. |
| `maxHealth` | double | `100` | Maximum health for stack targets. |
| `stackableTypes` | `Set<EntityType>` | see notes | Types allowed to stack. |
| `customNames` | boolean | `true` | Apply custom stack names. |
| `searchRadius` | double | `6` | Search radius (blocks). |
| `vacuumEffect` | boolean | `true` | Send vacuum/collect packet effect. |
| `skipCustomMobs` | boolean | `true` | Skip custom/plugin mobs. |
| `onlySpawnerMobs` | boolean | `false` | Only stack spawner-origin mobs. |
| `batchIntervalMs` | int | `250` | How often queued chunks process (ms). |

### `adaptive-entity-sleep`

Puts distant living entities into sleep/pause under load; optional mid-range duty-cycling via `Mob#setAware` when available. Wakes on damage/target when configured.

- **Class:** `FeatureAdaptiveEntitySleep`
- **Listener:** yes
- **Notes:** Honors `ReactProtection` / `SLEEP`. Folia has a dedicated sleep scan path. Duty-cycle requires `setAware`/`isAware` or is disabled.

| Field | Type | Default | Description |
|---|---|---|---|
| `enabled` | boolean | `true` | Enables or disables this feature. |
| `tickIntervalMS` | int | `1000` | Evaluation interval (ms). |
| `maxEntitiesSampledPerCycle` | int | `320` | Max entities sampled per cycle. |
| `minimumEntityAgeTicks` | int | `200` | Minimum age before sleep eligible. |
| `sleepBeyondNearestPlayer` | double | `48` | Distance from players to sleep (blocks). |
| `ignoreNamedEntities` | boolean | `true` | Skip named entities. |
| `ignoreTamedEntities` | boolean | `true` | Skip tamed entities. |
| `ignorePersistentEntities` | boolean | `true` | Skip persistent entities. |
| `ignoreVillagers` | boolean | `true` | Skip villagers. |
| `ignoreBosses` | boolean | `true` | Skip bosses. |
| `wakeOnDamage` | boolean | `true` | Wake on damage. |
| `wakeOnTarget` | boolean | `true` | Wake on target. |
| `dutyCycleEnabled` | boolean | `true` | Duty-cycle awareness between duty and sleep distance under load. |
| `dutyCycleStartDistance` | double | `24` | Distance where duty-cycling begins (blocks). |
| `dutyCycleSlots` | int | `4` | Rotating awareness slots. |
| `dutyCycleMinTickMs` | double | `42` | Tick ms required before duty-cycling engages. |

### `entity-trimmer`

When entity counts exceed soft caps (per chunk / player / world), removes lowest-priority eligible entities in batches.

- **Class:** `FeatureEntityTrimmer`
- **Listener:** yes (tick-driven)
- **Notes:** Honors `TRIM` protection. Folia: `trimEntitiesFolia()`. Hardcoded minimum age `ticksLived < 400` (not configurable).

| Field | Type | Default | Description |
|---|---|---|---|
| `enabled` | boolean | `true` | Enables or disables this feature. |
| `skipCustomMobs` | boolean | `false` | Skip custom mobs. |
| `playerMobBlockDistance` | int | `32` | Player proximity radius (blocks). |
| `blacklist` | `List<EntityType>` | displays, player, armor stands, frames, carts, boats, projectiles, items, TNT, … | Types never trimmed. |
| `printEntityPurgeSuccess` | boolean | `true` | Log successful purges. |
| `softMaxEntitiesPerChunk` | int | `11` | Soft max entities per chunk. |
| `softMaxEntitiesPerPlayer` | int | `100` | Soft max entities per player. |
| `softMaxEntitiesPerWorld` | int | `1000` | Soft max entities per world. |
| `priorityPercentCutoff` | double | `0.1` | Priority cutoff when ordering targets. |
| `tickIntervalMS` | int | `1000` | Evaluation interval (ms). |
| `opporunityThreshold` | double | `0.25` | Opportunity threshold for trim passes. |
| `minKillBatchSize` | int | `100` | Minimum kill batch size. |

### `item-super-stacker`

Merges nearby dropped items into flagged bundles with pickup explode-into-inventory behavior.

- **Class:** `FeatureItemSuperStacker`
- **Listener:** yes

| Field | Type | Default | Description |
|---|---|---|---|
| `enabled` | boolean | `true` | Enables or disables this feature. |
| `maxItemsPerBundle` | int | `64` | Max items per bundle. |
| `searchRadius` | double | `3` | Search radius (blocks). |

### `item-backpressure`

Under high tick time or entity count (or per-world pressure), removes remote ground items away from players, with age/name/valuable protections.

- **Class:** `FeatureItemBackpressure`
- **Listener:** no
- **Notes:** Folia samples near players and only removes region-owned items.

| Field | Type | Default | Description |
|---|---|---|---|
| `enabled` | boolean | `true` | Enables or disables this feature. |
| `tickIntervalMS` | int | `1000` | Evaluation interval (ms). |
| `triggerTickTimeMS` | double | `60` | Tick-time trigger (ms). |
| `triggerEntityCount` | int | `5000` | Entity-count trigger. |
| `maxItemsScannedPerWorld` | int | `220` | Max items scanned per world. |
| `maxItemsRemovedPerCycle` | int | `90` | Max items removed per cycle. |
| `minimumItemAgeTicks` | int | `200` | Minimum item age. |
| `noPlayerRadius` | double | `40` | No-player radius (blocks). |
| `protectNamedItems` | boolean | `true` | Protect named items. |
| `protectValuables` | boolean | `true` | Protect valuable materials. |
| `valuables` | `Set<Material>` | netherite, nether star, diamond, elytra, totem, … | Valuable materials set. |

### `spawn-burst-limiter`

Cancels `CreatureSpawnEvent` bursts per chunk over a rolling window (total / spawner / monster caps). Can push spawner delay when spawner spawns are limited.

- **Class:** `FeatureSpawnBurstLimiter`
- **Listener:** yes

| Field | Type | Default | Description |
|---|---|---|---|
| `enabled` | boolean | `true` | Enables or disables this feature. |
| `tickIntervalMS` | int | `5000` | Evaluation interval (ms). |
| `windowMS` | int | `1200` | Rolling window (ms). |
| `maxSpawnsPerChunkWindow` | int | `22` | Max spawns per chunk window. |
| `maxSpawnerSpawnsPerChunkWindow` | int | `10` | Max spawner spawns per chunk window. |
| `maxMonsterSpawnsPerChunkWindow` | int | `15` | Max monster spawns per chunk window. |
| `enforceNaturalSpawns` | boolean | `true` | Enforce natural spawns. |
| `enforceSpawnerSpawns` | boolean | `true` | Enforce spawner spawns. |
| `enforceMonsterSpawns` | boolean | `true` | Enforce monster spawns. |
| `ignoreNamedEntities` | boolean | `true` | Skip named entities. |
| `spawnerBackoff` | boolean | `true` | Delay spawners that hit the burst limit. |
| `spawnerBackoffTicks` | int | `600` | Backoff delay (ticks). |

### `spawner-light-cache`

Caches dark-candidate light snapshots per chunk for spawner/`TRIAL_SPAWNER` monster spawns; measurement-only until engaged under pressure, then cancels spawns when no dark candidate matches.

- **Class:** `FeatureSpawnerLightCache`
- **Listener:** yes
- **Notes:** No NMS bridge. Bypass near players. Invalidates on place/break/spread.

| Field | Type | Default | Description |
|---|---|---|---|
| `enabled` | boolean | `true` | Enables or disables this feature. |
| `tickIntervalMS` | int | `2000` | Evaluation interval (ms). |
| `bypassRadius` | int | `8` | Player bypass radius (blocks). |
| `darkLightMax` | int | `7` | Max block light for dark candidate. |
| `invalidationIntervalTicks` | int | `200` | Snapshot TTL (ticks). |
| `maxTrackedChunks` | int | `4096` | Max cached chunk snapshots. |
| `engageIncidentScore` | double | `60` | Incident score to engage cancellation. |
| `engageTickTimeMs` | double | `58` | Tick ms to engage cancellation. |
| `releaseTickTimeMs` | double | `45` | Tick ms to release to measurement-only. |

### `lazy-gravity`

Tracks falling blocks on clear vertical paths; with NMS bridge and pressure gate, can `SKIP` falling-block ticks away from players.

- **Class:** `FeatureLazyGravity`
- **Listener:** yes
- **Notes:** Requires falling-block tick hook. Measurement-only without bridge. Tick interval floored to `max(250, tickIntervalMS)`.

| Field | Type | Default | Description |
|---|---|---|---|
| `enabled` | boolean | `true` | Enables or disables this feature. |
| `tickIntervalMS` | int | `1000` | Evaluation interval (ms). |
| `bypassRadius` | int | `24` | Player bypass radius (blocks). |
| `engageIncidentScore` | double | `55` | Incident score to engage. |
| `engageTickTimeMs` | double | `55` | Tick ms to engage. |
| `releaseTickTimeMs` | double | `42` | Tick ms to release. |
| `sustainEngageMs` | long | `6000` | Sustained pressure before engage (ms). |
| `sustainReleaseMs` | long | `30000` | Sustained recovery before release (ms). |
| `maxTrackedTasks` | int | `8192` | Max tracked falling-block tasks. |
| `reapPerTick` | int | `1024` | Max reaped expired tasks per maintenance tick. |

### `minecart-tether`

Zeroes velocity on minecart entity types when no player is within `maxBlockDistance`.

- **Class:** `FeatureMinecartTether`
- **Listener:** yes (entity tick listeners)

| Field | Type | Default | Description |
|---|---|---|---|
| `enabled` | boolean | `true` | Enables or disables this feature. |
| `maxBlockDistance` | double | `32` | Max distance without player (blocks). |

### `portal-traffic-smoother`

Throttles player and non-player portal traffic per chunk over a window; cancels and re-teleports after a short delay when over caps.

- **Class:** `FeaturePortalTrafficSmoother`
- **Listener:** yes

| Field | Type | Default | Description |
|---|---|---|---|
| `enabled` | boolean | `true` | Enables or disables this feature. |
| `tickIntervalMS` | int | `2000` | Evaluation interval (ms). |
| `windowMS` | int | `1000` | Rolling window (ms). |
| `maxPlayerPortalsPerChunkWindow` | int | `6` | Max player portals per chunk window. |
| `maxEntityPortalsPerChunkWindow` | int | `16` | Max entity portals per chunk window. |
| `cooloffMS` | int | `5000` | Cool-off after throttle (ms). |
| `playerDelayTicks` | int | `2` | Player re-teleport delay (ticks). |
| `entityDelayTicks` | int | `4` | Entity re-teleport delay (ticks). |
| `maxQueuedDelays` | int | `512` | Max queued delays; full queue cancels only. |
| `onlyDuringPressure` | boolean | `true` | Only under pressure. |
| `pressureIncidentScore` | double | `40` | Pressure incident threshold. |
| `pressureTickMS` | double | `52` | Pressure tick-time threshold (ms). |
| `bypassNearPlayers` | boolean | `true` | Bypass near players. |
| `bypassPlayerRadius` | double | `10` | Bypass radius (blocks). |

### `explosion-packet-batching`

Collects same-tick explosions, clusters by `mergeRadius`, and with NMS suppressor + pressure gate can suppress per-explosion packets and broadcast merged packets.

- **Class:** `FeatureExplosionPacketBatching`
- **Listener:** yes
- **Notes:** Measurement-only without suppressor. Tick interval floored to `max(250, tickIntervalMS)`.

| Field | Type | Default | Description |
|---|---|---|---|
| `enabled` | boolean | `true` | Enables or disables this feature. |
| `tickIntervalMS` | int | `1000` | Evaluation interval (ms). |
| `bypassRadius` | int | `16` | Player bypass radius for clusters (blocks). |
| `mergeRadius` | int | `12` | Cluster merge radius (blocks). |
| `engageIncidentScore` | double | `55` | Incident score to engage. |
| `engageTickTimeMs` | double | `55` | Tick ms to engage. |
| `releaseTickTimeMs` | double | `42` | Tick ms to release. |
| `sustainEngageMs` | long | `6000` | Sustained pressure before engage (ms). |
| `sustainReleaseMs` | long | `30000` | Sustained recovery before release (ms). |
| `maxBufferedPerWorld` | int | `4096` | Max pending explosions per world. |
| `mergedBroadcastRangeBlocks` | int | `64` | Merged broadcast range (blocks). |

### `fast-explosions`

Staggers primed TNT fuse offsets, caps primed TNT left in explosion block lists, optionally disables TNT chain priming, and applies fast block updates.

- **Class:** `FeatureFastExplosions`
- **Listener:** yes
- **Notes:** Effective tick interval override `250` for counters. No ReactProtection/NMS bridge.

| Field | Type | Default | Description |
|---|---|---|---|
| `enabled` | boolean | `true` | Enables or disables this feature. |
| `maxPrimesPerTick` | int | `3` | Max primes per tick. |
| `spreadPrimedFuseTicks` | int | `7` | Fuse spread between chained primed TNT (ticks). |
| `maxExplosionChainsPerTick` | int | `3` | Max explosion chains per tick. |
| `fastBlockUpdates` | boolean | `true` | Fast block updates. |
| `disableEntityChainReactions` | boolean | `false` | Disable entity chain reactions. |
| `explosionChainReactions` | boolean | `false` | Allow limited chain explosions. |
