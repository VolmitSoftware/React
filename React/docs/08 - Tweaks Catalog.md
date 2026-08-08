# Tweaks Catalog

Lightweight event and NMS accelerations. Config: `plugins/React/tweak/<id>.toml`. Base `enabled` defaults to `true` on `ReactTweak` except **`shorthands`**, which forces `enabled = false` in its constructor until you enable it in TOML.

Several tweaks fail closed or passive to vanilla when required NMS bridges are missing. Inspect with `/react bridge status`.

### `entity-bubbler`

Removes selected projectile/utility entities stuck in bubble columns or soul-sand bubble lifts. Honors `DESPAWN` protection.

- **Class:** `TweakEntityBubbler` · **Listener:** yes (entity tick path)

| Field | Type | Default | Description |
|---|---|---|---|
| `enabled` | boolean | `true` | Enables or disables this tweak. |
| `entitiedToPreventFromBeingBubbled` | `List<EntityType>` | ARROW, ARMOR_STAND, MINECART, ENDER_PEARL, SNOWBALL | Types removed in bubble paths. |

### `entity-crowd-prevention`

Trims overcrowded livestock-style clusters. Honors `PURGE` protection. Config field `preventEntityBubbling` is currently unused at runtime.

- **Class:** `TweakEntityCrowdPrevention` · **Listener:** yes

| Field | Type | Default | Description |
|---|---|---|---|
| `enabled` | boolean | `true` | Enables or disables this tweak. |
| `maxEntitiesPerClusterCrowd` | int | `10` | Max entities per cluster. |
| `mobsToPreventFromCrowding` | `List<EntityType>` | COW, CHICKEN, PIG, SHEEP, PIG | Monitored types. |
| `preventEntityBubbling` | boolean | `true` | Declared; not referenced by runtime logic. |

### `entity-hardstop`

Hard-caps per-chunk entity population by cancelling spawns/breeds/drops once count ≥ limit. Spawn denial is the `SPAWN_CAP` protection operation path (see `17 - API - Entity Protection.md`).

- **Class:** `TweakEntityHardstop` · **Listener:** yes

| Field | Type | Default | Description |
|---|---|---|---|
| `enabled` | boolean | `true` | Enables or disables this tweak. |
| `maxEntitiesPerChunk` | int | `100` | Max entities per chunk. |
| `allowItemDrops` | boolean | `true` | Allow item drops past the cap. |
| `cacheIntervalTicks` | int | `200` | Rejected-chunk cache duration (ticks). |

### `experience-orb-merge`

On XP orb spawn, merges nearby orbs into the new orb.

- **Class:** `TweakExperienceOrbMerge` · **Listener:** yes

| Field | Type | Default | Description |
|---|---|---|---|
| `enabled` | boolean | `true` | Enables or disables this tweak. |
| `mergeRadius` | double | `2.75` | Merge radius (blocks). |
| `maxNearbyOrbsPerMerge` | int | `24` | Max orbs merged per spawn. |
| `maxExperiencePerOrb` | int | `10000` | Max XP per orb after merge. |

### `fast-columns`

Collapses bamboo/sugar cane/cactus/kelp columns on break/physics. Hard-coded `maxColumnSize = 16` (not in TOML).

- **Class:** `TweakFastColumns` · **Listener:** yes

| Field | Type | Default | Description |
|---|---|---|---|
| `enabled` | boolean | `true` | Enables or disables this tweak. |

### `fast-drops`

Routes eligible item drops into player inventory and grants eligible XP directly to the player. The current toggle implementation is coupled: entity item routing checks `teleportBlockDrops`; entity XP is granted only when both `teleportEntityDrops` and `teleportEntityXP` are true; block XP is removed when `teleportBlockXP` is true but granted only when `teleportEntityXP` is also true. Configurations that disable either coupled toggle can therefore leave drops vanilla or discard XP.

- **Class:** `TweakFastDrops` · **Listener:** yes

| Field | Type | Default | Description |
|---|---|---|---|
| `enabled` | boolean | `true` | Enables or disables this tweak. |
| `teleportBlockDrops` | boolean | `true` | Teleport block drops to player. |
| `teleportBlockXP` | boolean | `true` | Teleport block XP. |
| `teleportEntityDrops` | boolean | `true` | Teleport entity drops. |
| `teleportEntityXP` | boolean | `true` | Teleport entity XP. |
| `allowContainerDrops` | boolean | `false` | Allow container block drops path. |

### `fast-entity-incineration`

Instantly removes burning monsters on FIRE_TICK when no player is within radius. Honors `DESPAWN` protection.

