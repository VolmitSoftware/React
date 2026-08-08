# Installation & Configuration

Install the React shaded jar into `plugins/`, start the server once so the data folder is created, then edit TOML configs and reload or restart as required. React targets modern Paper/Purpur/Folia with `folia-supported: true`.

## Requirements

- Java 25 for both building and running this tree. Use the React artifact built for the target server's Minecraft API version.
- Soft dependency: PlaceholderAPI (optional; enables `%react_…%` keys).
- Optional peer plugins for mirrored metrics and gated features: Iris, Adapt, Wormholes, HoloUi, HiddenOre, BileTools.

## Install

1. Place the React jar in `plugins/`.
2. Start the server. React creates `plugins/React/` and writes missing default TOML files for registered content.
3. Grant `react.use` (or `react.*`) to operators.

## Data folder layout

| Path | Role |
|------|------|
| `plugins/React/config.toml` | Global settings |
| `plugins/React/core/<controller-id>.toml` | Controller settings, including hotload, maps, and config-input sessions |
| `plugins/React/feature/<id>.toml` | Per-feature config |
| `plugins/React/tweak/<id>.toml` | Per-tweak config |
| `plugins/React/action/<id>.toml` | Per-action config |
| `plugins/React/sampler/<id>.toml` | Per-sampler config when present |
| `plugins/React/languages/overrides/<locale>.toml` | Optional message overrides |
| `plugins/React/player-settings/<uuid>.json` | Persisted action-bar monitor preferences |
| `plugins/React/data/value-cache.json` | Cached material-value analysis |
| `plugins/React/benchmark/` | Benchmark output written by benchmark commands |
| `plugins/React/info/` | Generated command, permission, and plugin metadata |
| `plugins/React/migrations/backups/` | ZIP backups created before legacy JSON migration |
| `plugins/React/test-reports/` | JSON reports requested by `/react test run` or `loadtest` |

At startup, React backs up legacy JSON configs to a timestamped ZIP, writes their TOML replacements, records a migration marker, and deletes each JSON file only after its TOML replacement exists. A legacy JSON file beside an existing canonical TOML file is ignored by hotload.

## Global configuration (`ReactConfiguration`)

Primary operator-facing keys:

| Key | Default | Description |
|-----|---------|-------------|
| `priority` | entity priority model | Weights used by culling/queueing subsystems |
| `value` | material value model | Recipe/value analysis tuning |
| `customColors` | `true` | Custom colors in monitor views |
| `verbose` | `false` | Verbose console output |
| `debug` | `false` | Debug diagnostics |
| `language` | `en_US` | Locale for player/operator messages |
| `slowTickLogMode` | `BLAME` | `OFF`, `BLAME`, `SHORT`, or `DETAILED` slow-tick logging |
| `integrationSecretsEnabled` | `false` | Allows Iris/Adapt secret integration bundles when deps present |
| `unsafeBytecode` | `false` | Eagerly attaches React's general ByteBuddy agent during startup. Versioned NMS features may attach their own instrumentation independently when active; attached instrumentation remains until JVM restart. |
| `metrics` | `true` | bStats anonymous metrics |
| `adaptAbilityOpsMetricMode` | `SUCCESSFUL_CHECKS` | Which Adapt ability-ops metric React uses |
| `monitoring` | default groups | Default action-bar monitor layout |

Nested `monitoring` fields (`ReactConfiguration.Monitoring`):

| Key | Default | Description |
|-----|---------|-------------|
| `actionBarHeaderSlots` | `6` | Max monitor groups shown in the action bar at once |
| `actionBarSamplerSlots` | `6` | Max samplers in the focused action-bar row at once |
| `monitorConfiguration` | built-in groups | Default groups/samplers (CPU, Memory, World, Physics, Iris, Adapt) |

Nested `value` fields (`ReactConfiguration.ValueConfig`):

| Key | Default | Description |
|-----|---------|-------------|
| `baseValue` | `100` | Base material value before recipe/override adjustments |
| `maxRecipeListPrecaution` | `50` | Cap on recipe traversal depth |
| `valueMutlipliers` | built-in map | Per-material multipliers (field name is spelled `valueMutlipliers` in code) |

## Reload

- The hotload controller watches `config.toml`, locale overrides, and TOML files under `core/`, `feature/`, `tweak/`, `action/`, and `sampler/`. It applies delivered file changes without a full plugin reload.
- Feature and tweak changes deactivate and reactivate active components; enable-state changes activate or deactivate them. Sampler changes restart the sampler, action changes refresh its configuration, and core changes reload the matching controller.
- Global changes refresh language, entity priority, and active player monitors. Changes to `metrics` and the startup `unsafeBytecode` decision still require a full server restart.
- `/react reload` performs a complete React disable and enable lifecycle. If the old ticker cannot drain, React refuses to re-enable and requires a server restart.
- Invalid component/controller TOML is copied to a sibling `.bak` file and replaced from current defaults. An invalid global-config or localization hotload is rejected and the current live snapshot remains active.

## Hotload controller (`core/hotload.toml`)

| Key | Default | Description |
|-----|---------|-------------|
| `enabled` | `true` | Enables managed config file watching; disabling it requires manual reloads or restarts. |
| `pollIntervalMs` | `500` | Watcher queue-drain interval, clamped to at least 100 ms. Operating-system event delivery can add latency. |
| `maxDiffMessagesPerFile` | `12` | Maximum changed-key messages included in each operator summary. |
| `notifyOperators` | `true` | Sends hotload summaries to online operators in addition to console output. |

## Other controller configuration

- `core/config-input.toml`: `sessionTimeoutSeconds = 45` controls the in-game config editor's text-input timeout and is clamped to at least five seconds.
- `core/map.toml` controls map repair, redraw, packet delivery, and megamap behavior; see `11 - Monitors Maps & In-Game GUI.md`.


## Localization

Server English is code-owned under `art.arcane.react.localization`. Bundled locales ship as TOML resources. Select locale with `language`; override selected keys via `languages/overrides/<locale>.toml`. See `13 - Localization.md`.
