# API — Metric Publishing

The `art.arcane.react.api.metric` package lets another plugin publish numbers to React monitors, maps, sampler graphs, and PlaceholderAPI. React creates and manages a sampler from each accepted metric declaration.

The integration has three parts:

- A source **declares** each metric's key, kind, unit, display name, icon, and decimal places.
- The owning plugin **publishes** values as they become available.
- React **synthesises the sampler**; third-party plugins do not implement React's internal `Sampler` type.

---

## Depending on React

See [16 - API - Getting Started.md](<16 - API - Getting Started.md>) for `plugin.yml` / `paper-plugin.yml` and the compile classpath. Nothing in this
package needs anything beyond Bukkit's `Material` and `java.*`.

---

## Sampler boundary

`art.arcane.react.api.sampler.Sampler` is React's internal measurement type and it is not implementable from
outside, by design and by construction:

- It extends React's `Registered` (which loads a TOML config file from React's data folder using a shaded
  reflection library) and `ReactRenderer` (which draws onto a Minecraft map canvas).
- Its members mention `art.arcane.volmlib.util.format.Form`, `art.arcane.curse.Curse`,
  `com.google.common.util.concurrent.AtomicDouble`, `net.kyori.adventure.text.Component` and React's own
  colour and graph types. In the shipped jar the first two are relocated under
  `art.arcane.react.util.arcane.*` — names that exist only inside React.
- The controller that registers samplers is not reachable from any API type.

Compiling a class against `Sampler` from outside React therefore fails at build time or at runtime with
`NoClassDefFoundError` on a relocated name. `ReactMetric` is the supported boundary: a source declares a descriptor, publishes a `double`, and React creates the sampler used for rendering, graphs, formatting, caching, and retirement.

---

## The lifecycle

```
you register a ReactMetricSource with the ServicesManager
   |
   |  React polls the ServicesManager every 5 seconds
   v
sourceId() is read and validated
   |
   |  refused - and re-refused every cycle - if it is invalid or reserved
   v
metrics() is called on the server tick thread
   |
   |  faulted if it throws or returns null
   v
descriptors are sanitized, capped and stored; one sampler is created per surviving metric
   |
   v
you call ReactMetrics.publish(sourceId, key, value) whenever you have a number
   |
   |  a reading is live for 15 seconds, then the sampler shows "---"
   v
you unregister, or your plugin disables
   |
   v
within 5 seconds React retires the samplers and drops the readings
```

`metrics()` is re-read when the registered instance changes, and otherwise every 60 seconds. Returning a
different list from a later call adds and removes samplers accordingly, so a metric set that depends on
which of your features are enabled works without re-registering.

---

## Threading

| Call                                         | Where you may call it                                                        |
|----------------------------------------------|-------------------------------------------------------------------------------|
| `ReactMetricSource.sourceId()` / `metrics()`  | Called **by React** on the server tick thread — the main thread on Paper, the global region thread on Folia. Must not block, must not do I/O, must not schedule and wait |
| `ReactMetrics.publish` / `withdraw`           | **Any thread**, including your own async workers. The store behind them is a `ConcurrentHashMap` with atomic counters; publishing touches no world state, no entity and no chunk, and never blocks |
| `ReactMetrics.available` / `accepting` / `publishedSourceIds` | Any thread                                                  |
| `ReactMetrics.hostMetricKeys` / `readHostMetric` / `hostMetricAvailable` | Any thread. This is the same call React makes from its own sampler ticker, which is not the server thread. Samplers cache their value for 50 ms to 5 seconds depending on what they measure, and most of those that need main-thread or world state refresh behind the read rather than blocking on it — so a value may be a sample or two old. See the caveat below before calling this on a latency-sensitive path |

The publishing half accepts calls from any thread: nothing in `publish`, `withdraw`,
`accepting` or `publishedSourceIds` reads Folia-owned state. The constraint that matters on Folia is a
different one — **the value you publish must be safe for you to compute wherever you compute it**. Counting
your own `ConcurrentHashMap` is fine anywhere. Walking `world.getEntities()` is not, and React will not save
you from that.

`readHostMetric` is the one call with a cost you do not control. It reaches into React's sampler, and when
that sampler's cache has expired the refresh happens inside your call. Most samplers hand the expensive part
to another thread and return the previous value, but a few — the entity census (`entities-animals`,
`entities-hostile`, `villagers`, `projectiles`, `physics-entities`, `ground-items`) and
`entity-ai-active-count` — gather from every player's region on Folia and wait up to 200 ms for the answer.
That wait happens at most once per refresh window, but it does happen on your thread. Do not call
`readHostMetric` from a tick handler or a packet path; sample it on your own timer and keep the number.

---

## Worked example

A plugin that publishes four numbers about its pets: how many are alive, how fast they are being summoned,
how long its tick takes, and how big its cache is.

