package art.arcane.react.localization.catalog;

import art.arcane.react.localization.ReactLanguage;
import art.arcane.volmlib.util.localization.MessageArgument;
import art.arcane.volmlib.util.localization.MessageCatalog;
import art.arcane.volmlib.util.localization.TextKey;

import java.util.Map;

public final class MapMessages {
  private static final Map<String, TextKey> RENDERER_SUMMARIES = Map.ofEntries(
      rendererSummaryEntry("chunk-sampler-map", "Chunk heatmap of React observer cost around your current position."),
      rendererSummaryEntry("entity-pressure-heatmap", "Chunk heatmap of entity pressure; hotter chunks carry heavier mob or item load."),
      rendererSummaryEntry("redstone-activity-heatmap", "Chunk heatmap of redstone update pressure and clock-heavy circuitry."),
      rendererSummaryEntry("chunk-load-gen-cost-map", "Chunk heatmap of load and generation cost using load/gen rate and ms pressure."),
      rendererSummaryEntry("hopper-container-throughput-map", "Chunk heatmap of hopper transfer throughput to locate transport bottlenecks."),
      rendererSummaryEntry("player-impact-overlay", "Composite pressure map with per-player markers showing likely world impact."),
      rendererSummaryEntry("tick-spike-origin-replay-map", "Replay map of recent spike origins with decaying heat over nearby chunks."),
      rendererSummaryEntry("iris-generation-pressure-overlay", "Iris-aware pressure overlay using pregen queue and chunk stream latency."),
      rendererSummaryEntry("adapt-runtime-pressure-overlay", "Adapt-aware pressure overlay using session load and ability operation rate."),
      rendererSummaryEntry("adapt-ability-impact-list-map", "Ranks Adapt abilities by measured event, tick, and guarded-callback execution time."),
      rendererSummaryEntry("iris-world-chunk-share-pie-map", "Pie chart showing loaded chunk share across Iris-managed worlds."),
      rendererSummaryEntry("iris-biome-chunk-share-pie-map", "Pie chart showing chunk-share distribution across biomes in the map world."),
      rendererSummaryEntry("plugin-event-impact-pie-map", "Pie chart showing rolling plugin event-impact share over time."),
      rendererSummaryEntry("plugin-event-impact-list-map", "Ranked list of plugins by rolling event-impact score."),
      rendererSummaryEntry("iris-metrics", "Iris integration overview for engines, worlds, chunks, entities, and generation."),
      rendererSummaryEntry("adapt-metrics", "Adapt integration panel for session load, ability ops/min, and world policy latency."),
      rendererSummaryEntry("react-metrics", "React local metrics panel for TPS, tick latency, incident pressure, and queue health."),
      rendererSummaryEntry("unknown", "Fallback renderer shown when a specific monitor cannot be resolved.")
  );
  private static final Map<String, TextKey> RENDERER_DETAILS = Map.ofEntries(
      rendererDetail("chunk-sampler-map", "Hotter colors indicate more pressure in the sampled chunk."),
      rendererDetail("entity-pressure-heatmap", "Hotter colors indicate more pressure in the sampled chunk."),
      rendererDetail("redstone-activity-heatmap", "Hotter colors indicate more pressure in the sampled chunk."),
      rendererDetail("chunk-load-gen-cost-map", "Hotter colors indicate more pressure in the sampled chunk."),
      rendererDetail("hopper-container-throughput-map", "Hotter colors indicate more pressure in the sampled chunk."),
      rendererDetail("player-impact-overlay", "Hotter colors indicate more pressure in the sampled chunk."),
      rendererDetail("tick-spike-origin-replay-map", "Hotter colors indicate more pressure in the sampled chunk."),
      rendererDetail("iris-generation-pressure-overlay", "Hotter colors indicate more pressure in the sampled chunk."),
      rendererDetail("adapt-runtime-pressure-overlay", "Hotter colors indicate more pressure in the sampled chunk."),
      rendererDetail("iris-metrics", "Monitors: managed worlds, live engines, loaded chunks, entities, generation rate, and failures."),
      rendererDetail("adapt-metrics", "Monitors: session load, ability throughput, policy latency."),
      rendererDetail("react-metrics", "Monitors: TPS, tick ms, incident score, jobs queue, scheduler backlog."),
      rendererDetail("iris-world-chunk-share-pie-map", "Each slice represents the percent of loaded chunks per world."),
      rendererDetail("iris-biome-chunk-share-pie-map", "Each slice represents the percent of loaded chunks per biome (Iris custom names when available)."),
      rendererDetail("plugin-event-impact-pie-map", "Each slice represents the rolling share of plugin event cost over recent windows."),
      rendererDetail("plugin-event-impact-list-map", "Rows are ordered from highest rolling plugin impact to lowest."),
      rendererDetail("adapt-ability-impact-list-map", "Rows rank rolling 60-second measured callback cost; cb/m counts covered callback executions, while directly scheduled background work is outside this map.")
  );
  private static final Map<String, TextKey> SAMPLER_SUMMARIES = Map.ofEntries(
      samplerSummary("tick-time", "Average server MSPT (milliseconds per tick) across recent samples."),
      samplerSummary("ticks-per-second", "Effective TPS trend from the live server tick loop."),
      samplerSummary("tick-ms-p50", "Median tick time to show normal baseline server load."),
      samplerSummary("tick-ms-p95", "95th percentile tick time to expose high-tail latency spikes."),
      samplerSummary("tick-ms-p99", "99th percentile tick time for worst-case tick latency outliers."),
      samplerSummary("tick-spike-rate", "Rate of severe tick spikes crossing critical tick-time thresholds."),
      samplerSummary("incident-score", "Composite incident score built from multiple pressure samplers."),
      samplerSummary("memory-used", "Current JVM heap memory usage."),
      samplerSummary("memory-free", "Available JVM heap memory before further growth."),
      samplerSummary("memory-pressure", "Heap pressure ratio used by React to detect memory saturation."),
      samplerSummary("memory-garbage", "Estimated reclaimable heap memory pending garbage collection."),
      samplerSummary("memory-used-after-gc", "Post-GC heap footprint trend after recent collections."),
      samplerSummary("gc-time-percent", "Percent of time spent in GC relative to runtime."),
      samplerSummary("gc-pause-p95", "95th percentile garbage collection pause duration."),
      samplerSummary("player-ping-p95", "95th percentile player ping to reveal network latency tails."),
      samplerSummary("ping-jitter", "Player ping jitter showing latency instability over time."),
      samplerSummary("players", "Online player count over time."),
      samplerSummary("entities", "Tracked loaded-entity pressure across active chunks."),
      samplerSummary("entities-spawns", "Entity spawn rate observed from creature spawn events."),
      samplerSummary("entity-ai-active-count", "Approximate count of entities actively running AI logic."),
      samplerSummary("chunks", "Loaded chunk count trend."),
      samplerSummary("chunks-loaded", "Chunk-load event rate."),
      samplerSummary("chunks-generated", "Chunk-generation event rate."),
      samplerSummary("chunk-load-ms", "Chunk load latency in milliseconds."),
      samplerSummary("chunk-gen-ms", "Chunk generation latency in milliseconds."),
      samplerSummary("top-chunk-cost", "Highest observed per-chunk cost from React chunk sampling."),
      samplerSummary("top-world-mspt", "Worst world-level MSPT observed across loaded worlds."),
      samplerSummary("per-world-tick-time", "Worst per-world rolling tick share used by the per-world tick budget hysteresis layer."),
      samplerSummary("redstone", "Redstone update activity rate."),
      samplerSummary("redstone-burst-rate", "Burst intensity of redstone transitions over short windows."),
      samplerSummary("redstone-tick-time", "Tick time spent inside redstone processing paths."),
      samplerSummary("hopper", "Hopper transfer/update activity rate."),
      samplerSummary("hopper-tick-time", "Tick time spent in hopper processing paths."),
      samplerSummary("fluid", "Fluid update activity rate."),
      samplerSummary("fluid-tick-time", "Tick time spent in fluid simulation paths."),
      samplerSummary("physics", "Physics update activity rate."),
      samplerSummary("physics-tick-time", "Tick time spent in physics simulation paths."),
      samplerSummary("event-time", "Total time spent processing server events."),
      samplerSummary("event-handles-per-tick", "Event-handler invocation count per tick."),
      samplerSummary("events-listeners", "Registered event-listener count on the server."),
      samplerSummary("scheduler-backlog", "Scheduler backlog depth waiting for execution."),
      samplerSummary("backlog-growth-rate", "Rate at which scheduler backlog is growing or shrinking."),
      samplerSummary("react-jobs-queue", "Queued React jobs waiting to be processed."),
      samplerSummary("react-job-budget", "Remaining React job budget available per processing cycle."),
      samplerSummary("react-job-queue-time", "Time jobs spend waiting in React queues."),
      samplerSummary("react-sync-tick-time", "Time React spends on synchronous work per tick."),
      samplerSummary("react-async-tick-time", "Time React spends on asynchronous work per cycle."),
      samplerSummary("processor-system-load", "Host system CPU load trend."),
      samplerSummary("processor-process-load", "JVM process CPU load trend."),
      samplerSummary("processor-outside", "Non-React external process load pressure."),
      samplerSummary("iris-pregen-queue", "Iris remote pregen queue depth."),
      samplerSummary("iris-pregen-throughput", "Iris pregenerator throughput in chunks per second."),
      samplerSummary("iris-generation-total-ms", "Worst active Iris-world rolling generation time."),
      samplerSummary("iris-chunks-per-second", "Combined Iris engine chunk generation throughput."),
      samplerSummary("adapt-session-load", "Adapt runtime session load percentage."),
      samplerSummary("adapt-ability-ops", "Adapt ability operations per minute (mode selected by config adaptAbilityOpsMetricMode)."),
      samplerSummary("adapt-ability-checks-per-tick", "Adapt all ability checks averaged per tick over the telemetry window."),
      samplerSummary("adapt-world-policy-latency", "Adapt world-policy evaluation latency."),
      samplerSummary("crop-fast-forward", "Crop growth work fast-forwarded by React's farm optimization."),
      samplerSummary("explosion-packet-reduction", "Explosion packet volume reduced before delivery to nearby players."),
      samplerSummary("hopper-chain-coalescing", "Repeated hopper-chain operations coalesced into cheaper work."),
      samplerSummary("lazy-gravity-skipped", "Falling-block movement ticks projected as skippable by lazy gravity."),
      samplerSummary("pdc-write-batcher", "Persistent-data writes combined by React's write batcher."),
      samplerSummary("spawner-light-cache-skipped", "Repeated spawner light checks served from the short-lived cache."),
      samplerSummary("world-save-duration", "Duration of recent world-save operations."),
      samplerSummary("wormholes-block-changes", "Wormholes projected block changes published through the integration bridge."),
      samplerSummary("wormholes-packets", "Wormholes projection packet throughput published through the integration bridge."),
      samplerSummary("wormholes-portals", "Active Wormholes portal count published through the integration bridge."),
      samplerSummary("wormholes-projection-observers", "Players currently observing Wormholes projections."),
      samplerSummary("wormholes-projection-render-ms", "Wormholes projection rendering time."),
      samplerSummary("wormholes-projections-active", "Active Wormholes projections."),
      samplerSummary("wormholes-spoofed-entities", "Client-side entities currently projected by Wormholes."),
      samplerSummary("wormholes-traversals", "Wormholes portal traversal rate."),
      samplerSummary("unknown", "Fallback sampler used when a configured monitor cannot be resolved.")
  );
  private static final Map<String, TextKey> SAMPLER_NAMES = Map.ofEntries(
      samplerName("adapt-ability-checks-per-tick", "Adapt Ability Checks Per Tick"),
      samplerName("adapt-ability-ops", "Adapt Ability Ops"),
      samplerName("adapt-session-load", "Adapt Session Load"),
      samplerName("adapt-world-policy-latency", "Adapt World Policy Latency"),
      samplerName("backlog-growth-rate", "Backlog Growth Rate"),
      samplerName("chunk-gen-ms", "Chunk Gen Ms"),
      samplerName("chunk-load-ms", "Chunk Load Ms"),
      samplerName("chunks", "Chunks"),
      samplerName("chunks-generated", "Chunks Generated"),
      samplerName("chunks-loaded", "Chunks Loaded"),
      samplerName("crop-fast-forward", "Crop Fast Forward"),
      samplerName("entities", "Entities"),
      samplerName("entity-ai-active-count", "Entity Ai Active Count"),
      samplerName("entities-spawns", "Entities Spawns"),
      samplerName("event-handles-per-tick", "Event Handles Per Tick"),
      samplerName("events-listeners", "Events Listeners"),
      samplerName("event-time", "Event Time"),
      samplerName("explosion-packet-reduction", "Explosion Packet Reduction"),
      samplerName("fluid-tick-time", "Fluid Tick Time"),
      samplerName("fluid", "Fluid"),
      samplerName("gc-pause-p95", "Gc Pause P95"),
      samplerName("gc-time-percent", "Gc Time Percent"),
      samplerName("hopper-chain-coalescing", "Hopper Chain Coalescing"),
      samplerName("hopper-tick-time", "Hopper Tick Time"),
      samplerName("hopper", "Hopper"),
      samplerName("incident-score", "Incident Score"),
      samplerName("iris-chunks-per-second", "Iris Chunks Per Second"),
      samplerName("iris-generation-total-ms", "Iris Generation Total Ms"),
      samplerName("iris-pregen-queue", "Iris Pregen Queue"),
      samplerName("iris-pregen-throughput", "Iris Pregen Throughput"),
      samplerName("lazy-gravity-skipped", "Lazy Gravity Skipped"),
      samplerName("memory-free", "Memory Free"),
      samplerName("memory-garbage", "Memory Garbage"),
      samplerName("memory-pressure", "Memory Pressure"),
      samplerName("memory-used", "Memory Used"),
      samplerName("memory-used-after-gc", "Memory Used After Gc"),
      samplerName("pdc-write-batcher", "Pdc Write Batcher"),
      samplerName("per-world-tick-time", "Per World Tick Time"),
      samplerName("physics-tick-time", "Physics Tick Time"),
      samplerName("physics", "Physics"),
      samplerName("ping-jitter", "Ping Jitter"),
      samplerName("player-ping-p95", "Player Ping P95"),
      samplerName("players", "Players"),
      samplerName("processor-outside", "Processor Outside"),
      samplerName("processor-process-load", "Processor Process Load"),
      samplerName("processor-system-load", "Processor System Load"),
      samplerName("react-async-tick-time", "React Async Tick Time"),
      samplerName("react-job-budget", "React Job Budget"),
      samplerName("react-job-queue-time", "React Job Queue Time"),
      samplerName("react-jobs-queue", "React Jobs Queue"),
      samplerName("react-sync-tick-time", "React Sync Tick Time"),
      samplerName("redstone-burst-rate", "Redstone Burst Rate"),
      samplerName("redstone-tick-time", "Redstone Tick Time"),
      samplerName("redstone", "Redstone"),
      samplerName("scheduler-backlog", "Scheduler Backlog"),
      samplerName("spawner-light-cache-skipped", "Spawner Light Cache Skipped"),
      samplerName("tick-ms-p50", "Tick Ms P50"),
      samplerName("tick-ms-p95", "Tick Ms P95"),
      samplerName("tick-ms-p99", "Tick Ms P99"),
      samplerName("tick-spike-rate", "Tick Spike Rate"),
      samplerName("tick-time", "Tick Time"),
      samplerName("ticks-per-second", "Ticks Per Second"),
      samplerName("top-chunk-cost", "Top Chunk Cost"),
      samplerName("top-world-mspt", "Top World Mspt"),
      samplerName("unknown", "Unknown"),
      samplerName("world-save-duration", "World Save Duration"),
      samplerName("wormholes-block-changes", "Wormholes Block Changes"),
      samplerName("wormholes-packets", "Wormholes Packets"),
      samplerName("wormholes-portals", "Wormholes Portals"),
      samplerName("wormholes-projection-observers", "Wormholes Projection Observers"),
      samplerName("wormholes-projection-render-ms", "Wormholes Projection Render Ms"),
      samplerName("wormholes-projections-active", "Wormholes Projections Active"),
      samplerName("wormholes-spoofed-entities", "Wormholes Spoofed Entities"),
      samplerName("wormholes-traversals", "Wormholes Traversals")
  );
  private static final TextKey RENDERER_IRIS = TextKey.of("gui.map.summary.renderer.iris_fallback", "Iris integration renderer for {name}.");
  private static final TextKey RENDERER_ADAPT = TextKey.of("gui.map.summary.renderer.adapt_fallback", "Adapt integration renderer for {name}.");
  private static final TextKey RENDERER_REACT = TextKey.of("gui.map.summary.renderer.react_fallback", "React local metrics renderer for {name}.");
  private static final TextKey RENDERER_FALLBACK = TextKey.of("gui.map.summary.renderer.fallback", "Map renderer for {name}.");
  private static final TextKey SAMPLER_DETAIL = TextKey.of("gui.map.detail.sampler", "Live graph with current, minimum, and maximum values.");
  private static final TextKey SAMPLER_TICK = keywordSummary("tick", "Server tick-path timing and spike behavior.");
  private static final TextKey SAMPLER_MEMORY = keywordSummary("memory", "JVM memory pressure and garbage-collection behavior.");
  private static final TextKey SAMPLER_CHUNK = keywordSummary("chunk", "Chunk activity, latency, and load pressure.");
  private static final TextKey SAMPLER_ENTITY = keywordSummary("entity", "Entity population, spawn activity, or AI pressure.");
  private static final TextKey SAMPLER_PLAYER = keywordSummary("player", "Player activity and network latency quality.");
  private static final TextKey SAMPLER_REDSTONE = keywordSummary("redstone", "Redstone transition activity and processing cost.");
  private static final TextKey SAMPLER_HOPPER = keywordSummary("hopper", "Hopper transfer activity and processing cost.");
  private static final TextKey SAMPLER_FLUID = keywordSummary("fluid", "Fluid update activity and simulation cost.");
  private static final TextKey SAMPLER_PHYSICS = keywordSummary("physics", "Physics update activity and simulation cost.");
  private static final TextKey SAMPLER_EVENT = keywordSummary("event", "Server event volume and processing cost.");
  private static final TextKey SAMPLER_REACT = keywordSummary("react", "React scheduler queue depth and execution pressure.");
  private static final TextKey SAMPLER_PROCESSOR = keywordSummary("processor", "CPU and host load pressure around the server runtime.");
  private static final TextKey SAMPLER_IRIS = keywordSummary("iris", "Iris integration metric exposed through the remote sampler bridge.");
  private static final TextKey SAMPLER_ADAPT = keywordSummary("adapt", "Adapt integration metric exposed through the remote sampler bridge.");
  private static final TextKey SAMPLER_FALLBACK = keywordSummary("fallback", "Live sampler trend for this monitor.");

