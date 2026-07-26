# React plugin API

React is a runtime performance plugin: it samples the server, stacks mobs, trims and purges entities, sleeps
idle AI, caps spawns, and renders all of it onto maps and monitors. Two of those behaviours affect other
plugins, so two of them are public API — **telling React to leave your entities alone**, and **putting your
own numbers into React's dashboards**. A third surface, PlaceholderAPI, is read-only and needs no code.

| You want to…                                            | Read                                 |
|---------------------------------------------------------|--------------------------------------|
| stop React stacking, trimming, purging, sleeping or despawning your entities | [protection.md](protection.md) |
| show your plugin's numbers on React's monitors, maps and web dashboard | [metrics.md](metrics.md) |
| print React's numbers in a scoreboard, hologram or chat format | [placeholders.md](placeholders.md) |

Everything documented in those three files is built from Bukkit types, `java.*` types and React's own API
types only. No VolmLib, no Adventure, no Guava, no shaded types — so the API links against a plain Spigot or
Paper compile classpath and keeps working across React builds.

---

## Depending on React

React ships a `plugin.yml`, so it participates in the legacy Bukkit plugin classloader chain. Declaring a
dependency is all it takes to see `art.arcane.react.api.*`.

Bukkit plugin (`plugin.yml`):

```yaml
softdepend: [React]
```

Paper plugin (`paper-plugin.yml`):

```yaml
dependencies:
  server:
    React:
      load: BEFORE
      required: false
      join-classpath: true
```

`join-classpath: true` is mandatory on Paper. Paper plugin classloaders are isolated, and without it you get
`NoClassDefFoundError` on `art.arcane.react.api.*` even though the classes ship unrelocated.

### Compile classpath

React does not publish a Maven artifact. Compile against the plugin jar you already deploy:

```gradle
dependencies {
    compileOnly files('libs/React-2.0.0-26.2.jar')
}
```

Maven has no first-class equivalent of a file dependency; install the jar into your local repository under
coordinates you choose and depend on it with `<scope>provided</scope>`.

Scope is compile-only in every case — the jar must not end up inside yours. Never shade React classes into
your plugin: two copies of `ReactProtection` means your calls reach a facade with no binding installed, and
every call silently returns `false` with no error anywhere.

The version suffix tracks the Minecraft API version React was built against (`2.0.0-26.2` is React 2.0.0 for
Minecraft 26.2). The API packages themselves do not change with it.

---

## What relocation means for you

React's shaded jar rewrites these packages at build time:

| Original            | Inside the React jar                        |
|---------------------|---------------------------------------------|
| `art.arcane.volmlib` | `art.arcane.react.util.arcane.volmlib`     |
| `art.arcane.chrono`  | `art.arcane.react.util.arcane.chrono`      |
| `art.arcane.curse`   | `art.arcane.react.util.arcane.curse`       |
| `art.arcane.multiburst` | `art.arcane.react.util.arcane.multiburst` |
| `net.bytebuddy`      | `art.arcane.react.util.arcane.bytebuddy`   |
| `io.github.slimjar`  | `art.arcane.react.util.arcane.slimjar`     |

Three consequences:

- **You never need VolmLib to use React's API.** No public API type takes or returns one. If you depend on
  VolmLib yourself, your copy and React's copy are different classes with different names and cannot
  collide.
- **You cannot hand a VolmLib object to React**, and React cannot hand you one. Anything that looks like it
  should cross that line is internal.
- **Reflection into React by original package name fails.** `Class.forName("art.arcane.volmlib.…")` will not
  find React's copy. Nothing in the documented API requires reflection.

`art.arcane.react.api.protect` and `art.arcane.react.api.metric` are **not** relocated and never will be.
That is enforced by a test in React's own build, which fails if any public member of those two packages
mentions a relocated, shaded or Adventure type.

---

## Detecting React at runtime

Both facades are static and inert. `ReactProtection` and `ReactMetrics` return `false`, `0`, `""`, an empty
set — or `Double.NaN` from `readHostMetric` — when React's runtime is not installed, and never touch your
entity in that state.

```java
if (ReactProtection.available()) {
    ReactProtection.protect(entity, this, ReactOperations.all());
}
```

That covers React being **installed but not started** — during your `onEnable` if you load first, during a
`/react reload`, or after React shut itself down.

It does **not** cover React being absent from the server entirely. In that case the classes do not exist, and
the JVM throws `NoClassDefFoundError` the moment it links a method of yours that mentions one. With a
`softdepend`, keep every React-touching statement inside its own class and only load that class after
checking:

```java
@Override
public void onEnable() {
    if (getServer().getPluginManager().isPluginEnabled("React")) {
        new ReactBridge(this).install();
    }
}
```

`ReactBridge` is your class; it is the only one that imports `art.arcane.react.api.*`.

---

## What is not API

Only `art.arcane.react.api.protect` and `art.arcane.react.api.metric` are contracts. Everything else under
`art.arcane.react` is React's own runtime and changes without notice:

- **Any package named `internal`.** `api.protect.internal` and `api.metric.internal` hold the binding that
  React installs into the facade at startup. They are public only because React installs them from another
  package. Do not import them: a test in React's build asserts that no published API type can reach the
  binding, and code that reaches around it will break.
- **`art.arcane.react.api.sampler`.** `Sampler`, `ReactCachedSampler`, `ReactCachedRateSampler` and
  `ReactTickedSampler` are React's internal measurement types. They extend React's registry and map-renderer
  interfaces and their members use relocated and shaded types, so a third-party plugin cannot implement or
  extend them at all. Publish a metric instead — React builds the sampler for you. See
  [metrics.md](metrics.md).
- **`api.feature`, `api.tweak`, `api.action`, `api.monitor`, `api.rendering`, `api.entity`, `api.benchmark`,
  `api.test`, `api.web`.** React's own content model, dashboards, HTTP layer and self-test harness. Same
  problem: they are wired to React's registries and shaded dependencies.
- **`art.arcane.react.api.event`.** `ReactEvent` and `ReactCancellableEvent` are base classes that hold the
  `HandlerList` for every subclass, so all of React's internal layer events funnel through two shared lists,
  and some of them are fired every tick from a reused instance. `ReactEntityGuardEvent` deliberately does
  **not** extend either of them and owns its own `HandlerList`; it is the only React event a third party
  should register for.

---

## Versioning

`ReactOperation`, `ReactMetricKind` and every other enum in the API may gain constants in a future release. A
`switch` **expression** over an enum is exhaustive, so it stops compiling — and throws
`IncompatibleClassChangeError` on an already-compiled jar — the moment a constant is added.

Always write a `default` arm in third-party code:

```java
String verb = switch (event.getOperation()) {
    case STACK -> "stack";
    case TRIM, PURGE -> "delete";
    default -> "touch";
};
```

Records in the API (`ReactProtectionRule`, `ReactMetric`) may gain components. Build them through the static
factories and `with…` methods rather than the canonical constructor, and a new component will not break your
source.
