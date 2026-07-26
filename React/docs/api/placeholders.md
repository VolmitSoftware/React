# React PlaceholderAPI keys

React registers a PlaceholderAPI expansion under the identifier `react`. Every key is read-only, needs no
code, and works anywhere PlaceholderAPI is parsed — scoreboards, holograms, tab lists, chat formats, signs,
NPC names.

```
%react_tps%            20.00
%react_mspt%           12.35
%react_health%         92.50
%react_entities%       12345
%react_world.mspt%     3.50
%react_sampler.chunks% 8192
```

---

## Requirements

PlaceholderAPI must be **enabled before React enables**. React declares `softdepend: [PlaceholderAPI]`, so on
a normal startup that is automatic. If you install PlaceholderAPI onto a running server, or enable it after
React, run `/react reload` — React only attempts to register the expansion during its own enable.

The expansion is `persist()`-marked, so `/papi reload` does not unregister it.

| Property   | Value            |
|------------|------------------|
| Identifier | `react`          |
| Author     | Volmit Software  |
| Version    | `2.0.0`          |
| Required plugin | `React`     |

---

## Syntax

```
%react_<key>%
```

Keys are lowercased before lookup, so `%react_TPS%` and `%react_tps%` are the same key. Dots are part of the
key (`memory.used`), not a separator PlaceholderAPI understands, so nothing else needs escaping.

An **unknown** key returns nothing at all, and PlaceholderAPI leaves the literal `%react_nonsense%` in your
text. That is the fastest way to tell a typo from a value that is merely unavailable.

An **unavailable** value returns `---`.

If a key ever throws internally, React logs it once per key and returns `---` rather than propagating into
your format.

---

## Warm-up: the first read is always `---`

React does not sample anything until a placeholder asks for it, and it publishes values on a one-second
cycle. So the first time a key is requested it returns `---`, and the real value appears on the next cycle —
within one second.

This is deliberate. React has more than 140 samplers; sampling all of them once a second so that a
placeholder nobody uses stays warm would cost more than the placeholders are worth. Asking for a key adds it
to a demand set; only demanded keys are sampled.

Consequences:

- A scoreboard that refreshes once a second shows `---` for one frame after a restart, then real numbers.
- A one-shot `/papi parse me %react_tps%` immediately after startup shows `---`. Run it twice.
- **Demand is sticky.** Once a key has been asked for, React keeps sampling it until that sampler leaves the
  registry or React restarts. Asking for a key once a day costs the same as asking every tick, and a key you
  used once and removed from your config keeps its sampler warm until the next restart.
- An unknown `sampler.<id>` never enters the demand set, so a typo in a config that is parsed thousands of
  times a second cannot make React sample anything.

`%react_available%` tells you whether React has published a snapshot at all. It is `false` before the first
publish and `true` afterwards, and it is the right thing to gate a whole scoreboard section on.

---

## Number formats

React formats every value itself. It never emits a thousands separator, never emits a `%` sign (that would
break the surrounding placeholder syntax), and never emits colour codes.

| Style           | Format                                    | Used by                                            |
|-----------------|-------------------------------------------|-----------------------------------------------------|
| Fractional      | Exactly two decimals, `.` as the point    | `tps`, `mspt`, `mspt-p95`, `health`, `top-world-mspt`, `world.mspt` |
| Count           | Plain integer, rounded                    | `entities`, `chunks`, `ground-items`, `memory.used`, `memory.free` |
| Automatic       | Integer when the value is a whole number, otherwise two decimals | every `sampler.<id>` key             |
| Boolean         | `true` or `false`                         | `available`                                          |
| Unavailable     | `---`                                     | anything with no reading, and any non-finite value   |

Add your own unit text in your format string: `%react_tps%` and `%react_mspt% ms`.

---

## The complete key list

Thirteen keys exist. Twelve are fixed, one is a group.

### Server-wide