  private MapMessages() {
  }

  public static void addTo(MessageCatalog.Builder builder) {
    builder.addAll(RENDERER_SUMMARIES.values());
    builder.addAll(RENDERER_DETAILS.values());
    builder.addAll(SAMPLER_SUMMARIES.values());
    builder.addAll(SAMPLER_NAMES.values());
    builder.add(RENDERER_IRIS);
    builder.add(RENDERER_ADAPT);
    builder.add(RENDERER_REACT);
    builder.add(RENDERER_FALLBACK);
    builder.add(SAMPLER_DETAIL);
    builder.add(SAMPLER_TICK);
    builder.add(SAMPLER_MEMORY);
    builder.add(SAMPLER_CHUNK);
    builder.add(SAMPLER_ENTITY);
    builder.add(SAMPLER_PLAYER);
    builder.add(SAMPLER_REDSTONE);
    builder.add(SAMPLER_HOPPER);
    builder.add(SAMPLER_FLUID);
    builder.add(SAMPLER_PHYSICS);
    builder.add(SAMPLER_EVENT);
    builder.add(SAMPLER_REACT);
    builder.add(SAMPLER_PROCESSOR);
    builder.add(SAMPLER_IRIS);
    builder.add(SAMPLER_ADAPT);
    builder.add(SAMPLER_FALLBACK);
  }

