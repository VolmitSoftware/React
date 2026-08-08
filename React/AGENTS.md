# React Agent Guide

React is a runtime performance and monitoring plugin for Paper/Purpur/Folia: samplers, features, tweaks, actions, map monitors, and a public Java API for protection and metrics. Read this file before making any change; the workspace-level `../../AGENTS.md` also applies when working inside the VolmitSoftware workspace.

## Documentation Policy (mandatory)

- `docs/` is the authoritative reference for every feature of this plugin. Files are flat (no subfolders) and numbered `NN - Title.md`, ordered for someone new to the plugin; API docs always keep the highest numbers.
- ANY change that alters a feature, tweak, action, sampler, monitor, command, permission, setting, config TOML shape, NMS bridge behavior, workflow, or API surface MUST update the matching numbered doc in the same workstream. A behavior change with stale docs is an incomplete change — do not finish work without the doc update.
- Docs state actual runtime behavior, not intended behavior. If a change fixes a documented quirk, update or remove that quirk entry. If a change introduces surprising behavior, document it plainly.
- Docs are purely factual reference material: no marketing language, no emojis, no filler. Each file opens with a 1–4 sentence summary.
- Cross-references use exact filenames (for example `see "08 - Tweaks Catalog.md"`). When adding or renumbering files, fix every cross-reference.
- External hosted docs are not authority; this `docs/` tree replaces them.
- Do not document unfinished companion dashboards, remote clients, or relay brokers. Keep them out of `docs/` until they are intentionally released and the maintainer asks for their documentation.

## Doc Index

| File | Covers |
|------|--------|
| `00 - Overview.md` | What React is, feature map, doc index, project layout, building |
| `01 - Installation & Configuration.md` | Install, data folder, global config, language, metrics |
| `02 - Commands & Permissions.md` | Released operator `/react` commands, aliases, permission nodes, shorthands |
| `03 - Concepts.md` | Features, tweaks, actions, samplers, registries, config files |
| `04 - Features - Entity Systems.md` | Entity stacking, sleep, trim, spawn/item pressure features |
| `05 - Features - Maps & Overlays.md` | Heatmaps, pie/list maps, chunk and impact overlays |
| `06 - Features - Governors & Mechanics.md` | Governors, hoppers, redstone, farms, view distance, world mechanics |
| `07 - Features - Iris Adapt & Integrations.md` | Iris/Adapt/cross-plugin gated features |
| `08 - Tweaks Catalog.md` | Every tweak id, config, fail-closed notes |
| `09 - Actions Catalog.md` | Every action id, parameters, CLI invoke |
| `10 - Samplers & Metrics.md` | Built-in samplers, observation model, plugin-cost metrics |
| `11 - Monitors Maps & In-Game GUI.md` | Action bar, map GUI, monitor configuration |
| `12 - Incident Mode & Playbooks.md` | Incident score, incident mode, playbook actions |
| `13 - Localization.md` | Locales, catalogs, overrides |
| `14 - NMS Bridges & Platform Notes.md` | Bridges, Folia, fail-passive, jar workflow |
| `15 - Operator Runbooks & Smoke Tests.md` | Manual verification checklists |
| `16 - API - Getting Started.md` | Dependency setup, public packages, relocation |
| `17 - API - Entity Protection.md` | `ReactProtection` / providers / guard event |
| `18 - API - Metric Publishing.md` | `ReactMetrics` publishing API |
| `19 - API - PlaceholderAPI.md` | `%react_…%` keys |

Docs `00`–`15` serve operators and integrators in reading order; docs `16`–`19` serve plugin developers.

## Build and Test

- Java 25, compiled with `-parameters`. Independent Gradle build from `React/React/`: `./gradlew build`, `./gradlew test`, `./gradlew shadowJar`.
- NMS version modules under `nms/`; bridge interfaces under `bridge-api/`.

## Content Model

- **Features** (`content/feature`) — optional gameplay/performance systems with per-id TOML under `plugins/React/feature/`.
- **Tweaks** (`content/tweak`) — lighter event/NMS accelerations under `plugins/React/tweak/`.
- **Actions** (`content/action`) — operator-invoked one-shots under `plugins/React/action/`.
- **Samplers** (`content/sampler`) — metrics feeding monitors, maps, and PlaceholderAPI.
- **Controllers** (`core/controller`) — lifecycle for each registry, protection, maps, jobs.
- **Public third-party API** is only `art.arcane.react.api.protect` and `art.arcane.react.api.metric` (plus read-only PlaceholderAPI). Everything else under `art.arcane.react` is internal unless documented as a stable operator surface (commands, config keys).