| Key                     | Unit                | Meaning                                                                                       |
|-------------------------|---------------------|------------------------------------------------------------------------------------------------|
| `%react_available%`     | `true` / `false`    | Whether React has published a snapshot yet. `false` before the first cycle and after a shutdown |
| `%react_tps%`           | ticks per second    | Server tick rate, derived from real elapsed time between ticks. Capped at `20.00`                |
| `%react_mspt%`          | milliseconds        | Mean tick duration, from the server's own average-tick-time counter                             |
| `%react_mspt-p95%`      | milliseconds        | 95th-percentile tick duration over the last 1200 recorded ticks. The number that tells you about stutter, where `mspt` tells you about steady load |
| `%react_health%`        | 0–100               | `100` minus React's incident score. `100.00` is a healthy server. The score is a weighted blend of p95 tick time, tick spike rate, GC time, scheduler backlog and its growth, p95 player ping, the cost of the hottest chunk, and redstone burst rate |
| `%react_top-world-mspt%` | milliseconds       | The share of the current tick attributed to the single most expensive world. Not the same as that world's own tick duration — it is the fraction of total sampled cost times `mspt` |
| `%react_entities%`      | count               | Entities across all loaded chunks                                                                |
| `%react_chunks%`        | count               | Loaded chunks across all worlds                                                                  |
| `%react_ground-items%`  | count               | Dropped item entities lying on the ground                                                        |
| `%react_memory.used%`   | mebibytes (integer) | JVM heap in use: total heap minus free heap, divided by 1048576 and rounded                     |
| `%react_memory.free%`   | mebibytes (integer) | Heap **headroom to the maximum**: max heap minus used heap, divided by 1048576 and rounded. This is not `Runtime.freeMemory()` and it does not shrink as the JVM grows its heap |

### Per-world

| Key                  | Unit         | Meaning                                                                                          |
|----------------------|--------------|---------------------------------------------------------------------------------------------------|
| `%react_world.mspt%` | milliseconds | Mean tick duration of the world the **requesting player** is in                                    |

`world.mspt` is the only key whose value depends on who is asking. Everything else is one number for the
whole server, identical for every viewer.

It returns `---` when:

- there is no player context — a console parse, or a format rendered for nobody;
- React has not measured that world yet;
- the player has been offline for more than 60 seconds. Within that grace window React keeps serving the
  world the player was last in, so a leave message that includes the key still resolves.

### The sampler passthrough

```
%react_sampler.<sampler-id>%
```

Any registered sampler, by id. This is the escape hatch for everything React measures that does not have a
named key, and it is where metrics published by other plugins appear.

Three things to know:

- **The value is raw.** No unit conversion, no percentage scaling, no suffix. Memory samplers are in
  **bytes**, not mebibytes. The three CPU load samplers and `explosion-packet-reduction` are **fractions
  between 0 and 1** that React's own monitors render as a percentage, so the placeholder is 100× smaller than
  the number an operator sees. `adapt-cache-hit-ratio` is also a fraction, but React renders it as one too,
  so that key matches its monitor. Durations are milliseconds.
- **An unknown id returns nothing**, so `%react_sampler.typo%` is left in your text verbatim.
- **A sampler that disappears takes its key with it.** The integration samplers listed below are always
  registered, present source plugin or not. A sampler synthesised from a third-party published metric is
  not: it goes away when that plugin unregisters, and the key reverts to literal text.

---

## Sampler catalogue

Every id below can be used as `%react_sampler.<id>%`. All are server-wide.

### Tick and health

| Id                    | Unit         | Meaning                                                                 |
|-----------------------|--------------|--------------------------------------------------------------------------|
| `ticks-per-second`    | ticks/s      | Same series as `%react_tps%`                                             |
| `tick-time`           | ms           | Same series as `%react_mspt%`                                            |
| `tick-ms-p50`         | ms           | Median tick duration over the last 1200 ticks                            |
| `tick-ms-p95`         | ms           | 95th-percentile tick duration                                            |
| `tick-ms-p99`         | ms           | 99th-percentile tick duration                                            |
| `tick-spike-rate`     | spikes/min   | Ticks that overran the spike threshold, extrapolated to a per-minute rate |
| `incident-score`      | 0–100        | The composite behind `%react_health%`, before inversion                  |
| `per-world-tick-time` | ms           | Mean tick duration of the **worst** world                                |
| `top-world-mspt`      | ms           | Same series as `%react_top-world-mspt%`                                  |
| `top-chunk-cost`      | ms           | Share of the tick attributed to the single most expensive chunk           |

