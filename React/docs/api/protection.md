# React entity protection API

`art.arcane.react.api.protect` is how another plugin tells React **do not touch this entity**. React stacks,
trims, purges, sleeps and despawns entities on its hot paths; if your plugin owns an entity — a pet, a
summon, a boss, a quest NPC, a moving part of a machine — React will treat it like any other mob unless you
say otherwise.

There are two ways to say it, and they solve different problems:

| You want to…                                                    | Use                                     |
|-----------------------------------------------------------------|-----------------------------------------|
| protect entities that already carry something identifying — a persistent-data key, a scoreboard tag, a type, a world | `ReactProtectionProvider` (ServicesManager) |
| protect one specific entity you have in hand, right now          | `ReactProtection.protect(entity, plugin, ops)` |

Most integrations want the first. A rule is evaluated from facts React already reads, applies to every
entity that matches it including ones spawned before your plugin existed, and needs no bookkeeping of your
own.

---

## The one thing to understand first

**React never asks you about an entity.** There is no callback, no `boolean isProtected(Entity)` you get to
implement, and no way to make a decision when React is about to act.

You **declare** rules. React reads them, compiles them into a bitmask lookup, and evaluates that lookup
itself, per entity, on paths that run for every entity in every loaded chunk. `ReactProtectionProvider.rules()`
is called roughly every 30 seconds, on one of React's own worker threads — not once per entity, not once per
operation, and not on the server thread. A provider that tries to answer "is *this* entity mine?" has nowhere
to put the answer.

The result is that protection is expressed as **matchers over facts React can read cheaply**: entity type,
world name, scoreboard tags, persistent-data keys, spawn reason. If your ownership cannot be expressed that
way, write a marker onto the entity's persistent data when you create it and match on the key.

