# Incident Mode & Playbooks

The `incident-score` sampler combines eight pressure signals into a 0–100 value. `incident-mode` applies event-rate limits while that pressure is sustained, while `action-incident-playbook` queues a separate set of cleanup and recovery actions.

## Incident score

Each input is linearly normalized between the listed minimum and maximum, clamped to 0–1, multiplied by its weight, and summed. Positive backlog growth is used; negative growth contributes zero.

| Sampler | Normalization range | Weight |
|---|---:|---:|
| `tick-ms-p95` | 50–150 ms | 30% |
| `tick-spike-rate` | 5–120 spikes/min | 15% |
| `gc-time-percent` | 2–25% | 10% |
| `scheduler-backlog` | 10–300 jobs | 12% |
| `backlog-growth-rate` | 1–80 jobs/s | 8% |
| `player-ping-p95` | 80–350 ms | 10% |
| `top-chunk-cost` | 2–25 ms | 8% |
| `redstone-burst-rate` | 2–80 bursts/min | 7% |

`%react_health%` is `100 - incident-score`, clamped to 0–100.

## Feature `incident-mode`

The feature waits for its 60-second startup grace, then enters when `incident-score >= 58` or `tick-time >= 60 ms`. It remains active for at least eight seconds and exits only when tick time is at most 46 ms and incident score is at most 35.

During each one-second rate window it allows the configured number of events, then applies these limits:

| Path | Default limit | Enforcement | Near-player bypass |
|---|---:|---|---|
| Spawner and trial-spawner spawns | 28 | Cancel excess spawns | No |
| Natural, nether-portal, reinforcement, jockey, patrol, and raid spawns | 70 | Cancel excess spawns | No |
| Player and entity portal events | 18 | Cancel excess events | Yes, 14 blocks by default |
| Hopper inventory moves | 120 | Cancel excess moves | Yes, 14 blocks by default |
| Redstone transitions | 220 | Restore the old current | Yes, 14 blocks by default |

The complete field/default table is in `06 - Features - Governors & Mechanics.md`. Incident mode is its own limiter; other governors continue to evaluate their own pressure gates.

## Action `action-incident-playbook`

Run `/react action incident-playbook [include-gc=true] [tier=-1] [world=ALL]` (alias `aip`). Auto tier is severe (`2`) at incident score 70 or tick time 75 ms, medium (`1`) at score 45 or tick time 58 ms, and mild (`0`) otherwise.

The playbook attempts to queue registered quarantine, trim, hopper-normalization, prewarm, and optional GC tickets, then immediately completes its own ticket. The action controller ignores disabled child actions, although the playbook still counts that queue attempt in its completion total. Accepted child actions may overlap; this is queue orchestration rather than a sequential transaction.

| Tier | Quarantine | Entity trim | Hopper normalize | Prewarm |
|---|---|---|---|---|
| 0 mild | 16 chunks, score 100, player radius 64 | 300 total, 8/chunk, age 8 min | 12 chunks, 30 updates/chunk, 36 merges | 20 chunks, radius 1 |
| 1 medium | 28 chunks, score 80, player radius 56 | 600 total, 12/chunk, age 5 min | 20 chunks, 25 updates/chunk, 48 merges | 32 chunks, radius 1 |
| 2 severe | 42 chunks, score 60, player radius 48 | 1,000 total, 16/chunk, age 3 min | 32 chunks, 18 updates/chunk, 64 merges | 48 chunks, radius 2 |

The action defaults and full parameter objects are documented in `09 - Actions Catalog.md`.

## Trinity coordination

The secret `feature-trinity-incident-mode` requires registered Iris and Adapt capabilities. Its trigger and dependent-feature behavior are documented in `07 - Features - Iris Adapt & Integrations.md`.