- **Class:** `TweakFastEntityIncineration` · **Listener:** yes

| Field | Type | Default | Description |
|---|---|---|---|
| `enabled` | boolean | `true` | Enables or disables this tweak. |
| `incinerationBeyondNearestPlayer` | double | `32` | Min distance from players (blocks). |

### `fast-falling-blocks`

Cancels normal falling-block settle; queues accelerated column fall/land work budgeted by ms/tick.

- **Class:** `TweakFastFallingBlocks` · **Listener:** yes

| Field | Type | Default | Description |
|---|---|---|---|
| `enabled` | boolean | `true` | Enables or disables this tweak. |
| `maxFallMS` | double | `1.5` | Max fall processing ms per tick. |

### `fast-fire`

Short-circuits fire spread/fade/burn into `FastWorld` set/break ops.

- **Class:** `TweakFastFire` · **Listener:** yes

| Field | Type | Default | Description |
|---|---|---|---|
| `enabled` | boolean | `true` | Enables or disables this tweak. |

### `fast-fluids`

Queues bounded extra vanilla fluid ticks via NMS bridges; optional drain acceleration. **Fail-passive** without fluid bridges or after consecutive bridge failures.

- **Class:** `TweakFastFluids` · **Listener:** yes
- **Notes:** Clamps: `extraVanillaTicksPerEvent` 0–4, `maxExtraVanillaTicksPerServerTick` 16–4096, `maxBurstTicksPerLocationPerServerTick` 1–16. See `15 - Operator Runbooks & Smoke Tests.md`.

| Field | Type | Default | Description |
|---|---|---|---|
| `enabled` | boolean | `true` | Enables or disables this tweak. |
| `accelerateWater` | boolean | `true` | Accelerate water. |
| `accelerateLava` | boolean | `true` | Accelerate lava. |
| `extraVanillaTicksPerEvent` | int | `2` | Extra fluid ticks per flow event. |
| `maxExtraVanillaTicksPerServerTick` | int | `256` | Max extra fluid ticks per server tick. |
| `maxBurstTicksPerLocationPerServerTick` | int | `16` | Max burst ticks per location per flush. |
| `accelerateDrain` | boolean | `true` | Accelerate drain/retract neighbors. |

### `fast-snow`

Short-circuits snow form/fade into `FastWorld` set/break.

- **Class:** `TweakFastSnow` · **Listener:** yes

| Field | Type | Default | Description |
|---|---|---|---|
| `enabled` | boolean | `true` | Enables or disables this tweak. |

### `hopper-index`

Pre-ticks hoppers using `FeatureHopperItemIndex` to short-circuit vanilla AABB scans; optional idle empty-hopper cooldown stretch. **Fail-closed** without NMS bridges.

- **Class:** `TweakHopperIndex` · **Listener:** no

| Field | Type | Default | Description |
|---|---|---|---|
| `enabled` | boolean | `true` | Enables or disables this tweak. |
| `idleStretch` | boolean | `true` | Stretch cooldown on empty idle hoppers under load. |
| `idleStretchTicks` | int | `40` | Cooldown ticks when stretching. |
| `idleStretchSpreadPasses` | int | `40` | Spread passes for idle probes. |
| `idleStretchMinTickMs` | double | `45` | Tick ms before idle stretch engages. |

### `hopper-limit`

Cancels inventory transfers whose destination holder is a hopper when the hopper tick-time sampler exceeds the threshold. Transfers out of hoppers into non-hopper destinations are not handled by this tweak.

- **Class:** `TweakHopperLimit` · **Listener:** yes

| Field | Type | Default | Description |
|---|---|---|---|
| `enabled` | boolean | `true` | Enables or disables this tweak. |
| `maxHopperTickTime` | double | `0.75` | Max hopper tick time before cancel. |

### `item-despawn-accelerator`

On item spawn, if no player within radius, forces high `ticksLived` toward despawn (skips named/valuables when configured).

- **Class:** `TweakItemDespawnAccelerator` · **Listener:** yes

| Field | Type | Default | Description |
|---|---|---|---|
| `enabled` | boolean | `true` | Enables or disables this tweak. |
| `targetTicksLived` | int | `5600` | Forced ticks lived. |
| `noPlayerRadius` | double | `48` | No-player radius (blocks). |
| `ignoreNamedItems` | boolean | `true` | Skip named items. |
| `ignoreValuables` | boolean | `true` | Skip valuable materials. |
| `valuableItems` | `List<Material>` | netherite, nether star, diamond, ancient debris, elytra, totem, … | Valuable materials. |

