# Concepts

React content is registered into four primary catalogs: **features**, **tweaks**, **actions**, and **samplers**. Every registered object has a stable string id and may have a TOML config under `plugins/React/<category>/<id>.toml`; features, tweaks, and actions have an `enabled` flag, while samplers do not.

## Categories

| Kind | Config category | Role | Lifecycle |
|------|-----------------|------|-----------|
| Feature | `feature` | Optional performance/gameplay systems, maps, governors | Activate/deactivate; optional tick interval |
| Tweak | `tweak` | Lighter event or NMS accelerations | Activate/deactivate; optional tick |
| Action | `action` | Operator-invoked one-shot jobs | Create ticket → run with params |
| Sampler | `sampler` | Metrics | Sample on demand or on schedule; feed monitors, maps, and PlaceholderAPI |

Controllers under `art.arcane.react.core.controller` own the content registries and supporting protection, map, player, job, integration, event, hotload, and configuration lifecycles. Controller configuration uses `plugins/React/core/<controller-id>.toml`.

## Config loading

`Registered.loadConfiguration()` loads:

1. Canonical: `plugins/React/<category>/<id>.toml`
2. Legacy: `plugins/React/<category>/<id>.json` (migration)

Missing files are created from Java field defaults. Fields are documented with `@ConfigDoc` (value + impact) and surface in the in-game config GUI.

## Enable model

- Base classes `ReactFeature`, `ReactTweak`, and `ReactAction` define `enabled` (default `true` on the field).
- Exception: tweak `shorthands` calls `setEnabled(false)` in its constructor so it stays off until you set `enabled = true` in TOML.
- Some features call `setEnabled(false)` at runtime when required platform APIs or NMS bridges are missing (for example pathfinder budget without navigation bridges, dynamic view distance without Paper world distance setters, AFK view shedding without send-view-distance methods).
- Disabling keeps the config file; the component does not activate or (for actions) appear in normal queues.
- Capability-gated secret features additionally require peer plugins and `integrationSecretsEnabled`.

## Observation and sampling

- Samplers that nothing consumes can remain idle (sleep-when-unobserved).
- Monitors, maps, and PlaceholderAPI mark samplers live after first use; PlaceholderAPI publishes demanded values on its one-second snapshot cadence.
- Features often call `sample(samplerId)` with a fallback when reading pressure (tick time, incident score, entity counts).

## Protection

Entity mutation paths (stack, trim, purge, sleep, despawn) consult the protection controller. Third parties declare rules via `ReactProtection` / `ReactProtectionProvider` — see `17 - API - Entity Protection.md`. React does not call plugins per-entity for protection decisions on the hot path.

## NMS bridges

Some features/tweaks require version-specific NMS hooks from `bridge-api`. If a bridge does not resolve, the component fails closed or passive to vanilla behavior and logs once. Operators check `/react bridge status`. Details: `14 - NMS Bridges & Platform Notes.md`.

## Incident score

Sampler `incident-score` aggregates pressure. Feature `incident-mode` and action `action-incident-playbook` use it for automated or operator-driven response. See `12 - Incident Mode & Playbooks.md`.


## Naming

Ids are lowercase kebab-case (`mob-stacking`, `fast-fluids`). Display names are humanized from the id. Action ids sometimes keep an `action-` prefix in the registry while CLI subcommands use the short name (`quarantine-hot-chunks` for `action-quarantine-hot-chunks`).
