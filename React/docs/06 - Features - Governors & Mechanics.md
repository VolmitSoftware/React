# Features — Governors & Mechanics

Pressure-aware governors and world mechanics: activation/view ranges, hoppers, redstone, farms, furnaces, pathfinding, random ticks, quarantine, and incident mode. Config: `plugins/React/feature/<id>.toml`. Base `enabled` defaults to `true`.

Most governors engage only after sustained tick or incident thresholds and release through configured hysteresis.

### `activation-range-governor`

Scales down per-world Spigot entity activation ranges under sustained pressure; restores on release. Instant server-wide range change (unlike continuous `dynamic-activation-range`).

- **Class:** `FeatureActivationRangeGovernor` · **Listener:** no
- **Notes:** Reflects `World.getHandle()` → `spigotConfig` activation fields. If the config object or fields cannot be resolved, or a runtime reflection write fails, the feature calls `setEnabled(false)` and stops engaging.

| Field | Type | Default | Description |
|---|---|---|---|
| `enabled` | boolean | `true` | Enables or disables this feature. |
| `tickIntervalMS` | int | `2000` | Evaluation interval (ms). |
| `engageTickTimeMs` | double | `55` | Tick ms to engage. |
| `releaseTickTimeMs` | double | `42` | Tick ms to release. |
| `sustainEngageMs` | long | `6000` | Sustained pressure before engage (ms). |
| `sustainReleaseMs` | long | `30000` | Sustained recovery before release (ms). |
| `animalRangeFactor` | double | `0.5` | Animal range scale while engaged. |
| `monsterRangeFactor` | double | `0.6` | Monster range scale. |
| `raiderRangeFactor` | double | `0.8` | Raider range scale. |
| `miscRangeFactor` | double | `0.5` | Misc range scale. |
| `waterRangeFactor` | double | `0.5` | Water-mob range scale. |
| `villagerRangeFactor` | double | `0.5` | Villager range scale. |
| `flyingMonsterRangeFactor` | double | `0.6` | Flying-monster range scale. |
| `minimumRangeBlocks` | int | `8` | Minimum activation range after scaling. |
| `suspendInactiveVillagerTicking` | boolean | `true` | Suspend inactive villager ticking while engaged. |

### `dynamic-activation-range`

Continuously tunes an activation radius from tick time and pauses distant living entities via `ReactEntity`. Honors `SLEEP` protection.

- **Class:** `FeatureDynamicActivationRange` · **Listener:** yes (wake on damage/target)

| Field | Type | Default | Description |
|---|---|---|---|
| `enabled` | boolean | `true` | Enables or disables this feature. |
| `tickIntervalMS` | int | `1000` | Evaluation interval (ms). |
| `maxEntitiesSampledPerCycle` | int | `240` | Max entities sampled per cycle. |
| `minimumActivationRange` | double | `18` | Min activation range. |
| `maximumActivationRange` | double | `64` | Max activation range. |
| `currentActivationRange` | double | `64` | Current activation radius (blocks). |
| `targetTickMS` | double | `45` | Target tick-time threshold (ms). |
| `criticalTickMS` | double | `70` | Critical tick-time threshold (ms). |
| `minimumEntityAgeTicks` | double | `100` | Minimum entity age. |
| `ignoreTamedEntities` | boolean | `true` | Skip tamed. |
| `ignoreNamedEntities` | boolean | `true` | Skip named. |

### `dynamic-view-distance`

Maps rolling tick time and player count into per-world view and simulation distance. Requires Paper/Purpur world distance setters; on activate, calls `setEnabled(false)` and warns if setters are missing or reflection fails.

- **Class:** `FeatureDynamicViewDistance` · **Listener:** yes (no event handlers)

| Field | Type | Default | Description |
|---|---|---|---|
| `enabled` | boolean | `true` | Enables or disables this feature. |
| `updateCooldownSeconds` | int | `120` | Per-world update cooldown (seconds). |
| `warmupSeconds` | int | `45` | Warmup before touching worlds (seconds). |
| `viewDistance` | MinMax | min `6`, max `16` | View distance interpolation range. |
| `simulationDistance` | MinMax | min `4`, max `10` | Simulation distance range. |
| `lerpTickTime` | MinMax | min `45`, max `140` | Tick-time interpolation domain. |
| `lerpPlayersOnline` | MinMax | min `3`, max `100` | Player-count interpolation domain. |

### `afk-view-shedding`