If you genuinely need a per-decision veto, `ReactEntityGuardEvent` gives you one — but only for two of the
six operations, and it fires per entity on the same hot paths. See
[Vetoing per entity with an event](#vetoing-per-entity-with-an-event).

---

## The operations

`ReactOperation` names the six things React does to entities. Protecting against one does not protect against
the others.

| Constant    | What React does when it is *not* protected                                                                                                                                     |
|-------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `STACK`     | Merges the entity into a nearby identical mob. The merged entity is **removed** with `Entity#remove()` and the survivor's stack count goes up by yours. Its UUID, persistent data, name, equipment and AI state are gone. Both sides are checked: a protected entity is never absorbed and is never a merge target. |
| `TRIM`      | Deletes the entity to get a world, chunk or entity-type group back under a configured budget. Selection is by React's priority model — oldest and least valuable first — so a trim can fire while the server is perfectly healthy. |
| `PURGE`     | Deletes the entity as part of a deliberate sweep: an operator action, an entity-crowd cull, or a hot-chunk quarantine. Purge is indiscriminate within its filter; it does not rank candidates. |
| `SLEEP`     | Stops the entity from thinking. React sets `Mob#setAware(false)` or pauses the entity when no player is within the activation range, and wakes it when one comes back. The entity still exists and still renders. Pathfinding, targeting, item pickup and ticking behaviour stop. |
| `DESPAWN`   | Removes the entity early through one of React's accelerated-cleanup paths — burning mobs finished off out of player sight, and bubbled entities disposed of instead of being ticked. |
| `SPAWN_CAP` | Refuses to let an entity of that kind exist at all. React cancels the spawn event when a chunk is over its entity budget. `SPAWN_CAP` protection exempts the spawn from the cap so it goes through. |

`ReactOperations` packs them into an `int` bitmask:

```java
int everything = ReactOperations.all();
int deletion = ReactOperations.of(ReactOperation.TRIM, ReactOperation.PURGE, ReactOperation.DESPAWN);
boolean stacked = ReactOperations.covers(deletion, ReactOperation.STACK);
Set<ReactOperation> readable = ReactOperations.expand(deletion);
```

`of` is overloaded for a `Set<ReactOperation>` as well as varargs, so it round-trips with `expand`.
`ReactOperations.NONE` is `0`. `sanitize(int)` drops bits that do not name a constant — React applies it to
everything you hand in, so a stale mask from an older React build cannot grant a permission that no longer
exists.

**`SPAWN_CAP` is evaluated before an entity exists.** It is answered from entity type, world name and spawn
reason only. A rule that also declares marker keys or scoreboard tags is **skipped entirely** for spawn
decisions, because there is nothing to read them off. Keep spawn rules separate from entity rules.

**Not every spawn decision carries a reason.** React consults the cap from four events, and only
`CreatureSpawnEvent` supplies a `SpawnReason`; the generic entity-spawn, player-item-drop and breeding paths
pass none. A rule that declares `spawnReasons` cannot match those three, so leave that dimension empty unless
you specifically mean creature spawns.

---

## Matching an entity

`ReactProtectionRule` is a record with five matcher dimensions. Within a rule, **every dimension you set must
match** (AND). Within one dimension, **any value matches** (OR). A dimension you leave empty matches
everything.

```java
public record ReactProtectionRule(
    String ruleId,
    int operations,
    Set<EntityType> entityTypes,
    Set<NamespacedKey> markerKeys,
    Set<String> scoreboardTags,
    Set<String> worldNames,
    Set<CreatureSpawnEvent.SpawnReason> spawnReasons)
```

| Dimension       | Builder                 | Matches when                                              | Cost                             |
|-----------------|-------------------------|-----------------------------------------------------------|----------------------------------|
| Marker keys     | `withMarkerKeys(NamespacedKey…)` | the entity's `PersistentDataContainer` **has** any of the keys, at any data type | re-read on every check, never cached |
| Entity types    | `withEntityTypes(EntityType…)`   | `entity.getType()` is any of them                        | free                             |
| Scoreboard tags | `withScoreboardTags(String…)`    | `entity.getScoreboardTags()` contains any of them        | read once, then cached           |
| Worlds          | `withWorlds(String…)`            | the entity's world name equals any of them, case-insensitively | read once, then cached      |
| Spawn reasons   | `withSpawnReasons(SpawnReason…)` | the `CreatureSpawnEvent.SpawnReason` is any of them — **`SPAWN_CAP` only** | free                    |

Marker keys are matched on **presence**, not value or type. React calls `PersistentDataContainer#has(key)`
with no `PersistentDataType`, so a `BYTE`, an `INTEGER` or a `STRING` under that key all count.

Each `with…` method **replaces** its dimension rather than adding to it, and each is backed by `Set.of(…)`,
which rejects `null` elements and duplicate values with an exception. Pass each dimension once, with distinct
values.

### The common case: "this entity is mine, never touch it"

Stamp a key of your own onto anything you create, and declare one rule that matches it.

```java
package com.example.pets;

import art.arcane.react.api.protect.ReactOperations;
import art.arcane.react.api.protect.ReactProtectionProvider;
import art.arcane.react.api.protect.ReactProtectionRule;
import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;

import java.util.List;

public final class PetProtection implements ReactProtectionProvider {
    public static final String ID = "example-pets";

    private final NamespacedKey petKey;

    public PetProtection(Plugin plugin) {
        this.petKey = new NamespacedKey(plugin, "pet");
    }

    public NamespacedKey petKey() {
        return petKey;
    }

    @Override
    public String providerId() {
        return ID;
    }

    @Override
    public List<ReactProtectionRule> rules() {
        return List.of(ReactProtectionRule
            .of("pets", ReactOperations.all())
            .withMarkerKeys(petKey));
    }
}
```

Register it in `onEnable`. Bukkit unregisters you automatically when your plugin disables, and React notices
within one of its ticks.

```java
getServer().getServicesManager().register(
    ReactProtectionProvider.class, new PetProtection(this), this, ServicePriority.Normal);
```

Mark the entity when you spawn it. `PetProtection#petKey()` is the same `NamespacedKey` the rule matches on:

```java
public Wolf spawnPet(PetProtection protection, World world, Location at) {
    Wolf wolf = world.spawn(at, Wolf.class);
    wolf.getPersistentDataContainer().set(protection.petKey(), PersistentDataType.BYTE, (byte) 1);
    return wolf;
}
```

That is the whole integration. The mark is persistent data, so it survives chunk unloads, restarts and world
backups.

The **rule** does not. Bukkit unregisters your service when your plugin disables, and React drops your rules
on the next reconcile — so while your plugin is off, marked entities are unprotected even though the mark is
still on them, and protection comes back when you enable again. If you need protection that holds while your
plugin is disabled or uninstalled, write a claim instead (see
[Claiming a single entity](#claiming-a-single-entity)).

### A fuller provider

```java
package com.example.pets;

import art.arcane.react.api.protect.ReactOperation;
import art.arcane.react.api.protect.ReactOperations;
import art.arcane.react.api.protect.ReactProtectionProvider;
import art.arcane.react.api.protect.ReactProtectionRule;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.EntityType;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.plugin.Plugin;

import java.util.List;

public final class PetProtection implements ReactProtectionProvider {
    public static final String ID = "example-pets";

    private final NamespacedKey petKey;
    private final NamespacedKey summonKey;

    public PetProtection(Plugin plugin) {
        this.petKey = new NamespacedKey(plugin, "pet");
        this.summonKey = new NamespacedKey(plugin, "summon");
    }

    @Override
    public String providerId() {
        return ID;
    }

    @Override
    public List<ReactProtectionRule> rules() {
        ReactProtectionRule pets = ReactProtectionRule
            .of("pets", ReactOperations.all())
            .withMarkerKeys(petKey);

        ReactProtectionRule summons = ReactProtectionRule
            .of("summons", ReactOperation.STACK, ReactOperation.TRIM, ReactOperation.DESPAWN)
            .withMarkerKeys(summonKey)
            .withEntityTypes(EntityType.SKELETON, EntityType.ZOMBIE);

        ReactProtectionRule arena = ReactProtectionRule
            .of("arena-summons", ReactOperation.SPAWN_CAP)
            .withEntityTypes(EntityType.SKELETON)
            .withWorlds("arena")
            .withSpawnReasons(CreatureSpawnEvent.SpawnReason.CUSTOM);

        return List.of(pets, summons, arena);
    }
}
```

`summons` still allows `PURGE` and `SLEEP`: an operator sweep can clear them, and idle ones stop thinking.
`arena` carries no marker key on purpose — a spawn rule that declares one never matches.

---

## The lifecycle

```
you register a ReactProtectionProvider with the ServicesManager
   |
   |  React marks itself dirty on ServiceRegisterEvent
   v
rules() is called once per reconcile, on a React worker thread
   |
   |  refused if providerId() is blank or throws
   |  faulted if rules() throws or returns null - last good rules stay in force
   v
rules are compiled: ids qualified as "providerId/ruleId", masks sanitized,
worlds lowercased, duplicates dropped, capped at 64 rules per provider
   |
   |  if the compiled set differs from the previous one, the entity mask cache is flushed
   v
React evaluates the compiled set per entity, on its own threads
```

Reconcile timing:

- On startup, once, after every controller has started.
- Whenever a `ReactProtectionProvider` service is registered or unregistered, or any plugin is disabled —
  React ticks its protection controller every 5 seconds and reconciles on the next tick.
- Otherwise every 30 seconds regardless, so a provider whose rules change shape at runtime is picked up
  without re-registering.

Only the startup reconcile runs on the thread that enabled React. Every later one runs on a React worker
thread, so `rules()` must not read entities, worlds, chunks or anything else the server owns — return a list
you built earlier.

`rules()` is therefore called repeatedly and must be cheap and side-effect free. Build the list once in your
constructor and return the same instance if it never changes.

### The mask cache

React caches a per-entity bitmask keyed by entity UUID. Understanding what is and is not cached is the
difference between a rule that works and a rule that appears to work.

| Fact                        | Freshness                                                                 |
|-----------------------------|---------------------------------------------------------------------------|
| Marker keys                 | **Live.** Re-read on every check as long as any compiled rule uses marker keys. Add or remove the key and the next check sees it. |
| Entity type, world          | Cached on first read                                                       |
| Scoreboard tags             | Cached on first read — adding a tag later is **not** seen                  |
| Claims written by `ReactProtection.protect` / `release` | Cached, and the cache is invalidated for you   |
| Claims you write into persistent data yourself | Cached — **not** seen until the cache entry goes    |

A cache entry goes when: you call `ReactProtection.invalidate(entity)`, the compiled rule set changes, the
entity is entered again through a spawn or chunk-entities-load event, or the entry ages out. React sweeps
every 30 seconds and evicts entries older than 5 minutes.

So: **if you change anything other than a marker key, call `ReactProtection.invalidate(entity)`.** It is
cheap — one map removal — and safe to call when React is absent.

```java
wolf.addScoreboardTag("example-pet");
ReactProtection.invalidate(wolf);
```

---

## Threading

Every read and write goes through the entity's `PersistentDataContainer`, its world, or its scoreboard tags.
On Folia that state belongs to the region that owns the entity, and React refuses to touch it from anywhere
else.

| Call                                      | Where you may call it                                                   |
|-------------------------------------------|--------------------------------------------------------------------------|
| `ReactProtection.protect` / `release`     | The thread that owns the entity: the main thread on Paper, the owning region thread on Folia. On Folia, anywhere else it returns `false`, logs at verbose level and writes nothing |
| `ReactProtection.isProtected` / `operationsFor` / `ownerOf` | Same rule, with a **silent** failure mode on Folia: off the owning region you get the cached mask if there is one, otherwise `ReactOperations.NONE` and an empty owner string, and nothing is cached |
| `ReactProtection.invalidate`              | Any thread. It only removes a map entry and never reads entity state    |
| `ReactProtection.available()`             | Any thread                                                               |
| `ReactProtectionProvider.rules()`         | Called by React on one of its own worker threads, **not** the server thread. Do not touch Bukkit state from it, do not block, do not do I/O, do not schedule and wait |
| `ReactEntityGuardEvent` listener          | Called on whichever React thread is performing the operation — see below |

**Only Folia enforces the rule.** React's check is "is this a Folia runtime, and if so does the current region
own this entity". On Paper and Spigot the answer is always yes, so an off-main-thread `protect`, `release` or
`isProtected` is **not** refused, **not** logged, and reaches the entity's `PersistentDataContainer` anyway.
Nothing tells you it happened. Getting the thread right on Paper is entirely yours; React's refusal is a
Folia backstop, not a portable guard.

`ReactProtection.operationsFor` returning `NONE` off-region on Folia is the single most common way to be
confused by this API. It looks exactly like "nothing protects this entity".

The cheapest fix is to read where you already own the entity — inside an entity event — and keep the answer:

```java
package com.example.pets;

import art.arcane.react.api.protect.ReactOperation;
import art.arcane.react.api.protect.ReactProtection;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntitySpawnEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PetAudit implements Listener {
    private final Map<UUID, Boolean> trimProtected = new ConcurrentHashMap<>();

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSpawn(EntitySpawnEvent event) {
        trimProtected.put(
            event.getEntity().getUniqueId(),
            ReactProtection.isProtected(event.getEntity(), ReactOperation.TRIM));
    }
}
```

If you must read from a task, dispatch that task to the entity's owner first — `Entity#getScheduler()` on
Paper and Folia, `BukkitScheduler#runTask` on Spigot — and call from inside it. `Entity#getScheduler()` is not
on a plain Spigot compile classpath, which is why it is named here rather than shown as code.

---

## Claiming a single entity

When you have the entity in hand and no useful matcher, write a claim.

```java
boolean claimed = ReactProtection.protect(entity, this, ReactOperations.all());
boolean released = ReactProtection.release(entity, this);
```

The full facade:

```java
public static boolean available();
public static boolean protect(Entity entity, Plugin owner, ReactOperation... operations);
public static boolean protect(Entity entity, Plugin owner, int operations);
public static boolean release(Entity entity, Plugin owner);
public static boolean invalidate(Entity entity);
public static boolean isProtected(Entity entity, ReactOperation operation);
public static int operationsFor(Entity entity);
public static String ownerOf(Entity entity);
```

What a claim actually is: an `INTEGER` written into the entity's persistent data under
`react:protect-<your-plugin-name>`, holding your operation mask. Your plugin name is lowercased and every
character outside `[a-z0-9_.-]` becomes `_`, truncated to 48 characters.

Consequences worth knowing before you rely on it:

- **Claims persist with the entity.** They are saved to the world and survive restarts, chunk unloads and
  your plugin being uninstalled. Nothing cleans them up but you.
- **Claims union across owners.** Two plugins claiming the same entity give it the OR of both masks.
  `release` only clears your own key; the other plugin's protection stands.
- **`protect` unions with your own previous claim.** Calling it twice with different masks gives the entity
  both. To narrow a claim, `release` then `protect` again.
- **`ownerOf(entity)` returns the claim owners**, comma-separated and sorted — `"plugina,pluginb"`. It is
  empty for protection that came from a rule; rules are not claims and there is nobody to name.
- **Rule-derived protection cannot be released.** `release` only removes claims.
- **A mask of `ReactOperations.NONE`, a `null` entity or a `null` owner is refused** and returns `false`.

`operationsFor` returns the union of everything — rules and claims — as a bitmask. Feed it to
`ReactOperations.expand` when you want to show it to a human.

---

## Vetoing per entity with an event

`ReactEntityGuardEvent` is the one place React asks per entity, and it is deliberately narrow.

```java
public final class ReactEntityGuardEvent extends Event implements Cancellable {
    public ReactEntityGuardEvent(Entity entity, ReactOperation operation, boolean async);
    public Entity getEntity();
    public ReactOperation getOperation();
    public boolean isCancelled();
    public void setCancelled(boolean cancel);
    public HandlerList getHandlers();
    public static HandlerList getHandlerList();
}
```

```java
@EventHandler(ignoreCancelled = true)
public void onGuard(ReactEntityGuardEvent event) {
    if (index.isPet(event.getEntity())) {
        event.setCancelled(true);
    }
}
```

Cancelling means "do not perform this operation on this entity". React skips it and moves on.

**Limitations, stated plainly:**

- **It fires for `TRIM` and `PURGE` only.** Stacking, sleeping, despawning and spawn caps consult the
  compiled rule set directly and never fire an event. There is no per-entity veto for those four.
- **Not every `PURGE` fires it.** Only the operator purge action does. The hot-chunk quarantine action and
  the entity-crowd tweak call the compiled rule set directly, so an entity they delete is never offered to
  your handler. `TRIM` fires from both of its paths. Rules and claims cover every path; the event does not.
- **It only fires at the moment of removal**, after React has already selected the entity as a candidate.
  Rule-based and claim-based protection is checked earlier and removes the entity from consideration
  entirely, which is cheaper.
- **It does not fire at all when nobody is listening.** React checks the `HandlerList` first.
- **The `async` flag is computed, not fixed.** React sets it from whether the calling thread is a server tick
  thread. On Folia the trim and purge paths run on region threads, which count as tick threads, so the event
  is usually synchronous — but you must not assume it. Treat the event as owning only the entity it names,
  and do not touch other entities, other chunks or other worlds from the handler.
- **A handler that throws is a veto.** React catches `Throwable`, logs it, and treats the operation as
  refused. That is the opposite of the usual Bukkit behaviour, and it is deliberate: a broken listener must
  not cause deletions.
- **Re-entrancy is short-circuited.** If your handler causes React to evaluate another guard on the same
  thread, the nested call returns "allowed" without firing a second event.

The event has its own `HandlerList` and does not extend `ReactEvent` or `ReactCancellableEvent`, so
registering for it gets you this event and nothing else.

---

## Relationship to the older `StackExclusion` flag

VolmLib ships `art.arcane.volmlib.util.entity.StackExclusion`, which writes a `BYTE` under
`volmit:no-stack` into an entity's persistent data. Plugins in this suite used it to opt out of mob stacking
before React had an API.

**It still works, exactly.** React ships a built-in rule — `react/legacy-no-stack` — that matches the marker
key `volmit:no-stack` and grants `STACK`, `TRIM`, `PURGE`, `SLEEP` and `DESPAWN`. It is compiled with every
third-party rule and evaluated on the same path. Nothing you already wrote needs to change.

What it does not do:

- **No `SPAWN_CAP`.** The legacy flag lives on an entity that already exists, so it can never answer a
  spawn-time question.
- **No per-operation control.** It is one flag granting five operations. You cannot say "stack is fine, do
  not trim".
- **No owner.** `ReactProtection.ownerOf` returns an empty string for a legacy-flagged entity, so an operator
  cannot tell which plugin asked for it.
- **It is a VolmLib type.** Inside React's jar VolmLib is relocated, so the class you call is your copy, not
  React's. React only ever sees the resulting `NamespacedKey`.

**Which to use now:** for new work, `ReactProtection.protect(entity, plugin, ops)` or a rule with your own
marker key. Both name your plugin, both let you pick operations, and neither needs VolmLib. Keep
`StackExclusion` only where it is already deployed; there is no migration deadline and no plan to drop the
built-in rule.

If you want the legacy behaviour without a VolmLib dependency, the key is stable and you can write it
yourself:

```java
entity.getPersistentDataContainer().set(
    new NamespacedKey("volmit", "no-stack"), PersistentDataType.BYTE, (byte) 1);
```

---

## Failure policy

React assumes a provider will throw, return null, hand back a hostile collection, or register twice.

| Misbehaviour                                   | What React does                                                                    |
|------------------------------------------------|-------------------------------------------------------------------------------------|
| `providerId()` throws                          | Registration refused for this cycle, logged. `rules()` is never called               |
| `providerId()` is blank after trimming         | Registration refused for this cycle, logged                                          |
| `providerId()` is a lambda's synthetic name    | Accepted, with one warning naming your plugin. The name changes every restart, so an operator cannot recognise it — implement `providerId()` |
| `rules()` throws                               | Counted as a fault, logged with the exception type. Your **last known good rules stay in force** |
| `rules()` returns `null`                       | Counted as a fault. Last known good rules stay in force                              |
| The returned list's `iterator()` throws        | Counted as a fault. React only iterates; it never calls `size()`, so a hostile `size()` is harmless |
| The list contains `null` elements              | Skipped silently                                                                     |
| More than 64 rules                             | The first 64 are taken, the rest ignored with one log line                            |
| An endless list                                | Drained no further than 64 entries                                                   |
| A rule with no operations, or a blank `ruleId` | Dropped silently at compile time                                                     |
| Two rules with the same `providerId/ruleId`    | The first wins, the rest are dropped silently                                        |
| `rules()` takes 5 ms or more                   | One warning per provider naming your plugin. Never changes the outcome               |
| 5 faults from one provider                     | The provider is **quarantined**                                                      |

**Quarantine is permanent for the React session.** A quarantined provider's last known good rules stay
compiled and keep protecting entities, but React stops calling `rules()` on it and will not pick up any
change. Re-registering the same `providerId` does not clear it. What clears it is `/react reload`, which
rebuilds React's controllers from scratch, or a server restart. Registering under a different `providerId`
also works, since quarantine is keyed by id.

`ReactProtection` itself never throws. Every method is null-tolerant and returns a neutral value when React's
runtime is not installed.

There is no fail-open/fail-closed switch. A fault leaves your previous rules in place rather than dropping
protection, which is the conservative choice: a stale rule over-protects, and over-protection costs entity
count, not player data.

---

## Configuration

The protection API has no configuration of its own. It is always on, cannot be disabled, and its limits —
64 rules per provider, 5 faults before quarantine, a 5 ms slow warning, a 5-minute mask retention — are
fixed.

Two things an operator can change do affect what you see:

| File                              | Key       | Effect                                                                            |
|-----------------------------------|-----------|-----------------------------------------------------------------------------------|
| `plugins/React/config.toml`       | `verbose` | `false` by default. Every `[protect]` diagnostic that concerns your provider — refusals, faults, quarantine, slow-provider warnings, off-region write refusals — is verbose-level. Turn it on when your rules are not taking effect |
| `plugins/React/feature/*.toml`, `plugins/React/tweak/*.toml` | `enabled` | Turning off the feature that performs an operation removes that operation from the server entirely |

Which file performs which operation, if you need to reproduce a report:

| Operation   | Config files                                                                                              |
|-------------|-----------------------------------------------------------------------------------------------------------|
| `STACK`     | `feature/mob-stacking.toml`                                                                                |
| `TRIM`      | `feature/entity-trimmer.toml`, `action/action-trim-entities-by-age-priority.toml`                           |
| `PURGE`     | `action/purge-entities.toml`, `action/action-quarantine-hot-chunks.toml`, `tweak/entity-crowd-prevention.toml` |
| `SLEEP`     | `feature/adaptive-entity-sleep.toml`, `feature/dynamic-activation-range.toml`                               |
| `DESPAWN`   | `tweak/fast-entity-incineration.toml`, `tweak/entity-bubbler.toml`                                          |
| `SPAWN_CAP` | `tweak/entity-hardstop.toml`                                                                                |

---

## Enum reference

`ReactOperation` — `STACK`, `TRIM`, `PURGE`, `SLEEP`, `DESPAWN`, `SPAWN_CAP`. Ordinals are the bit positions
in a `ReactOperations` mask, so a mask is not portable across React versions if constants are reordered.
Never persist a raw mask outside React's own claims; store the `ReactOperation` names instead.

The enum may gain constants. Write a `default` arm in any `switch` expression over it:

```java
String verb = switch (event.getOperation()) {
    case TRIM, PURGE -> "delete";
    case STACK -> "merge";
    default -> "touch";
};
```

`ReactOperations.all()` is computed from the enum at class-init, so it automatically covers constants added
in a later release — which is what you want when your intent is "never touch this".
