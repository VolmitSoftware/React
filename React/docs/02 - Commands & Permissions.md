# Commands & Permissions

Root command is `/react` (alias `/re`). Use `/react help [page]` or `/react ? [page]` for generated command help. Permission root is `react.use`; `react.*` grants the full tree including shorthands.

## Permissions (`plugin.yml`)

| Permission | Default | Description |
|------------|---------|-------------|
| `react.use` | op | Use `/react` |
| `react.*` | op | All React commands; includes `react.use` and `react.shorthands.*` |
| `react.shorthands.*` | op | All shorthand commands when tweak enabled |
| `react.shorthands.gms` | op | `/gms` |
| `react.shorthands.gmsp` | op | `/gmsp` |
| `react.shorthands.gmc` | op | `/gmc` |
| `react.shorthands.give` | op | React `/give` shorthand |
| `react.shorthands.more` | op | `/more` |
| `react.shorthands.rl` | op | `/rl` → server reload |
| `react.shorthands.custom` | op | Operator-configured custom shorthands |

Not declared in `plugin.yml` but enforced in code:

| Permission | Role |
|------------|------|
| `react.configurator` | Required (or op) for `/react config gui` |

Director subcommands do not have individual `plugin.yml` nodes; root gate is `react.use` (or `react.*` / op).

Feature/tweak bypass nodes appear in config (examples: `react.bypass.projectile-limit`, `react.secret.adapt.bypass`, `react.secret.iris.bypass`).

## Root: `/react`

| Subcommand | Aliases | Origin | Description |
|------------|---------|--------|-------------|
| `monitor` | `m`, `mon` | player | Toggle action-bar monitor |
| `set-player-view-distance <distance>` | `vd`, `view-distance` | player | Set current world view and simulation distance; values above 32 are clamped, but the current command does not reject negative values (Paper setters required) |
| `map [renderer=unknown]` | | player | Open the map selector, or give the selected React renderer map |
| `reload` | `rl` | both | Reload React |
| `version` | `v` | both | Show React version |

## `/react config` (`cfg`)

The source also declares `c` for config, but `chunk` declares the same alias and Director currently resolves `/react c` to `chunk`. Use `/react config` or `/react cfg` for configuration.

| Subcommand | Aliases | Origin | Description |
|------------|---------|--------|-------------|
| `gui` | `menu`, `editor` | player | Opens TOML config editor GUI (`react.configurator` or op) |
| `monitor` | `m`, `mon` | player | Opens action-bar monitor configuration GUI |

## `/react action` (`act`, `a`)

| Subcommand | Aliases | Description |
|------------|---------|-------------|
| `purge-chunks` | `pc` | Unload chunks in selected world/area |
| `purge-entities` | `pe` | Purge matching entities |
| `collect-garbage` | `gc` | Request JVM GC |
| `quarantine-hot-chunks` | `aqhc` | Isolate hottest sampled chunks |
| `trim-entities-by-age-priority` | `ateap` | Trim old low-priority entities |
| `hopper-network-normalize` | `ahnn` | Normalize hopper hotspots |
| `prewarm-critical-chunks` | `apcc` | Preload critical chunks |
| `incident-playbook` | `aip` | Queue full incident mitigation sequence |
| `audit` | `list`, `ls` | List actions and enabled state |

Parameters vary by action (world, radius, max entities/chunks, ages). Defaults come from each action’s TOML — see `09 - Actions Catalog.md`.

## `/react chunk` (`c`)

| Subcommand | Aliases | Origin | Description |
|------------|---------|--------|-------------|
| `sample` | | player | Print sampler values for the current chunk |
| `worst` | `w` | player | Teleport to and inspect the worst sampled chunk |

## `/react environment` (`env`)

| Subcommand | Aliases | Description |
|------------|---------|-------------|
| `info` | `i` | Print platform, CPU, memory, storage, network-interface, display, sensor, GPU, and power information; also POST a smaller server/platform/storage/memory/CPU summary to `https://paste.bytecode.ninja/documents` and return its link |

## `/react benchmark` (`bench`)

| Subcommand | Aliases | Description |
|------------|---------|-------------|
| `cpu-benchmark` | `cpu`, `processor` | CPU benchmark |
| `drive-benchmark` | `drive` | Storage benchmark |
| `memory-benchmark` | `mem`, `memory`, `ram` | Memory benchmark |
| `all-benchmark` | `all`, `full` | CPU, memory, and drive |

Only one benchmark run is accepted at a time. CPU and memory tests execute synthetic workloads; the drive test reads and writes under `plugins/React/benchmark/`. Scores are relative diagnostics, not absolute hardware ratings.

## `/react debug`

| Subcommand | Aliases | Origin | Description |
|------------|---------|--------|-------------|
| `entity-data` | `ed` | player | Raycast entity; print priority/crowding diagnostics |

## `/react dev` (`developer`, `d`)

| Subcommand | Aliases | Origin | Description |
|------------|---------|--------|-------------|
| `test-all [radius=2]` | `ta` | player | Audit registries; queue the direct action suite near the player; radius is clamped to 0–6 |
| `verify` | `v`, `selftest` | both | Verify bridges, key samplers, lazy-gravity (PASS/FAIL) |

## `/react integration` (`int`)

| Subcommand | Aliases | Description |
|------------|---------|-------------|
| `status` | `s` | Peer-plugin health, heartbeats, optional correlation |

## `/react bridge` (`br`, `bridges`)

| Subcommand | Aliases | Description |
|------------|---------|-------------|
| `status` | `s`, `list` | List registered NMS bridges and resolution status |

Example: `/react bridge status`.

## `/react test` (`selftest`)

| Subcommand | Aliases | Description |
|------------|---------|-------------|
| `run [full=true] [json=true]` | `r` | Validation suite; `full` is currently report metadata and does not reduce the checks; JSON reports go to `plugins/React/test-reports/` |
| `loadtest <confirm> [players=1000] [duration=600]` | `load` | Two-pass synthetic load test on world 0; `confirm=true` is required |

Both test commands mutate their test world. `run` queues cleanup actions around world spawn, creates and removes map-frame fixtures, and spawns falling sand; `loadtest` generates heavy synthetic load and currently does not bound `players` or `duration`. Run them only in a disposable isolated server or after backing up the target world. See `15 - Operator Runbooks & Smoke Tests.md`.

## Shorthand commands (tweak `shorthands`)

When the `shorthands` tweak is enabled (`enabled = true` in TOML; constructor default is **off**), optional bare commands register:

| Command | Permission | Behavior |
|---------|------------|----------|
| `/gms` | `react.shorthands.gms` | Survival |
| `/gmsp` | `react.shorthands.gmsp` | Spectator |
| `/gmc` | `react.shorthands.gmc` | Creative |
| `/give` | `react.shorthands.give` | Item give with tab completion (may own bare `/give` while enabled) |
| `/more` | `react.shorthands.more` | Max stack of held item |
| `/rl` | `react.shorthands.rl` | Invokes server `/reload` |
| custom map keys | `react.shorthands.custom` or per-entry permission | Operator-defined shortcuts |

Configure in `plugins/React/tweak/shorthands.toml`. See `08 - Tweaks Catalog.md`.