### CPU, memory and the JVM

| Id                     | Unit             | Meaning                                                                          |
|------------------------|------------------|-----------------------------------------------------------------------------------|
| `processor-system-load` | fraction 0–1    | Whole-machine CPU load                                                            |
| `processor-process-load` | fraction 0–1   | CPU load of this JVM alone                                                        |
| `processor-outside`    | fraction 0–1     | System load minus process load: what the rest of the box is doing                 |
| `memory-used`          | bytes            | Heap in use — total minus free                                                    |
| `memory-free`          | bytes            | Headroom to the maximum heap                                                      |
| `memory-used-after-gc` | bytes            | Heap in use at the last point it dropped, that is, after the last collection      |
| `memory-garbage`       | bytes            | `memory-used` minus `memory-used-after-gc`: how much of the heap is collectable    |
| `memory-pressure`      | bytes/s          | Allocation rate                                                                    |
| `gc-time-percent`      | percent 0–100    | Share of wall time spent in garbage collection                                     |
| `gc-pause-p95`         | ms               | 95th-percentile GC pause                                                           |
| `jvm-threads`          | count            | Live JVM threads                                                                   |

### Scheduling

| Id                     | Unit      | Meaning                                                            |
|------------------------|-----------|---------------------------------------------------------------------|
| `bukkit-pending-tasks` | count     | Tasks queued in the Bukkit scheduler. Reads `0` where the server does not support the query |
| `scheduler-backlog`    | count     | Jobs queued in React's own sync job controller                      |
| `backlog-growth-rate`  | jobs/s    | Rate of change of that queue. Sustained positive means React is falling behind |
| `react-jobs-queue`     | count     | The same queue depth, on React's own dashboard series               |
| `react-job-budget`     | ms        | How far React's job execution overran its per-tick budget           |
| `react-job-queue-time` | ms        | Estimated compute time sitting in the queue                         |
| `react-sync-tick-time` | ms        | Time React spent on the server thread last tick                     |
| `react-async-tick-time` | ms       | Time React spent on its own threads last tick                       |

### Events

| Id                       | Unit           | Meaning                                                   |
|--------------------------|----------------|------------------------------------------------------------|
| `event-time`             | ms/s           | Milliseconds per second spent inside event handlers, across every plugin |
| `events-listeners`       | count          | Registered event listeners                                 |
| `event-handles-per-tick` | handles/tick   | Rolling average of event handler invocations per tick      |

### Worlds and chunks

| Id                    | Unit        | Meaning                                                        |
|-----------------------|-------------|-----------------------------------------------------------------|
| `worlds`              | count       | Loaded worlds                                                   |
| `chunks`              | count       | Loaded chunks — same series as `%react_chunks%`                 |
| `chunks-loaded`       | loads/s     | Chunk load events per second                                    |
| `chunks-generated`    | gens/s      | Newly generated chunks per second                               |
| `chunk-unloads`       | unloads/s   | Chunk unload events per second                                  |
| `chunks-force-loaded` | count       | Force-loaded chunks across all worlds                           |
| `chunk-tickets`       | count       | Plugin chunk tickets held across all worlds                     |
| `chunk-load-ms`       | ms          | Mean handling time of a chunk load                              |
| `chunk-gen-ms`        | ms          | Mean handling time of a chunk load that generated a new chunk   |
| `world-save-duration` | ms          | Duration of the last world save                                 |
| `block-entities`      | count       | Block entities (tile entities) across all worlds                |
| `block-entities-ticking` | count    | Block entities that actually tick                               |

### Entities