### `projectile-limiter`

Caps projectile launches per player and per chunk over rolling windows.

- **Class:** `TweakProjectileLimiter` · **Listener:** yes

| Field | Type | Default | Description |
|---|---|---|---|
| `enabled` | boolean | `true` | Enables or disables this tweak. |
| `playerWindowMS` | int | `1000` | Player window (ms). |
| `chunkWindowMS` | int | `1000` | Chunk window (ms). |
| `maxProjectilesPerPlayerWindow` | int | `40` | Max per player window. |
| `maxProjectilesPerChunkWindow` | int | `160` | Max per chunk window. |
| `cleanupIntervalMS` | int | `5000` | Cleanup/tick interval (ms). |
| `limitPlayerProjectiles` | boolean | `true` | Enforce per-player limits. |
| `limitChunkProjectiles` | boolean | `true` | Enforce per-chunk limits. |
| `bypassPermission` | String | `react.bypass.projectile-limit` | Bypass permission. |

### `server-hibernator`

Experimental empty-server hibernation. When its safety gate is enabled and the server is empty, it runs `save-all` once and then calls `Thread.sleep` from a synchronous repeating task every tick, blocking the main/global server thread for `secondsPerTick`. The safety gate defaults **off**.

- **Class:** `TweakServerHibernator` · **Listener:** yes (scheduled path)

| Field | Type | Default | Description |
|---|---|---|---|
| `enabled` | boolean | `true` | Enables or disables this tweak. |
| `imACloudServerEnableMe` | boolean | `false` | Safety gate; must be true to hibernate. |
| `secondsPerTick` | double | `1.0` | Sleep seconds per tick while empty. |

### `shorthands`

Registers operator shortcuts on the Bukkit command map. **Default disabled** (`enabled = false` in constructor).

- **Class:** `TweakShorthands` · **Listener:** no
- **Permissions:** `react.shorthands.*` and children — see `02 - Commands & Permissions.md`.

| Field | Type | Default | Description |
|---|---|---|---|
| `enabled` | boolean | `false` | Enables or disables this tweak. |
| `gms` | boolean | `true` | Register `/gms`. |
| `gmsp` | boolean | `true` | Register `/gmsp`. |
| `gmc` | boolean | `true` | Register `/gmc`. |
| `give` | boolean | `true` | Register React `/give`. |
| `more` | boolean | `true` | Register `/more`. |
| `rl` | boolean | `true` | Register `/rl` → server reload. |
| `customCommands` | `Map<String, CustomShorthand>` | empty | Custom label → command entries. |

#### `customCommands` entry fields

| Field | Type | Default | Description |
|---|---|---|---|
| `enabled` | boolean | `true` | Enable this custom entry. |
| `command` | String | `""` | Command without leading slash; `{args}` or appended args. |
| `permission` | String | `react.shorthands.custom` | Extra permission (blank = none). |
| `overrideExisting` | boolean | `false` | Replace existing bare labels. |

### `spawner-player-radius`

Cancels spawner / trial-spawner creature spawns when no player is within range.

- **Class:** `TweakSpawnerPlayerRadius` · **Listener:** yes

| Field | Type | Default | Description |
|---|---|---|---|
| `enabled` | boolean | `true` | Enables or disables this tweak. |
| `requiredPlayerDistance` | double | `32` | Required player radius (blocks). |
| `onlyMonsters` | boolean | `true` | Only monsters. |
| `enforceSpawnerSpawns` | boolean | `true` | Enforce SPAWNER reason. |
| `enforceTrialSpawnerSpawns` | boolean | `true` | Enforce TRIAL_SPAWNER reason. |

### `vehicle-idle-brake`

Zeroes velocity on distant empty minecarts/boats.

- **Class:** `TweakVehicleIdleBrake` · **Listener:** no (ticked)

| Field | Type | Default | Description |
|---|---|---|---|
| `enabled` | boolean | `true` | Enables or disables this tweak. |
| `tickIntervalMS` | int | `1000` | Evaluation interval (ms). |
| `maxVehiclesSampledPerWorld` | int | `180` | Max vehicles sampled per world. |
| `minVelocitySquared` | double | `0.0004` | Min velocity² to consider. |
| `maxDistanceWithoutPlayer` | double | `48` | Max distance without player. |
| `onlyEmptyVehicles` | boolean | `true` | Only empty vehicles. |
| `brakeMinecarts` | boolean | `true` | Brake minecarts. |
| `brakeBoats` | boolean | `true` | Brake boats. |