Lowers idle players’ send view distance; optional pressure notch caps all players’ send view distance. Requires `Player.getSendViewDistance` / `setSendViewDistance`; disables itself when those methods are absent or fail at runtime.

- **Class:** `FeatureAfkViewShedding` · **Listener:** yes

| Field | Type | Default | Description |
|---|---|---|---|
| `enabled` | boolean | `true` | Enables or disables this feature. |
| `tickIntervalMS` | int | `5000` | Evaluation interval (ms). |
| `idleAfterSeconds` | int | `180` | Idle timeout (seconds). |
| `idleSendViewDistance` | int | `4` | Idle send view distance (chunks). |
| `minTickTimeMs` | double | `0` | Tick ms before idle shedding; `0` = always. |
| `pressureNotch` | boolean | `true` | Cap all send view distances under pressure. |
| `pressureSendViewDistanceCap` | int | `8` | Pressure cap (chunks). |
| `pressureEngageTickTimeMs` | double | `70` | Pressure engage tick ms. |
| `pressureReleaseTickTimeMs` | double | `45` | Pressure release tick ms. |
| `pressureSustainEngageMs` | long | `12000` | Sustain engage (ms). |
| `pressureSustainReleaseMs` | long | `30000` | Sustain release (ms). |
| `pressureWarmupSeconds` | int | `45` | Warmup before pressure notch (seconds). |

### `tracker-range-governor`

Scales Spigot entity tracking ranges under pressure. Reflects `spigotConfig` tracking fields; missing fields or runtime reflection failures call `setEnabled(false)`.

- **Class:** `FeatureTrackerRangeGovernor` · **Listener:** no

| Field | Type | Default | Description |
|---|---|---|---|
| `enabled` | boolean | `true` | Enables or disables this feature. |
| `tickIntervalMS` | int | `2000` | Evaluation interval (ms). |
| `engageTickTimeMs` | double | `55` | Tick ms to engage. |
| `releaseTickTimeMs` | double | `42` | Tick ms to release. |
| `sustainEngageMs` | long | `6000` | Sustain engage (ms). |
| `sustainReleaseMs` | long | `30000` | Sustain release (ms). |
| `itemRangeFactor` | double | `0.5` | Item tracking scale. |
| `miscRangeFactor` | double | `0.5` | Misc tracking scale. |
| `displayRangeFactor` | double | `0.6` | Display tracking scale. |
| `animalRangeFactor` | double | `0.75` | Animal tracking scale. |
| `monsterRangeFactor` | double | `0.75` | Monster tracking scale. |
| `otherRangeFactor` | double | `0.75` | Other tracking scale. |
| `minimumRangeBlocks` | int | `16` | Minimum tracking range after scaling. |

### `pathfinder-budget`

Shrinks A* visited-node budget for distant mobs via NMS navigation multipliers. On activate, if navigation bridges do not resolve, calls `setEnabled(false)` and leaves vanilla pathfinding alone.

- **Class:** `FeaturePathfinderBudget` · **Listener:** no

| Field | Type | Default | Description |
|---|---|---|---|
| `enabled` | boolean | `true` | Enables or disables this feature. |
| `tickIntervalMS` | int | `1000` | Evaluation interval (ms). |
| `maxEntitiesSampledPerCycle` | int | `240` | Max mobs sampled per cycle. |
| `engageTickTimeMs` | double | `48` | Tick ms before budgets shrink. |
| `budgetMultiplier` | double | `0.4` | A* budget multiplier for distant mobs. |
| `fullBudgetWithinDistance` | double | `16` | Full budget within this player distance (blocks). |

### `random-tick-governor`

Lowers `randomTickSpeed` under sustained pressure; restores on release.

- **Class:** `FeatureRandomTickGovernor` · **Listener:** no

| Field | Type | Default | Description |
|---|---|---|---|
| `enabled` | boolean | `true` | Enables or disables this feature. |
| `tickIntervalMS` | int | `2000` | Evaluation interval (ms). |
| `engageTickTimeMs` | double | `60` | Tick ms to engage. |
| `engageIncidentScore` | double | `62` | Incident score to engage. |
| `releaseTickTimeMs` | double | `45` | Tick ms to release. |
| `sustainEngageMs` | long | `6000` | Sustain engage (ms). |
| `sustainReleaseMs` | long | `30000` | Sustain release (ms). |
| `reducedRandomTickSpeed` | int | `1` | Random tick speed while engaged. |

### `per-world-tick-budget`