| Id                       | Unit      | Meaning                                                            |
|--------------------------|-----------|---------------------------------------------------------------------|
| `entities`               | count     | Same series as `%react_entities%`                                   |
| `entities-animals`       | count     | Animals                                                             |
| `entities-hostile`       | count     | Hostile mobs                                                        |
| `villagers`              | count     | Villagers                                                           |
| `projectiles`            | count     | Projectile entities                                                 |
| `physics-entities`       | count     | Entities subject to physics — falling blocks, minecarts and the like |
| `ground-items`           | count     | Same series as `%react_ground-items%`                               |
| `entities-spawns`        | spawns/s  | Entity spawn events per second, all causes                          |
| `spawner-spawns`         | spawns/s  | Spawns that came from a mob spawner or trial spawner                |
| `entity-ai-active-count` | count     | Living non-player entities with AI enabled                          |

### Players

| Id               | Unit  | Meaning                                                     |
|------------------|-------|--------------------------------------------------------------|
| `players`        | count | Players online                                               |
| `player-ping-p95` | ms   | 95th-percentile player ping                                  |
| `ping-jitter`    | ms    | Mean change in ping between samples, across online players   |

### Block activity

| Id                            | Unit        | Meaning                                                              |
|-------------------------------|-------------|------------------------------------------------------------------------|
| `redstone`                    | updates/s   | Redstone change events per second                                      |
| `redstone-tick-time`          | ms          | Time spent on redstone per tick                                        |
| `redstone-burst-rate`         | bursts/min  | Redstone bursts detected, extrapolated to a per-minute rate            |
| `hopper`                      | updates/s   | Hopper transfers and hopper physics per second                         |
| `hopper-tick-time`            | ms          | Time spent on hoppers per tick                                         |
| `hopper-chain-coalescing`     | ticks/s     | Hopper ticks saved per second by React's chain coalescing              |
| `fluid`                       | flows/s     | Fluid flow events per second                                           |
| `fluid-tick-time`             | ms          | Time spent on fluids per tick                                          |
| `physics`                     | updates/s   | Block physics and piston events per second                             |
| `physics-tick-time`           | ms          | Time spent on block physics per tick                                   |
| `commands`                    | commands/s  | Commands executed per second, from players, console and RCON           |
| `crop-fast-forward`           | blocks/s    | Crop growth stages advanced per second by React's fast-forward feature |
| `lazy-gravity-skipped`        | ticks/s     | Falling-block ticks skipped per second by React's lazy gravity         |
| `spawner-light-cache-skipped` | checks/s    | Spawner light checks skipped per second by React's light cache         |
| `explosion-packet-reduction`  | ratio 0–1   | Share of explosion packets removed by React's explosion batching. Stored as a fraction; React's own monitors render it as a percentage |
| `pdc-write-batcher`           | writes/s    | Persistent-data writes deferred per second by React's write batcher    |
| `unknown`                     | —           | A stand-in used when React needs a sampler and has none. Always reads `0`, so the placeholder prints `0`; React's own monitors render it as `---` |

### Integration samplers

These read numbers reported by the other Volmit plugins. They are **always registered**, whether or not the
source plugin is installed, so the key always resolves and never falls back to literal text. Before the
source plugin has ever reported, they read `0`; once it has, they hold the last value they were given even
if the plugin goes away. Gate the display on your own knowledge of what is installed, not on the key
resolving.

Iris:

| Id                        | Unit     | Meaning                                  |
|---------------------------|----------|-------------------------------------------|
| `iris-chunks-per-second`  | chunks/s | Live generation throughput                |
| `iris-generation-total-ms` | ms      | Total generation time in the sample window |
| `iris-pregen-queue`       | chunks   | Chunks queued for pregeneration           |
| `iris-pregen-throughput`  | chunks/s | Pregeneration throughput                  |

Adapt:

| Id                             | Unit       | Meaning                                          |
|--------------------------------|------------|---------------------------------------------------|
| `adapt-player-sessions`        | players    | Players with an active Adapt session              |
| `adapt-learned-adaptations`    | count      | Adaptations learned across online players         |
| `adapt-session-load`           | percent    | Session processing load                           |
| `adapt-ability-ops`            | ops/min    | Ability operations per minute                     |
| `adapt-ability-checks-per-tick` | ops/tick  | Ability checks per tick                           |
| `adapt-check-latency`          | µs         | Mean ability-check latency, microseconds          |
| `adapt-cache-hit-ratio`        | ratio 0–1  | Ability cache hit ratio                           |
| `adapt-timing-budget`          | percent    | Share of the ability timing budget consumed       |
| `adapt-event-ops`              | ops/min    | Event handler operations per minute               |
| `adapt-xp-rate`                | xp/min     | Experience awarded per minute                     |
| `adapt-xp-payouts`             | ops/min    | Experience payout operations per minute           |
| `adapt-provenance-ops`         | ops/min    | Anti-farm provenance operations per minute        |
| `adapt-spatial-tickets`        | orbs       | Spatial experience tickets outstanding            |
| `adapt-persistence-queue`      | count      | Depth of the persistence write queue              |
| `adapt-minions`                | minions    | Active minions                                    |
| `adapt-fx-timelines`           | count      | Active effect timelines                           |
| `adapt-fx-packets`             | packets    | Effect packets used in the window                 |
| `adapt-fx-shed-band`           | band index | Which effect-shedding band is active              |
| `adapt-world-policy-latency`   | ms         | World policy lookup latency                       |

Wormholes:

| Id                              | Unit      | Meaning                                       |
|---------------------------------|-----------|------------------------------------------------|
| `wormholes-portals`             | portals   | Portals on this server                         |
| `wormholes-remote-portals`      | portals   | Portals known on peer servers                  |
| `wormholes-peers`               | servers   | Connected peer servers                         |
| `wormholes-peer-rtt`            | ms        | Highest round-trip time to a peer              |
| `wormholes-traversals`          | per min   | Portal traversals per minute                   |
| `wormholes-transfers`           | players   | Cross-server transfers in flight               |
| `wormholes-transfers-failed`    | count     | Cross-server transfers that failed, cumulative |
| `wormholes-projections-active`  | count     | Active portal projections                      |
| `wormholes-projection-observers` | count    | Players observing a projection                 |
| `wormholes-projection-render-ms` | ms/s     | Milliseconds per second spent rendering projections |
| `wormholes-replicated-blocks`   | blocks/s  | Blocks replicated per second                   |
| `wormholes-block-changes`       | blocks/s  | Block change packets per second                |
| `wormholes-view-subscriptions`  | views     | Active view subscriptions                      |
| `wormholes-view-entities`       | entities  | Entities tracked by view subscriptions         |
| `wormholes-spoofed-entities`    | entities  | Client-side spoofed entities                   |
| `wormholes-packets`             | packets/s | Packets per second                             |
| `wormholes-wire-in`             | bytes/s   | Inbound wire throughput                        |
| `wormholes-wire-out`            | bytes/s   | Outbound wire throughput                       |
| `wormholes-compression`         | ratio     | Outbound compression ratio                     |
| `wormholes-sideband-queue`      | bytes     | Bytes queued on the sideband channel           |
| `wormholes-sideband-drops`      | per s     | Sideband frames dropped per second             |
| `wormholes-resyncs`             | count     | Resync requests, cumulative                    |

HoloUi:

| Id                        | Unit      | Meaning                                     |
|---------------------------|-----------|----------------------------------------------|
| `holoui-menus`            | menus     | Menus currently open                         |
| `holoui-menu-definitions` | count     | Menu definitions loaded                      |
| `holoui-sessions`         | players   | Players holding a session                    |
| `holoui-display-entities` | entities  | Display entities in existence                |
| `holoui-visible-entities` | entities  | Display entities currently visible           |
| `holoui-spawns`           | per s     | Display entity spawns per second             |
| `holoui-packets`          | packets/s | Packets per second                           |
| `holoui-tick-ms`          | ms        | Time spent in HoloUi's tick                  |
| `holoui-previews`         | count     | Previews open                                |
| `holoui-preview-refresh`  | per s     | Preview refreshes per second                 |
| `holoui-builder-server`   | 0 or 1    | Whether the builder server is running        |