  public static String rendererSummary(String normalizedId, String displayName) {
    TextKey exact = RENDERER_SUMMARIES.get(normalizedId);
    if (exact != null) {
      return ReactLanguage.plain(exact);
    }
    TextKey fallback = normalizedId.startsWith("iris-")
        ? RENDERER_IRIS
        : normalizedId.startsWith("adapt-")
        ? RENDERER_ADAPT
        : normalizedId.startsWith("react-")
        ? RENDERER_REACT
        : RENDERER_FALLBACK;
    return ReactLanguage.plain(fallback, MessageArgument.untrusted("name", displayName));
  }

  public static String rendererDetail(String normalizedId, boolean sampler) {
    if (sampler) {
      return ReactLanguage.plain(SAMPLER_DETAIL);
    }
    TextKey exact = RENDERER_DETAILS.get(normalizedId);
    return exact == null ? null : ReactLanguage.plain(exact);
  }

  public static String samplerSummary(String normalizedId) {
    TextKey exact = SAMPLER_SUMMARIES.get(normalizedId);
    if (exact != null) {
      return ReactLanguage.plain(exact);
    }
    return ReactLanguage.plain(samplerKeyword(normalizedId));
  }

  public static String localizedSamplerName(String normalizedId, String fallback) {
    TextKey exact = SAMPLER_NAMES.get(normalizedId);
    return exact == null ? fallback : ReactLanguage.plain(exact);
  }