Measures per-world tick share and publishes NORMAL/PRESSURE/PANIC. Adaptive entity sleep, dynamic activation range, item backpressure, and pathfinder budget consume the per-world state when applying pressure behavior.

- **Class:** `FeaturePerWorldTickBudget` · **Listener:** no

| Field | Type | Default | Description |
|---|---|---|---|
| `enabled` | boolean | `true` | Enables or disables this feature. |
| `tickIntervalMS` | int | `50` | Evaluation interval (ms). |
| `budgetMs` | double | `35` | PRESSURE threshold (ms). |
| `panicMs` | double | `50` | PANIC threshold (ms). |
| `engageSustainTicks` | int | `60` | Cycles above threshold before engage. |
| `releaseSustainTicks` | int | `60` | Cycles below release before relax. |
| `releaseMs` | double | `28` | Release threshold (ms). |
| `worldOverrides` | `Map<String, WorldBudgetOverride>` | empty | Per-world budget/panic/release overrides. |

### `chunk-quarantine`

Scores hot chunks from spawns, redstone, physics, hoppers; quarantines and cancels/freezes activity under pressure.

- **Class:** `FeatureChunkQuarantine` · **Listener:** yes

| Field | Type | Default | Description |
|---|---|---|---|
| `enabled` | boolean | `true` | Enables or disables this feature. |
| `tickIntervalMS` | int | `2500` | Evaluation interval (ms). |
| `windowMS` | int | `1600` | Scoring window (ms). |
| `quarantineMS` | int | `12000` | Quarantine duration (ms). |
| `scoreTrigger` | double | `145` | Score to quarantine. |
| `maxTrackedChunks` | int | `4096` | Max tracked chunks. |
| `onlyDuringPressure` | boolean | `true` | Only under pressure. |
| `pressureIncidentScore` | double | `48` | Pressure incident threshold. |
| `pressureTickMS` | double | `58` | Pressure tick threshold (ms). |
| `bypassNearPlayers` | boolean | `true` | Bypass near players. |
| `bypassPlayerRadius` | double | `18` | Bypass radius (blocks). |
| `trackNaturalSpawns` | boolean | `true` | Track natural spawns. |
| `trackSpawnerSpawns` | boolean | `true` | Track spawner spawns. |
| `trackRedstone` | boolean | `true` | Track redstone. |
| `trackPhysics` | boolean | `true` | Track physics. |
| `samplePhysicsEveryN` | int | `3` | Physics sample cadence. |
| `trackHoppers` | boolean | `true` | Track hoppers. |
| `maxExpiryRemovalsPerCycle` | int | `192` | Max stale removals per cycle. |
| `maxExpiryScansPerCycle` | int | `1024` | Max expiry scans per cycle. |
| `maintenanceIntervalMS` | int | `1000` | Maintenance cadence (ms). |

### `circuit-manager`

Tracks redstone circuits; when redstone tick time exceeds `maxCircuitMS`, stops the worst circuit and freezes further current changes.

- **Class:** `FeatureCircuitManager` · **Listener:** yes
- **Notes:** Tick interval hard-coded `1000`.

| Field | Type | Default | Description |
|---|---|---|---|
| `enabled` | boolean | `true` | Enables or disables this feature. |
| `maxCircuitMS` | double | `15` | Max circuit redstone ms before stop. |

### `hopper-chain-coalescing`

Detects linear hopper chains and projects savings. Default measurement-only; `featureActMode` + NMS hopper hook skips intermediate ticks.

- **Class:** `FeatureHopperChainCoalescing` · **Listener:** yes

| Field | Type | Default | Description |
|---|---|---|---|
| `enabled` | boolean | `true` | Enables or disables this feature. |
| `tickIntervalMS` | int | `1000` | Evaluation interval (ms). |
| `bypassRadius` | int | `16` | Player bypass radius (blocks). |
| `minChainLength` | int | `4` | Minimum chain length. |
| `rebuildIntervalTicks` | int | `200` | Full index rebuild interval (ticks). |
| `engageOnIncident` | double | `60` | Incident score to engage accounting. |
| `engageOnTickMs` | double | `58` | Tick ms to engage. |
| `releaseOnTickMs` | double | `45` | Tick ms to release. |
| `featureActMode` | boolean | `false` | Skip intermediate hopper ticks when eligible. |
| `featureBucketBypass` | boolean | `false` | Bypass token-bucket when synthesizing transfers (act mode). |

### `hopper-item-index`

