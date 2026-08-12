# React Agent Guide

React is a runtime performance and monitoring plugin for Paper/Purpur/Folia: samplers, features, tweaks, actions, map monitors, and a public Java API for protection and metrics. Read this file before making any change; the workspace-level `../../AGENTS.md` also applies when working inside the VolmitSoftware workspace.

## Documentation Policy (mandatory)

- Documentation lives in the central docs repo: `../../docs` in this workspace (github.com/VolmitSoftware/docs, the Wiki.js wiki source). This repo carries no `docs/` tree. React's pages are `../../docs/react/` plus the landing page `../../docs/react.md`.
- Pages are flat under `../../docs/react/` with numbered, slugged filenames (`00-overview.md`, `01-installation-configuration.md`, ...) ordered for someone new to the plugin; API docs always keep the highest numbers. Every page carries Wiki.js YAML frontmatter (title, description, published, date, tags, editor, dateCreated) and no leading H1; bump `date` when editing.
- ANY change that alters a feature, tweak, action, sampler, monitor, command, permission, setting, config TOML shape, NMS bridge behavior, workflow, or API surface MUST update the matching numbered doc in the same workstream. A behavior change with stale docs is an incomplete change — do not finish work without the doc update.
- Docs state actual runtime behavior, not intended behavior. If a change fixes a documented quirk, update or remove that quirk entry. If a change introduces surprising behavior, document it plainly.
- Docs are purely factual reference material: no marketing language, no emojis, no filler. Each file opens with a 1–4 sentence summary.
- Cross-references are absolute wiki paths (for example `[08 - Tweaks Catalog](/react/08-tweaks-catalog)`). When adding or renaming pages, fix every cross-reference and the landing page `../../docs/react.md`.
- The central docs repo is the authority; the hosted wiki is generated from it.
- Do not document unfinished companion dashboards, remote clients, or relay brokers. Keep them out of the docs repo until they are intentionally released and the maintainer asks for their documentation.

## Doc Index

Pages under `../../docs/react/`:

| File | Covers |
|------|--------|
| `00-overview.md` | What React is, feature map, doc index, project layout, building |
| `01-installation-configuration.md` | Install, data folder, global config, language, metrics |
| `02-commands-permissions.md` | Released operator `/react` commands, aliases, permission nodes, shorthands |
| `03-concepts.md` | Features, tweaks, actions, samplers, registries, config files |
| `04-features-entity-systems.md` | Entity stacking, sleep, trim, spawn/item pressure features |
| `05-features-maps-overlays.md` | Heatmaps, pie/list maps, chunk and impact overlays |
| `06-features-governors-mechanics.md` | Governors, hoppers, redstone, farms, view distance, world mechanics |
| `07-features-iris-adapt-integrations.md` | Iris/Adapt/cross-plugin gated features |
| `08-tweaks-catalog.md` | Every tweak id, config, fail-closed notes |
| `09-actions-catalog.md` | Every action id, parameters, CLI invoke |
| `10-samplers-metrics.md` | Built-in samplers, observation model, plugin-cost metrics |
| `11-monitors-maps-in-game-gui.md` | Action bar, map GUI, monitor configuration |
| `12-incident-mode-playbooks.md` | Incident score, incident mode, playbook actions |
| `13-localization.md` | Locales, catalogs, overrides |
| `14-nms-bridges-platform-notes.md` | Bridges, Folia, fail-passive, jar workflow |
| `15-operator-runbooks-smoke-tests.md` | Manual verification checklists |
| `16-api-getting-started.md` | Dependency setup, public packages, relocation |
| `17-api-entity-protection.md` | `ReactProtection` / providers / guard event |
| `18-api-metric-publishing.md` | `ReactMetrics` publishing API |
| `19-api-placeholderapi.md` | `%react_…%` keys |

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
