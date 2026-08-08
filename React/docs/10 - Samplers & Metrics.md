# Samplers & Metrics

Samplers are React's measurement units. They feed monitors, map renderers, and PlaceholderAPI; every sampler also implements the React map-renderer contract. The complete meaning and unit table for every built-in id is in `19 - API - PlaceholderAPI.md`.

## Observation model

- Registered samplers start with the sample controller. Cached samplers perform their measurement when sampled and reuse it for their cache interval; ticked samplers update on their own schedule.
- PlaceholderAPI demand controls which sampler values its once-per-second publisher requests. It does not enable or disable sampler objects.
- Built-in cross-plugin samplers are registered even when their source plugin is absent. Their renderer formatting is `---` until data arrives; raw sampler reads return zero before the first value and retain the last received value afterward.
- Metrics published through `ReactMetrics` create dynamic samplers while their source is registered. They disappear when that source unregisters or its plugin disables.
- A sampler's `sample(Chunk)` path uses observer data when the metric has chunk samples and otherwise resolves to zero. Its map renderer graphs the sampler history.

## Built-in sampler count

This tree registers **146** sampler ids (excluding internal `unknown`).

### adapt

| Sampler id |
|---|
| `adapt-ability-checks-per-tick` |
| `adapt-ability-ops` |
| `adapt-cache-hit-ratio` |
| `adapt-check-latency` |
| `adapt-event-ops` |
| `adapt-fx-packets` |
| `adapt-fx-shed-band` |
| `adapt-fx-timelines` |
| `adapt-learned-adaptations` |
| `adapt-minions` |
| `adapt-persistence-queue` |
| `adapt-player-sessions` |
| `adapt-provenance-ops` |
| `adapt-session-load` |
| `adapt-spatial-tickets` |
| `adapt-timing-budget` |
| `adapt-world-policy-latency` |
| `adapt-xp-payouts` |
| `adapt-xp-rate` |

### biletools

| Sampler id |
|---|
| `biletools-dirty-plugins` |
| `biletools-reload-ms` |
| `biletools-reloads` |
| `biletools-remote-slave` |
| `biletools-watched-jars` |

### chunks

| Sampler id |
|---|
| `chunk-gen-ms` |
| `chunk-load-ms` |
| `chunk-tickets` |
| `chunk-unloads` |
| `chunks` |
| `chunks-force-loaded` |
| `chunks-generated` |
| `chunks-loaded` |

### entities

| Sampler id |
|---|
| `entities` |
| `entities-animals` |
| `entities-hostile` |
| `entities-spawns` |
| `entity-ai-active-count` |

### general

| Sampler id |
|---|
| `backlog-growth-rate` |
| `block-entities` |
| `block-entities-ticking` |
| `bukkit-pending-tasks` |
| `commands` |
| `crop-fast-forward` |
| `event-handles-per-tick` |
| `event-time` |
| `events-listeners` |
| `explosion-packet-reduction` |
| `gc-pause-p95` |
| `gc-time-percent` |
| `ground-items` |
| `incident-score` |
| `jvm-threads` |
| `lazy-gravity-skipped` |
| `pdc-write-batcher` |
| `per-world-tick-time` |
| `ping-jitter` |
| `player-ping-p95` |
| `players` |
| `projectiles` |
| `scheduler-backlog` |
| `spawner-light-cache-skipped` |
| `spawner-spawns` |
| `top-chunk-cost` |
| `top-world-mspt` |
| `villagers` |
| `world-save-duration` |
| `worlds` |

### hiddenore

| Sampler id |
|---|
| `hiddenore-breaks` |
| `hiddenore-drop-rules` |
| `hiddenore-drops` |
| `hiddenore-ore-removal` |
| `hiddenore-ore-removal-rate` |
| `hiddenore-pdc-reads` |
| `hiddenore-pdc-writes` |
| `hiddenore-reloads` |
| `hiddenore-seeded-mode` |
| `hiddenore-vein-cache` |
| `hiddenore-vein-computes` |
| `hiddenore-vein-discoveries` |