Maintains spatial indices of dropped items and hoppers for `TweakHopperIndex`.

- **Class:** `FeatureHopperItemIndex` · **Listener:** yes
- **Notes:** Folia skips initial seed/reconcile sweeps; event-driven index remains.

| Field | Type | Default | Description |
|---|---|---|---|
| `enabled` | boolean | `true` | Enables or disables this feature. |
| `reconcileIntervalMs` | int | `2000` | Reconciliation interval (ms). |

### `hopper-token-bucket`

Per-chunk token bucket limiting hopper item moves; cancels when empty.

- **Class:** `FeatureHopperTokenBucket` · **Listener:** yes

| Field | Type | Default | Description |
|---|---|---|---|
| `enabled` | boolean | `true` | Enables or disables this feature. |
| `tickIntervalMS` | int | `3000` | Evaluation interval (ms). |
| `bucketCapacity` | double | `120` | Bucket capacity. |
| `refillPerSecond` | double | `55` | Token refill rate. |
| `costPerMove` | double | `1` | Cost per hopper move. |
| `bypassWhenNearbyPlayers` | boolean | `true` | Bypass near players. |
| `bypassPlayerRadius` | double | `16` | Bypass radius (blocks). |

### `redstone-clock-governor`

Throttles high-frequency redstone clocks via `BlockRedstoneEvent` (hold current). No NMS.

- **Class:** `FeatureRedstoneClockGovernor` · **Listener:** yes

| Field | Type | Default | Description |
|---|---|---|---|
| `enabled` | boolean | `true` | Enables or disables this feature. |
| `tickIntervalMS` | int | `2000` | Evaluation interval (ms). |
| `windowMS` | int | `1000` | Transition window (ms). |
| `maxTransitionsPerWindow` | int | `12` | Max transitions per window. |
| `cooloffMS` | int | `6000` | Cool-off after throttle (ms). |
| `bypassWithinPlayerRadius` | double | `16` | Player bypass radius (blocks). |
| `onlyThrottleWithoutNearbyPlayers` | boolean | `true` | Only throttle remote clocks. |

### `crop-fast-forward`

When a chunk wakes after long dormancy, advances crop/sapling growth. **Silences under high load** (opposite polarity to most governors).

- **Class:** `FeatureCropFastForward` · **Listener:** yes

| Field | Type | Default | Description |
|---|---|---|---|
| `enabled` | boolean | `true` | Enables or disables this feature. |
| `tickIntervalMS` | int | `2500` | Evaluation interval (ms). |
| `activeRange` | int | `64` | Player range classifying chunk active (blocks). |
| `minElapsedTicks` | int | `200` | Min dormant ticks before fast-forward. |
| `maxFastForwardTicks` | int | `24000` | Cap on dormant ticks fed into growth math. |
| `engageOnIncident` | double | `30` | Incident score **above** which feature stops. |
| `engageOnTickMs` | double | `50` | Tick ms **above** which feature stops. |
| `releaseOnIncident` | double | `22` | Incident score to resume after silence. |
| `releaseOnTickMs` | double | `42` | Tick ms to resume after silence. |
| `maxTrackedChunks` | int | `32768` | Max tracked chunks. |
| `maxAdvancesPerPass` | int | `1024` | Max block updates per pass. |
| `saplingGrowthChance` | double | `0.142` | Sapling growth probability for proportional math. |

### `farm-burst-smoother`

When farm growth events burst, cancels growth and reapplies on a delayed budgeted schedule.

- **Class:** `FeatureFarmBurstSmoother` · **Listener:** yes

| Field | Type | Default | Description |
|---|---|---|---|
| `enabled` | boolean | `true` | Enables or disables this feature. |
| `tickIntervalMS` | int | `100` | Evaluation interval (ms). |
| `burstWindowMS` | int | `1200` | Burst window (ms). |
| `burstTriggerCount` | int | `72` | Growth events to trigger smoothing. |
| `minApplyDelayTicks` | int | `2` | Min apply delay (ticks). |
| `maxApplyDelayTicks` | int | `16` | Max apply delay (ticks). |
| `maxAppliesPerCycle` | int | `24` | Max applies per cycle. |
| `maxPendingUpdates` | int | `2500` | Max pending updates. |
| `stalePendingMS` | int | `15000` | Stale pending expiry (ms). |
| `onlyDuringPressure` | boolean | `true` | Only under pressure. |
| `pressureIncidentScore` | double | `42` | Pressure incident threshold. |
| `pressureTickMS` | double | `52` | Pressure tick threshold (ms). |
| `bypassNearPlayers` | boolean | `true` | Bypass near players. |
| `bypassPlayerRadius` | double | `10` | Bypass radius (blocks). |