HiddenOre:

| Id                            | Unit      | Meaning                                        |
|-------------------------------|-----------|-------------------------------------------------|
| `hiddenore-breaks`            | blocks/s  | Blocks broken per second                        |
| `hiddenore-drops`             | per s     | Drops injected per second                       |
| `hiddenore-drop-rules`        | rules     | Drop rules loaded                               |
| `hiddenore-ore-removal`       | 0 or 1    | Whether ore removal is enabled                  |
| `hiddenore-ore-removal-rate`  | blocks/s  | Blocks removed per second by ore removal        |
| `hiddenore-seeded-mode`       | 0 or 1    | Whether seeded vein mode is on                  |
| `hiddenore-vein-cache`        | chunks    | Chunks held in the vein cache                   |
| `hiddenore-vein-computes`     | chunks/s  | Chunks whose veins were computed, per second    |
| `hiddenore-vein-discoveries`  | per s     | Veins discovered per second                     |
| `hiddenore-pdc-reads`         | per s     | Persistent-data reads per second                |
| `hiddenore-pdc-writes`        | per s     | Persistent-data writes per second               |
| `hiddenore-reloads`           | count     | Config reloads, cumulative                      |

BileTools:

| Id                        | Unit    | Meaning                                     |
|---------------------------|---------|----------------------------------------------|
| `biletools-watched-jars`  | jars    | Jars being watched for changes               |
| `biletools-dirty-plugins` | plugins | Plugins with a pending reload                |
| `biletools-reloads`       | count   | Hot reloads performed, cumulative            |
| `biletools-reload-ms`     | ms      | Duration of the last hot reload              |
| `biletools-remote-slave`  | 0 or 1  | Whether the remote slave link is online      |

### Metrics published by other plugins

Any plugin that publishes through React's metric API gets a sampler here too. The id is the metric key with
every non-alphanumeric character replaced by `-`, so a plugin publishing `guardianpets.pets.live` gives you:

```
%react_sampler.guardianpets-pets-live%
```

The value is the raw `double` that plugin published, formatted by the automatic rule — integer when whole,
two decimals otherwise.

**A metric that stops updating freezes, it does not go to `---`.** React's monitors mark a reading stale
after 15 seconds and render `---`, but the underlying sampler keeps returning the last value it saw, and the
placeholder reads that number. A metric reads `0` only before its very first reading. Treat an unchanging
number as a possible "publisher stopped", not as a measurement. See [metrics.md](metrics.md).

---

## Cost

Resolving a placeholder is a map lookup against a snapshot that React publishes once a second on its own
thread. It never samples on the calling thread, never touches a world, and never blocks — so a scoreboard
that renders 30 React keys per player per tick costs 30 hash lookups per player per tick.

What does cost something is the sampling behind a key, which happens once a second regardless of how many
players display it. Asking for one key on a 200-player scoreboard costs the same as asking for it once.

---

## Troubleshooting

| Symptom                                       | Cause                                                                          |
|-----------------------------------------------|---------------------------------------------------------------------------------|
| `%react_tps%` appears literally in the output | The expansion is not registered. PlaceholderAPI was enabled after React — run `/react reload` |
| One key appears literally, the rest work      | Typo, or a `sampler.<id>` that is not a registered sampler                       |
| Everything shows `---`, `available` is `false` | React's runtime has not started, or is shutting down                            |
| One key shows `---` forever                    | Its sampler reports a non-finite value. `%react_world.mspt%` also does this with no player context |
| A `sampler.<id>` number stopped changing       | The plugin behind it stopped publishing. Placeholders read the raw sampler, which freezes at the last value rather than going to `---` |
| The value is 100× smaller than the monitor shows | You are reading a sampler that stores a fraction and is rendered as a percentage — `processor-system-load`, `processor-process-load`, `processor-outside`, `explosion-packet-reduction`. Multiply in your own format, or use a named key |
| Memory numbers look enormous                   | `sampler.memory-used` is in bytes. `%react_memory.used%` is in mebibytes         |
