# Features — Iris, Adapt & Integrations

Capability-gated surge guards and multi-plugin incident coordination. Iris/Adapt **map** overlays are in `05 - Features - Maps & Overlays.md`.

Use `/react integration status` for live capability status. Global `integrationSecretsEnabled` (default `false`) gates **secret** feature bundles.

## Integration model

- `IntegrationController` / `ReactIntegrationService` discover peer plugins and publish mirrored metrics (`iris-`, `adapt-`, `wormholes-`, `holoui-`, `hiddenore-`, `biletools-` — see `10 - Samplers & Metrics.md`).
- `CapabilityGatedFeature` declares `requiredCapabilities()` and optional `isSecretBundle()`.
- `ReactCapabilityFeature.autoRegister()`:
  - secret + `!integrationSecretsEnabled` → do not register
  - missing required plugin install → do not register
- `FeatureController` re-checks activation every two seconds. With a live integration controller, capability requires an accepting or healthy metrics node; installed-plugin detection is only the fallback when that controller is unavailable.
- Secret features only appear under `plugins/React/feature/` after they successfully register (secrets on + plugins present).

## Map overlays (cross-ref)

| Id | Capability | Secret |
|----|------------|--------|
| `adapt-runtime-pressure-overlay` | `adapt` | no |
| `iris-generation-pressure-overlay` | `iris` | no |
| `adapt-ability-impact-list-map` | `adapt` renderer availability | no |
| `iris-biome-chunk-share-pie-map` | disabled by `MapController` | no |
| `iris-world-chunk-share-pie-map` | `iris` renderer availability | no |

## Secret gated features

### `feature-adapt-runtime-surge-guard`

Requires `adapt`. Secret: yes. While surging, rate-limits player interact / combat / consume events (bypass: `react.secret.adapt.bypass`).

- **Class:** `FeatureAdaptRuntimeSurgeGuard`
- **Listener:** yes

| Field | Type | Default | Description |
|---|---|---|---|
| `enabled` | boolean | `true` | Enables or disables this feature. |
| `tickIntervalMS` | int | `1000` | Evaluation interval (ms). |
| `triggerTickMS` | double | `58` | Tick-time surge trigger (ms). |
| `triggerSessionLoadPercent` | double | `70` | Adapt session-load surge trigger. |
| `triggerAbilityOpsPerMinute` | double | `260` | Ability-ops surge trigger. |
| `windowMS` | int | `1800` | Rate-limit window (ms). |
| `maxInteractionsPerWindow` | int | `8` | Max interactions per window. |
| `maxCombatOpsPerWindow` | int | `10` | Max combat ops per window. |
| `maxConsumeOpsPerWindow` | int | `4` | Max consume ops per window. |
| `messageCooldownMS` | long | `2200` | Throttle message cooldown (ms). |
| `bypassPermission` | String | `react.secret.adapt.bypass` | Bypass permission. |

### `feature-iris-terrain-surge-guard`

Requires `iris`. Secret: yes. When surging, limits moves/teleports into ungenerated chunks (bypass: `react.secret.iris.bypass`).

- **Class:** `FeatureIrisTerrainSurgeGuard`
- **Listener:** yes

| Field | Type | Default | Description |
|---|---|---|---|
| `enabled` | boolean | `true` | Enables or disables this feature. |
| `tickIntervalMS` | int | `1000` | Evaluation interval (ms). |
| `triggerTickMS` | double | `56` | Tick-time trigger (ms). |
| `triggerIrisPregenQueue` | double | `280` | Iris pregen queue trigger. |
| `triggerIrisGenerationMS` | double | `24` | Iris generation ms trigger. |
| `windowMS` | int | `2500` | Rate window (ms). |
| `maxUngeneratedChunkMovesPerWindow` | int | `10` | Max ungenerated chunk moves per window. |
| `maxUngeneratedChunkTeleportsPerWindow` | int | `4` | Max ungenerated chunk teleports per window. |
| `messageCooldownMS` | long | `2500` | Message cooldown (ms). |
| `bypassPermission` | String | `react.secret.iris.bypass` | Bypass permission. |

### `feature-trinity-incident-mode`

Requires `iris` **and** `adapt`. Secret: yes. It enters when either Iris or Adapt reports pressure and either tick time or incident score reaches the configured threshold. On entry it activates each enabled incident/quarantine/surge-guard feature and queues `action-incident-playbook` on a cooldown; each activated feature still evaluates its own engagement gates.

- **Class:** `FeatureTrinityIncidentMode`

| Field | Type | Default | Description |
|---|---|---|---|
| `enabled` | boolean | `true` | Enables or disables this feature. |
| `tickIntervalMS` | int | `1000` | Evaluation interval (ms). |
| `enterIncidentScore` | double | `62` | Incident score enter threshold. |
| `enterTickMS` | double | `62` | Tick ms enter threshold. |
| `enterIrisQueue` | double | `340` | Iris pregen queue pressure threshold. |
| `enterAdaptSessionLoad` | double | `72` | Adapt session-load pressure threshold. |
| `enterAdaptAbilityOps` | double | `280` | Adapt ability-ops pressure threshold. |
| `minimumEngageMS` | int | `12000` | Minimum engage duration (ms). |
| `playbookCooldownMS` | int | `20000` | Min time between playbook queues (ms). |
| `verboseTransitions` | boolean | `true` | Log engage/release transitions. |

## Operator enable checklist (secret path)

1. Install/enable Iris and/or Adapt as needed.
2. Set `integrationSecretsEnabled = true` in global React config.
3. Restart React or run a full `/react reload` so the feature registry is rebuilt.
4. Confirm TOMLs under `plugins/React/feature/` and `enabled = true`.
5. Confirm integration metrics healthy via `/react integration status`.
6. Trinity needs **both** Iris and Adapt; single-cap surge guards need their own plugin.
