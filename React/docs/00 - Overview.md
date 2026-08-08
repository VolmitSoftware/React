# Overview

React is a runtime performance and monitoring plugin for Paper, Purpur, and Folia. It samples the server, runs optional features and tweaks that bound lag sources, exposes operator actions for incidents, and renders monitors and maps in-game.

## Feature Map

- **Samplers** — metrics for tick time, entities, hoppers, redstone, memory, cross-plugin integrations, and more. See `10 - Samplers & Metrics.md`.
- **Features** — optional systems covering entities, maps, governors, world mechanics, and integrations. See `04 - Features - Entity Systems.md`, `05 - Features - Maps & Overlays.md`, `06 - Features - Governors & Mechanics.md`, and `07 - Features - Iris Adapt & Integrations.md`.
- **Tweaks** — lighter event/NMS accelerations (fluids, fire, shorthands, hardstops). See `08 - Tweaks Catalog.md`.
- **Actions** — operator one-shots (purge, quarantine, incident playbook). See `09 - Actions Catalog.md`.
- **Monitors and maps** — action bar, map GUI, heatmaps. See `11 - Monitors Maps & In-Game GUI.md`.
- **Incident mode** — score-driven posture and playbooks. See `12 - Incident Mode & Playbooks.md`.
- **Public plugin API** — entity protection, metric publishing, and PlaceholderAPI. See `16 - API - Getting Started.md`, `17 - API - Entity Protection.md`, `18 - API - Metric Publishing.md`, and `19 - API - PlaceholderAPI.md`.

## Documentation Index

| File | Covers |
|------|--------|
| `00 - Overview.md` | This file |
| `01 - Installation & Configuration.md` | Install, data folder, global config |
| `02 - Commands & Permissions.md` | `/react` tree, permissions, shorthands |
| `03 - Concepts.md` | Registries, TOML layout, enable model |
| `04 - Features - Entity Systems.md` | Entity features |
| `05 - Features - Maps & Overlays.md` | Map/overlay features |
| `06 - Features - Governors & Mechanics.md` | Governors and world mechanics |
| `07 - Features - Iris Adapt & Integrations.md` | Capability-gated features |
| `08 - Tweaks Catalog.md` | All tweaks |
| `09 - Actions Catalog.md` | All actions |
| `10 - Samplers & Metrics.md` | Sampler ids and observation model |
| `11 - Monitors Maps & In-Game GUI.md` | Action bar, maps, in-game config UI |
| `12 - Incident Mode & Playbooks.md` | Incident score and response |
| `13 - Localization.md` | Locales and overrides |
| `14 - NMS Bridges & Platform Notes.md` | Bridges, Folia, jar workflow |
| `15 - Operator Runbooks & Smoke Tests.md` | Manual checklists |
| `16 - API - Getting Started.md` | Third-party dependency setup |
| `17 - API - Entity Protection.md` | Protection API |
| `18 - API - Metric Publishing.md` | Metrics API |
| `19 - API - PlaceholderAPI.md` | `%react_…%` keys |

Docs `00`–`15` are for operators; `16`–`19` are for plugin developers.

## Project Layout

| Path | Contents |
|------|----------|
| `React/React/src/main/java/art/arcane/react/` | Plugin main, content, controllers, API |
| `content/feature`, `tweak`, `action`, `sampler` | Registered content |
| `content/directorcommand` | `/react` command tree |
| `core/controller` | Lifecycle controllers |
| `api/protect`, `api/metric` | Public third-party API |
| `bridge-api/`, `nms/` | NMS bridge interfaces and version impls |

## Building

From `React/React/`, use Java 25:

```
./gradlew build
./gradlew test
./gradlew shadowJar
```

The shaded plugin jar is written under `React/React/build/libs/`. The version suffix tracks the Minecraft API React was built against (for example `2.0.0-26.2`).