### holoui

| Sampler id |
|---|
| `holoui-builder-server` |
| `holoui-display-entities` |
| `holoui-menu-definitions` |
| `holoui-menus` |
| `holoui-packets` |
| `holoui-preview-refresh` |
| `holoui-previews` |
| `holoui-sessions` |
| `holoui-spawns` |
| `holoui-tick-ms` |
| `holoui-visible-entities` |

### iris

| Sampler id |
|---|
| `iris-chunks-per-second` |
| `iris-generation-total-ms` |
| `iris-pregen-queue` |
| `iris-pregen-throughput` |

### memory

| Sampler id |
|---|
| `memory-free` |
| `memory-garbage` |
| `memory-pressure` |
| `memory-used` |
| `memory-used-after-gc` |

### processor

| Sampler id |
|---|
| `processor-outside` |
| `processor-process-load` |
| `processor-system-load` |

### react-internal

| Sampler id |
|---|
| `react-async-tick-time` |
| `react-job-budget` |
| `react-job-queue-time` |
| `react-jobs-queue` |
| `react-sync-tick-time` |

### tick

| Sampler id |
|---|
| `tick-ms-p50` |
| `tick-ms-p95` |
| `tick-ms-p99` |
| `tick-spike-rate` |
| `tick-time` |
| `ticks-per-second` |

### world-systems

| Sampler id |
|---|
| `fluid` |
| `fluid-tick-time` |
| `hopper` |
| `hopper-chain-coalescing` |
| `hopper-tick-time` |
| `physics` |
| `physics-entities` |
| `physics-tick-time` |
| `redstone` |
| `redstone-burst-rate` |
| `redstone-tick-time` |

### wormholes

| Sampler id |
|---|
| `wormholes-block-changes` |
| `wormholes-compression` |
| `wormholes-packets` |
| `wormholes-peer-rtt` |
| `wormholes-peers` |
| `wormholes-portals` |
| `wormholes-projection-observers` |
| `wormholes-projection-render-ms` |
| `wormholes-projections-active` |
| `wormholes-remote-portals` |
| `wormholes-replicated-blocks` |
| `wormholes-resyncs` |
| `wormholes-sideband-drops` |
| `wormholes-sideband-queue` |
| `wormholes-spoofed-entities` |
| `wormholes-transfers` |
| `wormholes-transfers-failed` |
| `wormholes-traversals` |
| `wormholes-view-entities` |
| `wormholes-view-subscriptions` |
| `wormholes-wire-in` |
| `wormholes-wire-out` |

## Convenience PlaceholderAPI keys

Short keys such as `%react_tps%` and `%react_mspt%` map to specific samplers. Full table: `19 - API - PlaceholderAPI.md`. Any sampler is also `%react_sampler.<id>%`.

## Cross-plugin prefixes

| Prefix | Source plugin |
|---|---|
| `adapt-` | Adapt |
| `iris-` | Iris |
| `wormholes-` | Wormholes |
| `holoui-` | HoloUi |
| `hiddenore-` | HiddenOre |
| `biletools-` | BileTools |

Mirrored metric renderers show `---` while the owning plugin has never supplied data; raw `%react_sampler.<id>%` reads return `0` before the first value and retain the last value afterward. Mirrored values lag the source's publish interval. The owning plugin's PlaceholderAPI key is canonical when both plugins expose the same metric.

## Dynamic plugin-cost samplers

React registers `plugin-<normalized-plugin-name>` for each enabled plugin except React and the peers represented by built-in integration samplers. The id lowercases the plugin name and replaces characters outside letters, digits, `_`, and `-` with `-`; the value is a five-sample rolling mean of event-handler time in `ms/s`. The sampler is removed when that plugin disables.

## Publishing your own metrics

See `18 - API - Metric Publishing.md`. Do not implement React’s internal `Sampler` type from outside the plugin.
