# Monitors, Maps & In-Game GUI

React exposes live metrics through a per-player HUD, filled-map renderers, and inventory configuration interfaces. Player monitor choices persist separately from the global defaults, while map repair and delivery are controlled by `core/map.toml`.

## Action-bar monitor

- `/react monitor` toggles monitoring for the executing player. The enabled state and selected monitor layout persist in `plugins/React/player-settings/<uuid>.json` and are restored on join.
- `/react config monitor` edits that player's groups, samplers, heads, names, and colors. Global `monitoring.monitorConfiguration` supplies the initial layout for a player who has no saved configuration.
- The default groups are CPU, Memory, World, Physics, Iris, and Adapt. `monitoring.actionBarHeaderSlots = 6` and `monitoring.actionBarSamplerSlots = 6` cap visible group and focused-sampler entries.
- While the player is sneaking and not in the monitor's falling/flying cooldown, hotbar scrolling selects a group. Pressing sneak twice within 250 ms locks or unlocks that group; while locked, sneaking and scrolling changes its head sampler.
- The main row requests the action bar and falls back to a boss bar when another higher-priority HUD owner has the action bar. The focused group requests the title/subtitle surface. React releases and clears its surfaces when monitoring stops.
- Explicit monitor and GUI changes save immediately. A periodic dirty check runs once per minute, and quitting forces a final save.

## Map selection and item handling

- `/react map` opens the renderer selector. `/react map <renderer-id>` selects a renderer directly.
- Selecting with a normal left click, or using the direct command, puts a newly created React map in the main hand. An existing main-hand item is moved into inventory and dropped at the player's location only if inventory overflows.
- Shift-clicking a renderer in the selector adds its map to inventory instead and drops only overflow.
- React map items carry persistent renderer metadata. Inventory maps are repaired on join and in maintenance batches; item-frame maps are rediscovered and repaired after startup or reload.
- A framed map uses the item frame as the renderer's spatial anchor. A held map falls back to the viewer location, then the map world's spawn if no viewer is available.

Every built-in sampler and every feature implementing `ReactRenderer` is considered for the map registry, regardless of the feature's `enabled` field. Integration-prefixed renderers are omitted until their peer capability is present. `iris-biome-chunk-share-pie-map` is explicitly disabled by `MapController` and is not selectable.

## Item-frame delivery and megamaps

- Active frame maps are pushed only to eligible viewers. Distance, aim direction, line of sight, held-map bypass, and idle-view cadence are configurable.
- Adjacent item frames with the same renderer can form one tiled megamap. Walls above `megamapMaxTiles` fall back to independent single-map rendering.
- When `megamapSplitDuplicates` is enabled, cloned React maps placed in frames receive distinct map ids so neighboring copies can occupy separate tiles.
- Held maps redraw more frequently than framed maps by default. Startup/reload temporarily increases repair batches and frame pushes so restored displays converge sooner.

## Map controller (`core/map.toml`)

| Key | Default | Effect |
|---|---:|---|
| `maintenanceTickIntervalMs` | `500` | Main repair and delivery maintenance cadence. |
| `inventoryRepairCadenceMs` | `250` | Minimum interval between inventory repair passes. |
| `inventoryRepairBatchSize` | `3` | Online player inventories repaired per pass. |
| `itemFrameRepairCadenceMs` | `250` | Minimum interval between loaded-chunk frame scans. |
| `itemFrameChunkBatchSize` | `8` | Loaded chunks scanned for framed maps per pass. |
| `startupBoostDurationMs` | `12000` | Duration of aggressive repair and delivery after startup/reload. |
| `startupBoostInventoryBatchSize` | `12` | Inventory repair batch during startup boost. |
| `startupBoostItemFrameChunkBatchSize` | `32` | Frame-repair chunk batch during startup boost. |
| `frameMapPushIntervalMs` | `600` | Push interval for actively viewed frame maps. |
| `startupFrameMapPushIntervalMs` | `100` | Frame-map push interval during startup boost. |
| `frameMapIdlePushIntervalMs` | `6000` | Push interval for nearby viewers not actively looking at the frame. |
| `frameMapPushRadiusBlocks` | `24` | Maximum viewer distance for frame-map pushes. |
| `frameMapPushOutsideRangeWhenHolding` | `false` | Lets a player holding the same map bypass frame distance checks. |
| `frameMapLookDotThreshold` | `0.45` | Aim-direction dot-product threshold for active viewing. |
| `frameMapRequireLineOfSight` | `true` | Requires line of sight for nearby frame viewers; holders bypass it. |
| `frameMapPushStateRetentionMs` | `600000` | Retention time for per-viewer frame delivery state. |
| `heldMapRedrawIntervalMs` | `150` | Minimum canvas redraw interval for held maps. |
| `frameMapRedrawIntervalMs` | `300` | Minimum canvas redraw interval for framed maps. |
| `frameMapValidateIntervalMs` | `2000` | Interval between full item-stack validation for tracked frames. |
| `megamapEnabled` | `true` | Combines adjacent same-renderer frame maps into a tiled display. |
| `megamapMaxTiles` | `32` | Maximum tiles in one megamap wall. |
| `megamapSplitDuplicates` | `true` | Assigns cloned framed maps distinct ids for tiling. |

## In-game configuration GUI

- `/react config gui` opens the TOML editor for players with `react.configurator` or operator status.
- Feature, tweak, action, sampler, and controller fields annotated with `@ConfigDoc` are grouped for browsing and written to their canonical TOML files.
- Text-entry sessions use `core/config-input.toml`; `sessionTimeoutSeconds` defaults to `45` and is clamped to at least five seconds.
- Color and monitor pickers edit the player's stored monitor configuration rather than the global defaults.