  private static TextKey samplerKeyword(String normalizedId) {
    if (normalizedId.contains("tick")) {
      return SAMPLER_TICK;
    }
    if (normalizedId.contains("memory") || normalizedId.contains("gc")) {
      return SAMPLER_MEMORY;
    }
    if (normalizedId.contains("chunk")) {
      return SAMPLER_CHUNK;
    }
    if (normalizedId.contains("entity")) {
      return SAMPLER_ENTITY;
    }
    if (normalizedId.contains("player") || normalizedId.contains("ping")) {
      return SAMPLER_PLAYER;
    }
    if (normalizedId.contains("redstone")) {
      return SAMPLER_REDSTONE;
    }
    if (normalizedId.contains("hopper")) {
      return SAMPLER_HOPPER;
    }
    if (normalizedId.contains("fluid")) {
      return SAMPLER_FLUID;
    }
    if (normalizedId.contains("physics")) {
      return SAMPLER_PHYSICS;
    }
    if (normalizedId.contains("event")) {
      return SAMPLER_EVENT;
    }
    if (normalizedId.contains("react") || normalizedId.contains("job") || normalizedId.contains("queue") || normalizedId.contains("backlog")) {
      return SAMPLER_REACT;
    }
    if (normalizedId.contains("processor") || normalizedId.contains("load")) {
      return SAMPLER_PROCESSOR;
    }
    if (normalizedId.startsWith("iris-")) {
      return SAMPLER_IRIS;
    }
    if (normalizedId.startsWith("adapt-")) {
      return SAMPLER_ADAPT;
    }
    return SAMPLER_FALLBACK;
  }

  private static Map.Entry<String, TextKey> rendererSummaryEntry(String id, String english) {
    return Map.entry(id, TextKey.of("gui.map.summary.renderer." + id.replace('-', '_'), english));
  }

  private static Map.Entry<String, TextKey> rendererDetail(String id, String english) {
    return Map.entry(id, TextKey.of("gui.map.detail.renderer." + id.replace('-', '_'), english));
  }

  private static Map.Entry<String, TextKey> samplerSummary(String id, String english) {
    return Map.entry(id, TextKey.of("gui.map.summary.sampler." + id.replace('-', '_'), english));
  }

  private static Map.Entry<String, TextKey> samplerName(String id, String english) {
    return Map.entry(id, TextKey.of("gui.map.name.sampler." + id.replace('-', '_'), english));
  }

  private static TextKey keywordSummary(String keyword, String english) {
    return TextKey.of("gui.map.summary.sampler.keyword." + keyword, english);
  }
}