### `furnace-brew-batching`

Tracks furnaces/brewing stands; with NMS hooks, skips intermediate ticks away from players under pressure (measurement-only without bridge).

- **Class:** `FeatureFurnaceBrewBatching` · **Listener:** yes

| Field | Type | Default | Description |
|---|---|---|---|
| `enabled` | boolean | `true` | Enables or disables this feature. |
| `tickIntervalMS` | int | `1000` | Evaluation interval (ms). |
| `bypassRadius` | int | `16` | Player bypass radius (blocks). |
| `engageIncidentScore` | double | `55` | Incident score to engage. |
| `engageTickTimeMs` | double | `55` | Tick ms to engage. |
| `releaseTickTimeMs` | double | `42` | Tick ms to release. |
| `sustainEngageMs` | long | `6000` | Sustain engage (ms). |
| `sustainReleaseMs` | long | `30000` | Sustain release (ms). |
| `maxTrackedEntries` | int | `8192` | Max tracked block entities. |
| `reseedChunksPerTick` | int | `32` | Max chunks reseeded per maintenance tick. |

### `fast-leaf-decay`

Accelerates leaf decay around break/decay events with radius scan and optional fast block removal.

- **Class:** `FeatureFastLeafDecay` · **Listener:** yes

| Field | Type | Default | Description |
|---|---|---|---|
| `enabled` | boolean | `true` | Enables or disables this feature. |
| `leafDecayDistance` | int | `6` | Leaf distance threshold for decay eligibility. |
| `leafDecayRadius` | int | `5` | Scan radius around seed (blocks). |
| `maxAsyncMS` | double | `10` | Max async work (ms). |
| `maxSyncSpikeMS` | double | `10` | Max sync spike (ms). |
| `tickIntervalMS` | int | `250` | Evaluation interval (ms). |
| `decayTriggerCooldownMS` | int | `250` | Trigger cooldown (ms). |
| `decayTickSpread` | int | `20` | Currently unused. |
| `soundChance` | double | `0.25` | Sound probability. |
| `soundVolume` | double | `0.26` | Sound volume. |
| `soundPitch` | double | `0.2` | Sound pitch. |
| `forceDecayPersistent` | boolean | `false` | Force decay persistent leaves. |
| `playSounds` | boolean | `true` | Play decay sounds. |
| `fastBlockChanges` | boolean | `true` | Use fast block changes. |
| `decaySound` | String | `minecraft:block.azalea_leaves.fall` | Decay sound key. |

### `incident-mode`

Enters a sustained incident state from high incident score or tick time (after startup grace), then rate-limits spawner/natural spawns, portals, hopper moves, and redstone until calm. See also `12 - Incident Mode & Playbooks.md`.

- **Class:** `FeatureIncidentMode` · **Listener:** yes

| Field | Type | Default | Description |
|---|---|---|---|
| `enabled` | boolean | `true` | Enables or disables this feature. |
| `tickIntervalMS` | int | `1000` | Evaluation interval (ms). |
| `enterIncidentScore` | double | `58` | Enter on incident score. |
| `exitIncidentScore` | double | `35` | Exit below this score. |
| `enterTickMS` | double | `60` | Enter on tick ms. |
| `exitTickMS` | double | `46` | Exit below this tick ms. |
| `minimumIncidentDurationMS` | int | `8000` | Minimum incident duration (ms). |
| `startupGraceMS` | int | `60000` | Startup grace (ms). |
| `rateWindowMS` | int | `1000` | Rate-limit window (ms). |
| `maxSpawnerSpawnsPerWindow` | int | `28` | Max spawner spawns per window. |
| `maxNaturalSpawnsPerWindow` | int | `70` | Max natural spawns per window. |
| `maxPortalEventsPerWindow` | int | `18` | Max portal events per window. |
| `maxHopperMovesPerWindow` | int | `120` | Max hopper moves per window. |
| `maxRedstoneTransitionsPerWindow` | int | `220` | Max redstone transitions per window. |
| `bypassNearPlayers` | boolean | `true` | Bypass near players. |
| `bypassPlayerRadius` | double | `14` | Bypass radius (blocks). |
| `verboseTransitions` | boolean | `true` | Log transitions. |