### The descriptors

```java
package com.example.pets;

import art.arcane.react.api.metric.ReactMetric;
import art.arcane.react.api.metric.ReactMetricKind;
import art.arcane.react.api.metric.ReactMetricSource;
import org.bukkit.Material;

import java.util.List;

public final class PetMetrics implements ReactMetricSource {
    public static final String SOURCE_ID = "guardianpets";
    public static final String LIVE = "guardianpets.pets.live";
    public static final String SUMMON_RATE = "guardianpets.summons.rate";
    public static final String TICK_MS = "guardianpets.tick.ms";
    public static final String CACHE_BYTES = "guardianpets.cache.bytes";

    @Override
    public String sourceId() {
        return SOURCE_ID;
    }

    @Override
    public List<ReactMetric> metrics() {
        return List.of(
            ReactMetric.gauge(LIVE, "Live Pets", "pets").withIcon(Material.BONE),
            ReactMetric.rate(SUMMON_RATE, "Summons", "/s").withIcon(Material.SKELETON_SKULL),
            ReactMetric.millis(TICK_MS, "Pet Tick").withIcon(Material.CLOCK),
            new ReactMetric(CACHE_BYTES, ReactMetricKind.BYTES, "B", "Pet Cache", Material.CHEST, 0));
    }
}
```

`ReactMetricKind.BYTES` has no static factory, so the fourth metric uses the canonical constructor. Its
component order is `(key, kind, unit, displayName, icon, decimals)`.

### The publisher

```java
package com.example.pets;

import art.arcane.react.api.metric.ReactMetrics;

public final class PetMetricPublisher implements Runnable {
    private final PetIndex index;

    public PetMetricPublisher(PetIndex index) {
        this.index = index;
    }

    @Override
    public void run() {
        if (!ReactMetrics.accepting(PetMetrics.SOURCE_ID)) {
            return;
        }

        ReactMetrics.publish(PetMetrics.SOURCE_ID, PetMetrics.LIVE, index.livePets());
        ReactMetrics.publish(PetMetrics.SOURCE_ID, PetMetrics.SUMMON_RATE, index.summonsPerSecond());
        ReactMetrics.publish(PetMetrics.SOURCE_ID, PetMetrics.TICK_MS, index.lastTickMillis());
        ReactMetrics.publish(PetMetrics.SOURCE_ID, PetMetrics.CACHE_BYTES, index.cacheBytes());
    }
}
```

`PetIndex` is your class. The `accepting` check is the cheap way to skip the work entirely when React is
absent, not yet started, or has not read your declaration yet — it returns `true` only once React holds a
declaration for your source id.

### Registration

```java
package com.example.pets;

import art.arcane.react.api.metric.ReactMetricSource;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class PetPlugin extends JavaPlugin {
    private final ScheduledExecutorService publisher = Executors.newSingleThreadScheduledExecutor();
    private PetIndex index;

    @Override
    public void onEnable() {
        index = new PetIndex();
        getServer().getServicesManager().register(
            ReactMetricSource.class, new PetMetrics(), this, ServicePriority.Normal);
        publisher.scheduleAtFixedRate(
            new PetMetricPublisher(index), 1L, 1L, TimeUnit.SECONDS);
    }

    @Override
    public void onDisable() {
        publisher.shutdownNow();
    }
}
```

A plain executor is used here on purpose: `BukkitScheduler` does not exist on Folia, and Folia's own
`AsyncScheduler` is not on a Spigot compile classpath. Publishing is thread-safe and non-blocking, so any
timer you already have works — a `BukkitTask` on Paper, `Bukkit.getAsyncScheduler()` on Folia, or your own
executor everywhere. What matters is only that computing your numbers is legal on whatever thread you pick.

Once a second is the right cadence. React's synthesised sampler caches for one second and any reading older
than 15 seconds is treated as absent, so publishing faster gains nothing and publishing slower than every
15 seconds makes the metric flicker to `---`.

Bukkit unregisters the service when your plugin disables. React notices within 5 seconds and retires the
samplers.

---

## The minimum

Two methods, no descriptors beyond one line:

```java
getServer().getServicesManager().register(ReactMetricSource.class, new ReactMetricSource() {
    @Override
    public String sourceId() {
        return "guardianpets";
    }

    @Override
    public List<ReactMetric> metrics() {
        return List.of(ReactMetric.gauge("guardianpets.pets.live", "Live Pets", "pets"));
    }
}, this, ServicePriority.Normal);
```

`ReactMetricSource` has two abstract methods, so it cannot be a lambda. There is no default `sourceId()` and
no fallback: React refuses a source whose id it cannot validate, every cycle, with a log line.

---

## Naming rules

React enforces both naming rules on every declaration cycle. Invalid descriptors are dropped; verbose logging reports only the accepted declaration count.

