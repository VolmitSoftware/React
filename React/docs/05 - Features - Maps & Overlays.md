# Features — Maps & Overlays

Heatmaps, pie maps, list maps, and pressure overlays on Minecraft maps. Open with `/react map`. Config: `plugins/React/feature/<id>.toml`.

Most chunk heatmaps share `FeatureChunkHeatmapBase` (implements `ReactRenderer` and `ChunkGridExporter` for grid export). Pie charts share `FeatureIrisChunkSharePieBase`. List maps implement `ReactRenderer` directly.

## Shared heatmap base (`FeatureChunkHeatmapBase`)

Inherited by all heatmaps/overlays listed below unless noted.

| Field | Type | Default | Description |
|---|---|---|---|
| `enabled` | boolean | `true` | Feature on/off. |
| `chunkPixelSize` | int | `5` | Pixels per chunk cell (zoom). |
| `mapRadiusChunks` | int | `0` | `0` = derive from view distance; else fixed radius. |
| `rotateWithPlayer` | boolean | `true` | Rotate with player heading. |
| `drawCenterMarker` | boolean | `true` | Crosshair at anchor. |
| `drawLabel` | boolean | `true` | Title in header. |
| `minSignificantScore` | double | `0.001` | Below peak score → quiet map (no noise-scale colors). |

Scan uses loaded chunks in a circular chunk radius, optional player-yaw rotation, 45 ms scan cache, megamap wall support.

## Shared pie base (`FeatureIrisChunkSharePieBase`)

Donut pie + legend. Cap slices by legend height (3–32). Overflow → “Other”. Bucket cache 45 ms. Iris helpers optionally resolve Iris biome names via reflection; otherwise vanilla biomes. Pie subclasses in this set typically add **no** keys beyond `enabled`.

## Heatmaps

### `chunk-load-gen-cost-map`

Weighted load/gen cost per loaded chunk: `loadMS*1.0 + genMS*1.35 + loadRate*0.4 + genRate*0.7` from samplers `chunk-load-ms`, `chunk-gen-ms`, `chunks-loaded`, `chunks-generated`.

- **Class:** `FeatureChunkLoadGenCostMap` · Config: base heatmap keys only.

### `chunk-sampler-map`

Observer aggregate cost: `SampledChunk.totalScore()` per chunk.

- **Class:** `FeatureChunkSamplerMap` · Config: base only.

### `entity-pressure-heatmap`

Score: per-chunk `entities` sampler.

- **Class:** `FeatureEntityPressureHeatmap` · Config: base only.

### `redstone-activity-heatmap`

Score: per-chunk `redstone` sampler.

- **Class:** `FeatureRedstoneActivityHeatmap` · Config: base only.

### `hopper-container-throughput-map`

Score: per-chunk `hopper` sampler.

- **Class:** `FeatureHopperContainerThroughputMap` · Config: base only.

### `player-impact-overlay`

Chunk score: `totalScore + entities*0.5 + redstone*0.3 + hopper*0.2`. Overlay draws up to `maxPlayersDrawn` ranked players.

- **Class:** `FeaturePlayerImpactOverlay`

| Field | Type | Default | Description |
|---|---|---|---|
| *(base heatmap keys)* | | | |
| `maxPlayersDrawn` | int | `24` | Max players drawn on overlay. |
| `showPlayerInitials` | boolean | `true` | Show player initial letters. |

### `tick-spike-origin-replay-map`

Captures spike origins when `tick-time` ≥ threshold and decays heat over time. Folia path region-schedules capture without per-chunk total-score weighting.

- **Class:** `FeatureTickSpikeOriginReplayMap`

| Field | Type | Default | Description |
|---|---|---|---|
| *(base heatmap keys)* | | | |
| `tickIntervalMS` | int | `250` | Tick interval (ms). |
| `spikeThresholdMS` | double | `50` | Tick-time spike threshold (ms). |
| `spikeCaptureCooldownMS` | int | `350` | Cooldown between captures (ms). |
| `captureRadiusChunks` | int | `3` | Capture ring radius (chunks). |
| `maxTrackedChunks` | int | `4096` | Max tracked heat cells. |
| `staleChunkMS` | int | `120000` | Stale heat expiry (ms). |
| `decayEveryMS` | int | `500` | Decay cadence (ms). |
| `decayFactor` | double | `0.90` | Heat multiplier each decay. |
| `minimumHeat` | double | `0.15` | Drop heat below this. |

## List maps

### `plugin-event-impact-list-map`

Ranked plugin event cost from `PluginEventImpactSeries`.

- **Class:** `FeaturePluginEventImpactListMap`

| Field | Type | Default | Description |
|---|---|---|---|
| `enabled` | boolean | `true` | Enables or disables this feature. |
| `maxRowsPerTile` | int | `0` | `0` = fill height; else rows × grid height. |

### `adapt-ability-impact-list-map`

Ranks Adapt ability detail metrics. The feature object registers normally, but `MapController` omits the renderer until the Adapt capability is present.

- **Class:** `FeatureAdaptAbilityImpactListMap`

| Field | Type | Default | Description |
|---|---|---|---|
| `enabled` | boolean | `true` | Enables or disables this feature. |
| `maxRowsPerTile` | int | `0` | `0` = fill height. |

## Pie maps

### `plugin-event-impact-pie-map`

Pie of rolling plugin event impact. Config: `enabled` only.

- **Class:** `FeaturePluginEventImpactPieMap`

### `iris-biome-chunk-share-pie-map`

Loaded chunks in the map world by biome label. `MapController` currently hard-disables this renderer id, so it is not selectable. Config: `enabled` only.

- **Class:** `FeatureIrisBiomeChunkSharePieMap`

### `iris-world-chunk-share-pie-map`

Buckets Iris world groups by loaded chunks. `MapController` omits the renderer until the Iris capability is present. Config: `enabled` only.

- **Class:** `FeatureIrisWorldChunkSharePieMap`

## Capability-gated overlays

### `adapt-runtime-pressure-overlay`

Requires capability `adapt` (not a secret bundle). Score blends chunk total score with Adapt session load and ability ops.

- **Class:** `FeatureAdaptRuntimePressureOverlay`
- **Notes:** Activated when Adapt capability is present. Config: base heatmap keys only.

### `iris-generation-pressure-overlay`

Requires capability `iris` (not secret). Scores chunks only in worlds with an Iris remote metric group.

- **Class:** `FeatureIrisGenerationPressureOverlay`
- **Notes:** Config: base heatmap keys only.

See `07 - Features - Iris Adapt & Integrations.md` for registration vs activation gating.
