package art.arcane.react.localization.catalog;

import art.arcane.react.api.rendering.MegamapGrid;
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
      rendererSummaryEntry("adapt-runtime-pressure-overlay", "Adapt-aware pressure overlay using session load and measured guard-check timing budget."),
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
      samplerSummary("ground-items", "Item entities lying in loaded chunks across all worlds."),
      samplerSummary("entities-hostile", "Hostile mobs loaded across all worlds."),
      samplerSummary("entities-animals", "Passive animals loaded across all worlds."),
      samplerSummary("villagers", "Villagers and wandering traders loaded across all worlds."),
      samplerSummary("projectiles", "Projectiles currently in flight across all worlds."),
      samplerSummary("physics-entities", "Primed TNT and falling blocks currently simulating."),
      samplerSummary("spawner-spawns", "Mobs produced per second by spawner blocks."),
      samplerSummary("block-entities", "Block entities loaded across all worlds."),
      samplerSummary("block-entities-ticking", "Block entities ticking every tick across all worlds."),
      samplerSummary("chunks", "Loaded chunk count trend."),
      samplerSummary("chunks-loaded", "Chunk-load event rate."),
      samplerSummary("chunks-generated", "Chunk-generation event rate."),
      samplerSummary("chunk-unloads", "Chunks unloaded per second across all worlds."),
      samplerSummary("chunk-load-ms", "Chunk load latency in milliseconds."),
      samplerSummary("chunk-gen-ms", "Chunk generation latency in milliseconds."),
      samplerSummary("top-chunk-cost", "Highest observed per-chunk cost from React chunk sampling."),
      samplerSummary("top-world-mspt", "Worst world-level MSPT observed across loaded worlds."),
      samplerSummary("per-world-tick-time", "Worst per-world rolling tick share used by the per-world tick budget hysteresis layer."),
      samplerSummary("chunks-force-loaded", "Chunks pinned loaded permanently by force load tickets."),
      samplerSummary("chunk-tickets", "Chunks pinned loaded by plugin chunk tickets."),
      samplerSummary("worlds", "Worlds currently loaded on the server."),
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
      samplerSummary("commands", "Commands executed per second by players and consoles."),
      samplerSummary("scheduler-backlog", "Scheduler backlog depth waiting for execution."),
      samplerSummary("backlog-growth-rate", "Rate at which scheduler backlog is growing or shrinking."),
      samplerSummary("bukkit-pending-tasks", "Tasks waiting in the Bukkit scheduler across all plugins."),
      samplerSummary("react-jobs-queue", "Queued React jobs waiting to be processed."),
      samplerSummary("react-job-budget", "Remaining React job budget available per processing cycle."),
      samplerSummary("react-job-queue-time", "Time jobs spend waiting in React queues."),
      samplerSummary("react-sync-tick-time", "Time React spends on synchronous work per tick."),
      samplerSummary("react-async-tick-time", "Time React spends on asynchronous work per cycle."),
      samplerSummary("processor-system-load", "Host system CPU load trend."),
      samplerSummary("processor-process-load", "JVM process CPU load trend."),
      samplerSummary("processor-outside", "Non-React external process load pressure."),
      samplerSummary("jvm-threads", "Live JVM threads including plugin and server pools."),
      samplerSummary("iris-pregen-queue", "Iris remote pregen queue depth."),
      samplerSummary("iris-pregen-throughput", "Iris pregenerator throughput in chunks per second."),
      samplerSummary("iris-generation-total-ms", "Worst active Iris-world rolling generation time."),
      samplerSummary("iris-chunks-per-second", "Combined Iris engine chunk generation throughput."),
      samplerSummary("adapt-session-load", "Adapt runtime session load percentage."),
      samplerSummary("adapt-ability-ops", "Adapt ability operations per minute (mode selected by config adaptAbilityOpsMetricMode)."),
      samplerSummary("adapt-ability-checks-per-tick", "Adapt all ability checks averaged per tick over the telemetry window."),
      samplerSummary("adapt-world-policy-latency", "Adapt world-policy evaluation latency."),
      samplerSummary("adapt-cache-hit-ratio", "Share of ability level checks served from the active-level cache."),
      samplerSummary("adapt-check-latency", "Mean microseconds per uncached ability guard check."),
      samplerSummary("adapt-event-ops", "Adaptation event handler invocations per minute."),
      samplerSummary("adapt-fx-packets", "Particle packets consumed in the current FX budget tick."),
      samplerSummary("adapt-fx-shed-band", "FX degradation band; zero is full quality, four is heaviest shedding."),
      samplerSummary("adapt-fx-timelines", "Live FX timelines playing in the effects engine."),
      samplerSummary("adapt-learned-adaptations", "Distinct adaptations armed by at least one online player."),
      samplerSummary("adapt-minions", "Summoned minions currently alive across all owners."),
      samplerSummary("adapt-persistence-queue", "Player data saves waiting for async persistence."),
      samplerSummary("adapt-player-sessions", "Players with an active Adapt session loaded."),
      samplerSummary("adapt-provenance-ops", "Block provenance lookups per minute from the XP integrity system."),
      samplerSummary("adapt-spatial-tickets", "Spatial XP orbs waiting in the world for pickup."),
      samplerSummary("adapt-timing-budget", "Rolling 60-second percentage of a 50 ms/s Adapt guard-check budget."),
      samplerSummary("adapt-xp-payouts", "XP payout events per minute across all skills."),
      samplerSummary("adapt-xp-rate", "Skill XP awarded server-wide per minute after integrity scaling."),
      samplerSummary("crop-fast-forward", "Crop growth work fast-forwarded by React's farm optimization."),
      samplerSummary("explosion-packet-reduction", "Explosion packet volume reduced before delivery to nearby players."),
      samplerSummary("hopper-chain-coalescing", "Repeated hopper-chain operations coalesced into cheaper work."),
      samplerSummary("lazy-gravity-skipped", "Falling-block movement ticks projected as skippable by lazy gravity."),
      samplerSummary("pdc-write-batcher", "Persistent-data writes combined by React's write batcher."),
      samplerSummary("spawner-light-cache-skipped", "Repeated spawner light checks served from the short-lived cache."),
      samplerSummary("world-save-duration", "Duration of recent world-save operations."),
      samplerSummary("wormholes-block-changes", "Wormholes projected block changes published through the integration bridge."),
      samplerSummary("wormholes-compression", "Outbound wire compression ratio; near 1.0 means a stale dictionary."),
      samplerSummary("wormholes-packets", "Wormholes projection packet throughput published through the integration bridge."),
      samplerSummary("wormholes-peer-rtt", "Worst-case round-trip latency to a connected peer server."),
      samplerSummary("wormholes-peers", "Remote servers currently linked in the wormholes mesh."),
      samplerSummary("wormholes-portals", "Active Wormholes portal count published through the integration bridge."),
      samplerSummary("wormholes-projection-observers", "Players currently observing Wormholes projections."),
      samplerSummary("wormholes-projection-render-ms", "Wormholes projection rendering time."),
      samplerSummary("wormholes-projections-active", "Active Wormholes projections."),
      samplerSummary("wormholes-remote-portals", "Remote portal destinations known from connected peers."),
      samplerSummary("wormholes-replicated-blocks", "Block updates replicated per second to remote projections."),
      samplerSummary("wormholes-resyncs", "Cumulative chunk resync requests from peers detecting divergence."),
      samplerSummary("wormholes-sideband-drops", "Sideband messages shed per second under backpressure."),
      samplerSummary("wormholes-sideband-queue", "Bytes backlogged on the rate-limited sideband status channel."),
      samplerSummary("wormholes-spoofed-entities", "Client-side entities currently projected by Wormholes."),
      samplerSummary("wormholes-transfers", "Players currently mid-handoff between servers."),
      samplerSummary("wormholes-transfers-failed", "Cumulative failed cross-server player transfers."),
      samplerSummary("wormholes-traversals", "Wormholes portal traversal rate."),
      samplerSummary("wormholes-view-entities", "Entities tracked for replication to remote viewers."),
      samplerSummary("wormholes-view-subscriptions", "Remote-view sessions this server is serving to peers."),
      samplerSummary("wormholes-wire-in", "Post-compression network ingress from remote peers."),
      samplerSummary("wormholes-wire-out", "Post-compression network egress of the wormholes mesh."),
      samplerSummary("gloss-animations", "Text animations currently registered and ticking."),
      samplerSummary("gloss-boards", "Scoreboard sidebars currently rendered for players."),
      samplerSummary("gloss-bubbles", "Chat bubbles spawned above players per second."),
      samplerSummary("gloss-builder-server", "Whether the web builder server is running; one means online."),
      samplerSummary("gloss-display-entities", "Packet-only display entities registered for holograms."),
      samplerSummary("gloss-emoji", "Chat emoji replacements applied per second."),
      samplerSummary("gloss-holograms", "Persistent text holograms currently active in loaded chunks."),
      samplerSummary("gloss-indicators", "Damage indicators spawned per second."),
      samplerSummary("gloss-menu-definitions", "Menu definitions loaded from configuration."),
      samplerSummary("gloss-menus", "Holographic menus currently open."),
      samplerSummary("gloss-packets", "Hologram packets sent to clients per second."),
      samplerSummary("gloss-panels", "World-anchored panel menus currently active."),
      samplerSummary("gloss-preview-refresh", "Container preview inventory refresh operations per second."),
      samplerSummary("gloss-previews", "Container HUD previews currently rendered."),
      samplerSummary("gloss-sessions", "Players holding any Gloss menu or preview state."),
      samplerSummary("gloss-spawns", "Display entity spawn and despawn churn per second."),
      samplerSummary("gloss-tablist-players", "Players currently receiving Gloss tablist formatting."),
      samplerSummary("gloss-tick-ms", "Main-thread milliseconds spent per second in the session tick loop."),
      samplerSummary("gloss-visible-entities", "Display entities actually spawned to a client right now."),
      samplerSummary("hiddenore-breaks", "Rule-covered blocks broken by players per second."),
      samplerSummary("hiddenore-drop-rules", "Drop rules loaded from configuration."),
      samplerSummary("hiddenore-drops", "Custom drops injected per second replacing vanilla drops."),
      samplerSummary("hiddenore-ore-removal", "Whether anti-xray ore removal is enabled; one means active."),
      samplerSummary("hiddenore-ore-removal-rate", "Ore blocks replaced per second during world generation."),
      samplerSummary("hiddenore-pdc-reads", "Chunk provenance data reads per second."),
      samplerSummary("hiddenore-pdc-writes", "Chunk provenance data writes per second."),
      samplerSummary("hiddenore-reloads", "Cumulative configuration hot reloads this session."),
      samplerSummary("hiddenore-seeded-mode", "Whether seeded vein generation is active; one means seeded."),
      samplerSummary("hiddenore-vein-cache", "Chunks currently held in the seeded vein cache."),
      samplerSummary("hiddenore-vein-computes", "Chunk vein layouts computed per second on cache misses."),
      samplerSummary("hiddenore-vein-discoveries", "New hidden veins discovered by players per second."),
      samplerSummary("biletools-dirty-plugins", "Plugins quarantined after a failed or timed out reload."),
      samplerSummary("biletools-reload-ms", "Duration of the most recent plugin reload."),
      samplerSummary("biletools-reloads", "Cumulative hot reloads and hot drops this session."),
      samplerSummary("biletools-remote-slave", "Whether the remote deploy slave socket is open; one means online."),
      samplerSummary("biletools-watched-jars", "Plugin jars tracked by the hot reload watcher."),
      samplerSummary("unknown", "Fallback sampler used when a configured monitor cannot be resolved.")
  );
  private static final Map<String, TextKey> SAMPLER_NAMES = Map.ofEntries(
      samplerName("adapt-ability-checks-per-tick", "Adapt Ability Checks Per Tick"),
      samplerName("adapt-ability-ops", "Adapt Ability Ops"),
      samplerName("adapt-cache-hit-ratio", "Adapt Cache Hit Ratio"),
      samplerName("adapt-check-latency", "Adapt Check Latency"),
      samplerName("adapt-event-ops", "Adapt Event Ops"),
      samplerName("adapt-fx-packets", "Adapt FX Packets"),
      samplerName("adapt-fx-shed-band", "Adapt FX Shed Band"),
      samplerName("adapt-fx-timelines", "Adapt FX Timelines"),
      samplerName("adapt-learned-adaptations", "Adapt Learned Adaptations"),
      samplerName("adapt-minions", "Adapt Minions"),
      samplerName("adapt-persistence-queue", "Adapt Save Queue"),
      samplerName("adapt-player-sessions", "Adapt Player Sessions"),
      samplerName("adapt-provenance-ops", "Adapt Provenance Ops"),
      samplerName("adapt-session-load", "Adapt Session Load"),
      samplerName("adapt-spatial-tickets", "Adapt Spatial XP"),
      samplerName("adapt-timing-budget", "Adapt Timing Budget"),
      samplerName("adapt-world-policy-latency", "Adapt World Policy Latency"),
      samplerName("adapt-xp-payouts", "Adapt XP Payouts"),
      samplerName("adapt-xp-rate", "Adapt XP Rate"),
      samplerName("backlog-growth-rate", "Backlog Growth Rate"),
      samplerName("biletools-dirty-plugins", "BileTools Dirty Plugins"),
      samplerName("biletools-reload-ms", "BileTools Reload Time"),
      samplerName("biletools-reloads", "BileTools Reloads"),
      samplerName("biletools-remote-slave", "BileTools Remote Slave"),
      samplerName("biletools-watched-jars", "BileTools Watched Jars"),
      samplerName("block-entities", "Block Entities"),
      samplerName("block-entities-ticking", "Ticking Block Entities"),
      samplerName("bukkit-pending-tasks", "Pending Tasks"),
      samplerName("chunk-gen-ms", "Chunk Gen Ms"),
      samplerName("chunk-load-ms", "Chunk Load Ms"),
      samplerName("chunk-tickets", "Chunk Tickets"),
      samplerName("chunk-unloads", "Chunk Unloads"),
      samplerName("chunks", "Chunks"),
      samplerName("chunks-force-loaded", "Force Loaded Chunks"),
      samplerName("chunks-generated", "Chunks Generated"),
      samplerName("chunks-loaded", "Chunks Loaded"),
      samplerName("commands", "Commands"),
      samplerName("crop-fast-forward", "Crop Fast Forward"),
      samplerName("entities", "Entities"),
      samplerName("entities-animals", "Animals"),
      samplerName("entities-hostile", "Hostile Mobs"),
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
      samplerName("gloss-animations", "Gloss Animations"),
      samplerName("gloss-boards", "Gloss Boards"),
      samplerName("gloss-bubbles", "Gloss Chat Bubbles"),
      samplerName("gloss-builder-server", "Gloss Builder Server"),
      samplerName("gloss-display-entities", "Gloss Display Entities"),
      samplerName("gloss-emoji", "Gloss Emoji Replacements"),
      samplerName("gloss-holograms", "Gloss Holograms"),
      samplerName("gloss-indicators", "Gloss Damage Indicators"),
      samplerName("gloss-menu-definitions", "Gloss Menu Definitions"),
      samplerName("gloss-menus", "Gloss Menus"),
      samplerName("gloss-packets", "Gloss Packets"),
      samplerName("gloss-panels", "Gloss Panels"),
      samplerName("gloss-preview-refresh", "Gloss Preview Refresh"),
      samplerName("gloss-previews", "Gloss Previews"),
      samplerName("gloss-sessions", "Gloss Sessions"),
      samplerName("gloss-spawns", "Gloss Spawns"),
      samplerName("gloss-tablist-players", "Gloss Tablist Players"),
      samplerName("gloss-tick-ms", "Gloss Tick Time"),
      samplerName("gloss-visible-entities", "Gloss Visible Entities"),
      samplerName("ground-items", "Ground Items"),
      samplerName("hiddenore-breaks", "HiddenOre Breaks"),
      samplerName("hiddenore-drop-rules", "HiddenOre Drop Rules"),
      samplerName("hiddenore-drops", "HiddenOre Drops"),
      samplerName("hiddenore-ore-removal", "HiddenOre Ore Removal"),
      samplerName("hiddenore-ore-removal-rate", "HiddenOre Removal Rate"),
      samplerName("hiddenore-pdc-reads", "HiddenOre PDC Reads"),
      samplerName("hiddenore-pdc-writes", "HiddenOre PDC Writes"),
      samplerName("hiddenore-reloads", "HiddenOre Reloads"),
      samplerName("hiddenore-seeded-mode", "HiddenOre Seeded Mode"),
      samplerName("hiddenore-vein-cache", "HiddenOre Vein Cache"),
      samplerName("hiddenore-vein-computes", "HiddenOre Vein Computes"),
      samplerName("hiddenore-vein-discoveries", "HiddenOre Vein Finds"),
      samplerName("hopper-chain-coalescing", "Hopper Chain Coalescing"),
      samplerName("hopper-tick-time", "Hopper Tick Time"),
      samplerName("hopper", "Hopper"),
      samplerName("incident-score", "Incident Score"),
      samplerName("iris-chunks-per-second", "Iris Chunks Per Second"),
      samplerName("iris-generation-total-ms", "Iris Generation Total Ms"),
      samplerName("iris-pregen-queue", "Iris Pregen Queue"),
      samplerName("iris-pregen-throughput", "Iris Pregen Throughput"),
      samplerName("jvm-threads", "JVM Threads"),
      samplerName("lazy-gravity-skipped", "Lazy Gravity Skipped"),
      samplerName("memory-free", "Memory Free"),
      samplerName("memory-garbage", "Memory Garbage"),
      samplerName("memory-pressure", "Memory Pressure"),
      samplerName("memory-used", "Memory Used"),
      samplerName("memory-used-after-gc", "Memory Used After Gc"),
      samplerName("pdc-write-batcher", "Pdc Write Batcher"),
      samplerName("per-world-tick-time", "Per World Tick Time"),
      samplerName("physics-entities", "Physics Entities"),
      samplerName("physics-tick-time", "Physics Tick Time"),
      samplerName("physics", "Physics"),
      samplerName("ping-jitter", "Ping Jitter"),
      samplerName("player-ping-p95", "Player Ping P95"),
      samplerName("players", "Players"),
      samplerName("processor-outside", "Processor Outside"),
      samplerName("processor-process-load", "Processor Process Load"),
      samplerName("processor-system-load", "Processor System Load"),
      samplerName("projectiles", "Projectiles"),
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
      samplerName("spawner-spawns", "Spawner Spawns"),
      samplerName("tick-ms-p50", "Tick Ms P50"),
      samplerName("tick-ms-p95", "Tick Ms P95"),
      samplerName("tick-ms-p99", "Tick Ms P99"),
      samplerName("tick-spike-rate", "Tick Spike Rate"),
      samplerName("tick-time", "Tick Time"),
      samplerName("ticks-per-second", "Ticks Per Second"),
      samplerName("top-chunk-cost", "Top Chunk Cost"),
      samplerName("top-world-mspt", "Top World Mspt"),
      samplerName("unknown", "Unknown"),
      samplerName("villagers", "Villagers"),
      samplerName("world-save-duration", "World Save Duration"),
      samplerName("worlds", "Worlds"),
      samplerName("wormholes-block-changes", "Wormholes Block Changes"),
      samplerName("wormholes-compression", "Wormholes Compression"),
      samplerName("wormholes-packets", "Wormholes Packets"),
      samplerName("wormholes-peer-rtt", "Wormholes Peer RTT"),
      samplerName("wormholes-peers", "Wormholes Peers"),
      samplerName("wormholes-portals", "Wormholes Portals"),
      samplerName("wormholes-projection-observers", "Wormholes Projection Observers"),
      samplerName("wormholes-projection-render-ms", "Wormholes Projection Render Ms"),
      samplerName("wormholes-projections-active", "Wormholes Projections Active"),
      samplerName("wormholes-remote-portals", "Wormholes Remote Portals"),
      samplerName("wormholes-replicated-blocks", "Wormholes Replicated Blocks"),
      samplerName("wormholes-resyncs", "Wormholes Resyncs"),
      samplerName("wormholes-sideband-drops", "Wormholes Sideband Drops"),
      samplerName("wormholes-sideband-queue", "Wormholes Sideband Queue"),
      samplerName("wormholes-spoofed-entities", "Wormholes Spoofed Entities"),
      samplerName("wormholes-transfers", "Wormholes Transfers"),
      samplerName("wormholes-transfers-failed", "Wormholes Failed Transfers"),
      samplerName("wormholes-traversals", "Wormholes Traversals"),
      samplerName("wormholes-view-entities", "Wormholes View Entities"),
      samplerName("wormholes-view-subscriptions", "Wormholes View Subscriptions"),
      samplerName("wormholes-wire-in", "Wormholes Wire In"),
      samplerName("wormholes-wire-out", "Wormholes Wire Out")
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

  public static final TextKey COLUMN_NAME = render("column_name", "Name");
  public static final TextKey COLUMN_SHARE = render("column_share", "Share");
  public static final TextKey COLUMN_COST = render("column_cost", "Cost");
  public static final TextKey COLUMN_CALLS = render("column_calls", "Calls");
  public static final TextKey COLUMN_OPS = render("column_ops", "Ops");
  public static final TextKey COLUMN_COUNT = render("column_count", "Count");
  public static final TextKey ROWS_SHOWN = render("rows_shown", "{shown}/{total}");
  public static final TextKey TOTAL_COST = render("total_cost", "{value} ms/s");
  public static final TextKey SCALE_CHUNKS = render("scale_chunks", "{radius} chunk radius");
  public static final TextKey MORE_ITEMS = render("more_items", "+{count} more");
  public static final TextKey MEGAMAP_ZOOMED = megamap("zoomed", "{grid} zoom");
  public static final TextKey MEGAMAP_CAPPED = megamap("capped", "{grid} capped");
  public static final TextKey MEGAMAP_DEFECT_HOLE = megamap("defect_hole", "wall gap");
  public static final TextKey MEGAMAP_DEFECT_DUPLICATE = megamap("defect_duplicate", "duplicate map");
  public static final TextKey MEGAMAP_DEFECT_ROTATED = megamap("defect_rotated", "frame rotated");
  public static final TextKey MEGAMAP_DEFECT_MIXED = megamap("defect_mixed", "mixed maps");
  public static final TextKey MEGAMAP_WALLS = megamap("walls", "Map walls");
  public static final TextKey MEGAMAP_WALLS_SUMMARY =
      megamap("walls_summary", "Adjacent frames with the same map join into one display.");
  public static final TextKey MEGAMAP_WALLS_DETECTED = megamap("walls_detected", "{count} detected");
  public static final TextKey MEGAMAP_WALLS_ISSUES = megamap("walls_issues", "{count} with issues");
  public static final TextKey MEGAMAP_ADAPTIVE = megamap("adaptive", "Megamap: more data up to {size}");
  public static final TextKey MEGAMAP_MAGNIFY = megamap("magnify", "Megamap: scales up, same layout");
  public static final TextKey MEGAMAP_ACTIVE = megamap("active", "Active wall: {size} ({frames})");
  public static final TextKey MEGAMAP_ISSUE = megamap("issue", "Wall issue: {reason}");

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
    builder.add(COLUMN_NAME);
    builder.add(COLUMN_SHARE);
    builder.add(COLUMN_COST);
    builder.add(COLUMN_CALLS);
    builder.add(COLUMN_OPS);
    builder.add(COLUMN_COUNT);
    builder.add(ROWS_SHOWN);
    builder.add(TOTAL_COST);
    builder.add(SCALE_CHUNKS);
    builder.add(MORE_ITEMS);
    builder.add(MEGAMAP_ZOOMED);
    builder.add(MEGAMAP_CAPPED);
    builder.add(MEGAMAP_DEFECT_HOLE);
    builder.add(MEGAMAP_DEFECT_DUPLICATE);
    builder.add(MEGAMAP_DEFECT_ROTATED);
    builder.add(MEGAMAP_DEFECT_MIXED);
    builder.add(MEGAMAP_WALLS);
    builder.add(MEGAMAP_WALLS_SUMMARY);
    builder.add(MEGAMAP_WALLS_DETECTED);
    builder.add(MEGAMAP_WALLS_ISSUES);
    builder.add(MEGAMAP_ADAPTIVE);
    builder.add(MEGAMAP_MAGNIFY);
    builder.add(MEGAMAP_ACTIVE);
    builder.add(MEGAMAP_ISSUE);
  }

  public static TextKey defectLabel(MegamapGrid.DefectReason reason) {
    return switch (reason) {
      case HOLE -> MEGAMAP_DEFECT_HOLE;
      case DUPLICATE_MAP_ID -> MEGAMAP_DEFECT_DUPLICATE;
      case ROTATED -> MEGAMAP_DEFECT_ROTATED;
      case MIXED_RENDERER -> MEGAMAP_DEFECT_MIXED;
    };
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

  private static TextKey render(String id, String english) {
    return TextKey.of("gui.map.render." + id, english);
  }

  private static TextKey megamap(String id, String english) {
    return TextKey.of("gui.map.megamap." + id, english);
  }
}