**Source id.** Stripped and lowercased before validation, then it must be:

- 2 to 32 characters
- only `a`–`z`, `0`–`9`, `_`, `-`
- first character `a`–`z` or `0`–`9`
- not one of the reserved ids: `react`, `iris`, `adapt`, `wormholes`, `holoui`, `hiddenore`, `biletools`

**Metric key.** Not lowercased for you — write it lowercase.

- Must start with your source id followed by a literal `.`
- At most 64 characters in total
- After the prefix: `a`–`z` and `0`–`9`, with single `.`, `_` or `-` characters as separators
- No two separators in a row, and it may not end with one

`guardianpets.pets.live` is valid. `GuardianPets.Live`, `guardianpets..live`, `guardianpets.live-` and
`pets.live` are all rejected.

**Sampler id.** React derives it by lowercasing your key and replacing every non-alphanumeric character with
`-`. `guardianpets.pets.live` becomes the sampler `guardianpets-pets-live`, which is the id an operator sees
and the id you use in [`%react_sampler.guardianpets-pets-live%`](<19 - API - PlaceholderAPI.md>).

---

## What the descriptor controls

```java
public record ReactMetric(
    String key,
    ReactMetricKind kind,
    String unit,
    String displayName,
    Material icon,
    int decimals)
```

| Component     | Effect                                                                                                        |
|---------------|----------------------------------------------------------------------------------------------------------------|
| `key`         | Identity. Also derives the sampler id and the placeholder                                                       |
| `kind`        | Chooses the unit suffix when `unit` is blank and describes the series to React renderers                       |
| `unit`        | Suffix shown after the number. Stripped, control characters and `§` removed, truncated to 16 characters. Blank falls back to the kind default |
| `displayName` | Label on monitors and maps. Same sanitizing, truncated to 48 characters. Blank falls back to `key`             |
| `icon`        | Item icon in the monitor picker. `null` becomes `Material.SLIME_BALL`                                           |
| `decimals`    | Decimal places when formatting. Clamped to 0–4                                                                  |

Factories, and the decimals they pick:

| Factory                             | Kind      | Unit      | Decimals |
|-------------------------------------|-----------|-----------|----------|
| `ReactMetric.gauge(key, name, unit)` | `GAUGE`   | yours     | 0        |
| `ReactMetric.counter(key, name, unit)` | `COUNTER` | yours   | 0        |
| `ReactMetric.rate(key, name, unit)` | `RATE`    | yours     | 1        |
| `ReactMetric.percent(key, name)`    | `PERCENT` | `%`       | 1        |
| `ReactMetric.millis(key, name)`     | `MILLIS`  | `ms`      | 2        |

`withIcon`, `withDecimals`, `withDisplayName` and `withUnit` each return a new record.

### `ReactMetricKind`

| Constant  | Meaning                                  | Suffix when `unit` is blank |
|-----------|------------------------------------------|------------------------------|
| `GAUGE`   | A level that goes up and down            | none                         |
| `COUNTER` | A total that only grows                  | none                         |
| `RATE`    | Something per second                     | `/s`                         |
| `PERCENT` | 0–100                                    | `%`                          |
| `MILLIS`  | A duration in milliseconds               | `ms`                         |
| `BYTES`   | A size in bytes                          | `B`                          |

React does not transform your value to match the kind. A `PERCENT` metric published as `0.42` displays as
`0.42 %`, not `42 %`. Publish the number you want shown.

The enum may gain constants — write a `default` arm in any `switch` expression over it.

---

## Publishing

```java
public static boolean available();
public static boolean accepting(String sourceId);
public static boolean publish(String sourceId, String key, double value);
public static boolean publish(String sourceId, String key, double value, long sampledAtMillis);
public static void withdraw(String sourceId, String key);
public static Set<String> publishedSourceIds();
```

`publish` returns `false` and increments a drop counter — it never throws — when any of these is true:

- React's runtime is not installed
- the key was never declared, or the source is not currently declared
- the key does not belong to the source id you passed
- the value is `NaN` or infinite
- `sampledAtMillis` is `0` or negative
- `sampledAtMillis` is more than 5 seconds in the future
- `sampledAtMillis` is more than 15 seconds in the past

The three-argument overload stamps `System.currentTimeMillis()` for you. Use the four-argument one only when
you sampled at a known earlier instant, and only within that 15-second window — a batch of readings replayed
from a queue after a lag spike will be rejected, which is intended.

`withdraw` clears the current reading without removing the declaration: the sampler stays registered and
displays `---` until you publish again. To remove the metric entirely, stop returning it from `metrics()`.

### Staleness is a display state, not a numeric one

When a reading goes stale — 15 seconds with no publish, or an explicit `withdraw` — React's **monitors and
maps** show `---`. The **numeric** value does not become `NaN` or reset to zero: the synthesised sampler
holds the last value it saw and keeps returning it. Anything that reads the number rather than the rendered
string sees a frozen value, not an absent one. That includes
[`%react_sampler.…%`](<19 - API - PlaceholderAPI.md>) and `ReactMetrics.readHostMetric`.

A synthesised sampler returns `0` only before its first ever reading. If you need consumers to be able to
tell "stopped" from "genuinely zero", publish an explicit heartbeat metric alongside the one that matters.

---

## Reading React's own numbers

The same facade reads React's samplers, so another plugin can consume React's tick-time and other host metrics without a separate integration.

```java
public static Set<String> hostMetricKeys();
public static double readHostMetric(String key);
public static boolean hostMetricAvailable(String key);
```

The keys are React's sampler ids — `tick-time`, `ticks-per-second`, `entities`, `chunks`, `memory-used` and
the rest, including samplers synthesised from other plugins' published metrics. See
[19 - API - PlaceholderAPI.md](<19 - API - PlaceholderAPI.md>) for the catalogue.

```java
double mspt = ReactMetrics.readHostMetric("tick-time");

if (Double.isFinite(mspt) && mspt > 45D) {
    scheduler.shedLoad();
}
```

`readHostMetric` returns `Double.NaN` when React is absent, when the key is `null` or blank, and when no
sampler has that id. `hostMetricAvailable` is exactly `Double.isFinite(readHostMetric(key))`. There is no
exception path.

Sampler ids are React's internal names and are not part of this contract in the way metric keys are. Guard
with `hostMetricAvailable` rather than assuming an id exists.

---

## Failure policy

| Misbehaviour                              | What React does                                                                  |
|-------------------------------------------|-----------------------------------------------------------------------------------|
| `sourceId()` throws                       | Refused this cycle, logged. `metrics()` is never called                           |
| `sourceId()` is invalid or reserved       | Refused this cycle, logged, and refused again every cycle                          |
| `metrics()` throws                        | Counted as a fault, logged with the exception type. Previously declared metrics keep working |
| `metrics()` returns `null`                | Counted as a fault. Same outcome                                                   |
| `metrics()` returns `null` elements       | Skipped silently                                                                   |
| More than 64 metrics                      | Truncated at 64 with one log line — and then at 24 by the store                    |
| More than 24 valid metrics                | The first 24 unique valid keys are kept; the rest are dropped silently             |
| A 17th source registers                   | Every metric is dropped. React holds declarations for at most 16 sources; the only trace is a verbose line saying it declared `0/N` metrics |
| Two metrics with the same key             | The first wins, the duplicate is dropped                                            |
| A key that fails validation               | Dropped from the declaration; `publish` for it then returns `false`                 |
| Two services with the same source id      | The first one discovered wins; the other is ignored silently                        |
| The derived sampler id is already taken   | Warned, and **no sampler is created for that metric**. Readings are still accepted but nothing displays them. Rename the key |
| `metrics()` takes 5 ms or more            | One warning per source naming your plugin. Never changes the outcome                |
| 5 faults from one source                  | The source is **quarantined**                                                       |

**Quarantine stops React re-reading your declaration; it does not stop you publishing.** Metrics declared
before the faults keep their samplers and keep accepting values. What you lose is the ability to change the
metric set. Re-registering the same source id does not clear it — `/react reload` or a server restart does,
because they rebuild React's controllers. Registering under a different source id also works.

Nothing you pass in is echoed to players without sanitizing: unit and display name have control characters
and section signs stripped and are length-capped before React shows them anywhere.

---

## Configuration

The metric API has no configuration. It is always on and its limits — 16 sources, 24 metrics per source,
64 per declaration read, 15-second freshness, 5-second future tolerance, 5 faults before quarantine, a 5 ms
slow warning, a 5-second discovery interval, a 60-second re-declaration interval — are fixed.

`verbose = false` in `plugins/React/config.toml` hides most `[metric]` diagnostics. Enable it when a source is not appearing to see source refusals, faults, accepted declaration counts, and withdrawal notices. Invalid individual metric descriptors are omitted from the accepted declaration rather than logged one by one; sampler-id collisions and failed reconcile passes are warnings regardless of verbose mode.

---

## Where a published metric shows up

| Surface                    | How to see it                                                     |
|----------------------------|---------------------------------------------------------------------|
| Monitor picker and HUD     | `/react monitor`, then choose your sampler by its display name       |
| Map graph                  | `/react map <sampler-id>`                                           |
| PlaceholderAPI             | `%react_sampler.<sampler-id>%` — see [19 - API - PlaceholderAPI.md](<19 - API - PlaceholderAPI.md>) |
| Another plugin             | `ReactMetrics.readHostMetric("<sampler-id>")`                        |

All four use the derived sampler id, not the metric key.
