package art.arcane.react.localization.catalog;

import art.arcane.react.localization.ReactLanguage;
import art.arcane.volmlib.util.localization.MessageArgument;
import art.arcane.volmlib.util.localization.MessageCatalog;
import art.arcane.volmlib.util.localization.TextKey;

import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class RendererMessages {
  public static final TextKey STATUS_LINE = key("status.line", "Status: {status}");
  public static final TextKey MESSAGE_LINE = key("status.message", "Msg: {message}");
  public static final TextKey AGE_LINE = key("status.age", "Age: {age}s");
  public static final TextKey METRIC_LINE = key("metric.line", "{label}: {value}");
  public static final TextKey BRIDGE_UNAVAILABLE = key("status.bridge_unavailable", "Bridge unavailable");
  public static final TextKey VALUE_NOT_AVAILABLE = key("value.not_available", "n/a");
  public static final TextKey STATUS_LOCAL = key("status.local", "LOCAL");
  public static final TextKey STATUS_OFFLINE = key("status.offline", "OFFLINE");
  public static final TextKey STATUS_MISSING = key("status.missing", "MISSING");
  public static final TextKey STATUS_HEALTHY = key("status.healthy", "HEALTHY");
  public static final TextKey STATUS_ONLINE = key("status.online", "ONLINE");
  public static final TextKey STATUS_OK = key("status.ok", "OK");
  public static final TextKey STATUS_DEGRADED = key("status.degraded", "DEGRADED");
  public static final TextKey STATUS_WARN = key("status.warn", "WARN");
  public static final TextKey STATUS_UNAVAILABLE = key("status.unavailable", "UNAVAILABLE");
  public static final TextKey STATUS_ERROR = key("status.error", "ERROR");
  public static final TextKey STATUS_NO_WORLD = key("status.no_world", "NO WORLD");
  public static final TextKey STATUS_FRAME = key("status.frame", "FRAME");
  public static final TextKey STATUS_WAITING = key("status.waiting", "WAITING");

  public static final TextKey TITLE_REACT_MAP = title("react_map", "React Map");
  public static final TextKey TITLE_REACT_METRICS = title("react_metrics", "React Metrics");
  public static final TextKey TITLE_ADAPT_METRICS = title("adapt_metrics", "Adapt Metrics");
  public static final TextKey TITLE_WORMHOLES_METRICS = title("wormholes_metrics", "Wormholes Metrics");
  public static final TextKey TITLE_GLOSS_METRICS = title("gloss_metrics", "Gloss Metrics");
  public static final TextKey TITLE_HIDDENORE_METRICS = title("hiddenore_metrics", "HiddenOre Metrics");
  public static final TextKey TITLE_BILETOOLS_METRICS = title("biletools_metrics", "BileTools Metrics");
  public static final TextKey TITLE_IRIS_OVERVIEW = title("iris_overview", "Iris Overview");
  public static final TextKey TITLE_IRIS_ENGINE_ACTIVITY = title("iris_engine_activity", "Iris Engine Activity");
  public static final TextKey TITLE_IRIS_LIFECYCLE = title("iris_lifecycle", "Iris Lifecycle");
  public static final TextKey TITLE_IRIS_MANTLE_HEALTH = title("iris_mantle_health", "Iris Mantle Health");
  public static final TextKey TITLE_IRIS_CACHE_OVERVIEW = title("iris_cache_overview", "Iris Cache Overview");
  public static final TextKey TITLE_IRIS_STREAM_CACHES = title("iris_stream_caches", "Iris Stream Caches");
  public static final TextKey TITLE_IRIS_OTHER_CACHES = title("iris_other_caches", "Iris 3D and Other Caches");
  public static final TextKey TITLE_IRIS_PREGEN_PROGRESS = title("iris_pregen_progress", "Iris Pregen Progress");
  public static final TextKey TITLE_IRIS_PREGEN_HEALTH = title("iris_pregen_health", "Iris Pregen Health");
  public static final TextKey TITLE_IRIS_GENERATION_PIPELINE = title("iris_generation_pipeline", "Iris Generation Pipeline");
  public static final TextKey TITLE_IRIS_GENERATION_FEATURES = title("iris_generation_features", "Iris Generation Features");
  public static final TextKey TITLE_IRIS_WORLD_OVERVIEW = title("iris_world_overview", "Iris World Overview");
  public static final TextKey TITLE_IRIS_WORLD_ENGINE = title("iris_world_engine", "Iris World Engine");
  public static final TextKey TITLE_IRIS_WORLD_MANTLE = title("iris_world_mantle", "Iris World Mantle");
  public static final TextKey TITLE_IRIS_WORLD_GENERATION = title("iris_world_generation", "Iris World Generation");
  public static final TextKey TITLE_IRIS_WORLD_FEATURES = title("iris_world_features", "Iris World Features");
  public static final TextKey TITLE_IRIS_WORLD_PREGEN = title("iris_world_pregen", "Iris World Pregen");
  public static final TextKey TITLE_IRIS_WORLD_PREGEN_TIME = title("iris_world_pregen_time", "Iris World Pregen Time");
  public static final TextKey TITLE_ADAPT_CALLBACK_COST = title("adapt_callback_cost", "Adapt Callback Cost");
  public static final TextKey TITLE_PLUGIN_IMPACT = title("plugin_impact", "Plugin Impact");
  public static final TextKey TITLE_WORLD_CHUNK_SHARE = title("world_chunk_share", "World Chunk Share");
  public static final TextKey TITLE_PLUGIN_IMPACT_SHARE = title("plugin_impact_share", "Plugin Impact Share");
  public static final TextKey TITLE_BIOME_CHUNK_SHARE = title("biome_chunk_share", "Biome Chunk Share");
  public static final TextKey TITLE_CHUNK_COST_HEAT = title("chunk_cost_heat", "Chunk Cost Heat");
  public static final TextKey TITLE_ADAPT_PRESSURE = title("adapt_pressure", "Adapt Pressure");
  public static final TextKey TITLE_REDSTONE_ACTIVITY = title("redstone_activity", "Redstone Activity");
  public static final TextKey TITLE_ENTITY_PRESSURE = title("entity_pressure", "Entity Pressure");
  public static final TextKey TITLE_PLAYER_IMPACT = title("player_impact", "Player Impact");
  public static final TextKey TITLE_TICK_SPIKE_REPLAY = title("tick_spike_replay", "Tick Spike Replay");
  public static final TextKey TITLE_IRIS_PRESSURE = title("iris_pressure", "Iris Pressure");
  public static final TextKey TITLE_HOPPER_THROUGHPUT = title("hopper_throughput", "Hopper Throughput");
  public static final TextKey TITLE_CHUNK_LOAD_GEN_COST = title("chunk_load_gen_cost", "Chunk Load/Gen Cost");

  public static final TextKey METRIC_TPS = metric("tps", "TPS");
  public static final TextKey METRIC_TICK = metric("tick", "Tick");
  public static final TextKey METRIC_INCIDENT = metric("incident", "Incident");
  public static final TextKey METRIC_JOBS = metric("jobs", "Jobs");
  public static final TextKey METRIC_BACKLOG = metric("backlog", "Backlog");
  public static final TextKey METRIC_SESSION = metric("session", "Session");
  public static final TextKey METRIC_ABILITY_MODE = metric("ability_mode", "Ability ({mode})");
  public static final TextKey METRIC_ALL_PER_TICK = metric("all_per_tick", "All/tick");
  public static final TextKey METRIC_POLICY = metric("policy", "Policy");
  public static final TextKey METRIC_PORTALS = metric("portals", "Portals");
  public static final TextKey METRIC_PROJECTING = metric("projecting", "Projecting");
  public static final TextKey METRIC_OBSERVERS = metric("observers", "Observers");
  public static final TextKey METRIC_RENDER = metric("render", "Render");
  public static final TextKey METRIC_TRAFFIC = metric("traffic", "Traffic");
  public static final TextKey METRIC_ENTITIES = metric("entities", "Entities");
  public static final TextKey METRIC_TRAVEL = metric("travel", "Travel");
  public static final TextKey METRIC_WORLDS = metric("worlds", "Worlds");
  public static final TextKey METRIC_ACTIVE = metric("active", "Active");
  public static final TextKey METRIC_CHUNKS = metric("chunks", "Chunks");
  public static final TextKey METRIC_CHUNK_RATE = metric("chunk_rate", "Chunk Rate");
  public static final TextKey METRIC_GENERATED = metric("generated", "Generated");
  public static final TextKey METRIC_FAILED = metric("failed", "Failed");
  public static final TextKey METRIC_SESSION_CHUNKS = metric("session_chunks", "Session Chunks");
  public static final TextKey METRIC_LIFETIME_CHUNKS = metric("lifetime_chunks", "Lifetime Chunks");
  public static final TextKey METRIC_BLOCK_UPDATES = metric("block_updates", "Block Updates");
  public static final TextKey METRIC_ACTIVE_LEASES = metric("active_leases", "Active Leases");
  public static final TextKey METRIC_PARALLELISM = metric("parallelism", "Parallelism");
  public static final TextKey METRIC_HOTLOADS = metric("hotloads", "Hotloads");
  public static final TextKey METRIC_CLOSING = metric("closing", "Closing");
  public static final TextKey METRIC_STUDIO = metric("studio", "Studio");
  public static final TextKey METRIC_MAINTENANCE = metric("maintenance", "Maintenance");
  public static final TextKey METRIC_WORKERS = metric("workers", "Workers");
  public static final TextKey METRIC_REGISTERING = metric("registering", "Registering");
  public static final TextKey METRIC_RESIDENT = metric("resident", "Resident");
  public static final TextKey METRIC_UNLOAD_QUEUE = metric("unload_queue", "Unload Queue");
  public static final TextKey METRIC_IDLE_AVERAGE = metric("idle_average", "Idle Avg");
  public static final TextKey METRIC_IDLE_MAXIMUM = metric("idle_maximum", "Idle Max");
  public static final TextKey METRIC_IDLE_MINIMUM = metric("idle_minimum", "Idle Min");
  public static final TextKey METRIC_HEAP = metric("heap", "Heap");
  public static final TextKey METRIC_RECLAIM = metric("reclaim", "Reclaim");
  public static final TextKey METRIC_CACHES = metric("caches", "Caches");
  public static final TextKey METRIC_ENTRIES = metric("entries", "Entries");
  public static final TextKey METRIC_CAPACITY = metric("capacity", "Capacity");
  public static final TextKey METRIC_USAGE = metric("usage", "Usage");
  public static final TextKey METRIC_RESOURCE_CACHES = metric("resource_caches", "Resource Caches");
  public static final TextKey METRIC_RESOURCE_ENTRIES = metric("resource_entries", "Resource Entries");
  public static final TextKey METRIC_RESOURCE_CAPACITY_SHORT = metric("resource_capacity_short", "Resource Cap");
  public static final TextKey METRIC_RESOURCE_USAGE = metric("resource_usage", "Resource Use");
  public static final TextKey METRIC_2D_CACHES = metric("2d_caches", "2D Caches");
  public static final TextKey METRIC_2D_ENTRIES = metric("2d_entries", "2D Entries");
  public static final TextKey METRIC_2D_CAPACITY = metric("2d_capacity", "2D Capacity");
  public static final TextKey METRIC_2D_USAGE = metric("2d_usage", "2D Usage");
  public static final TextKey METRIC_3D_CACHES = metric("3d_caches", "3D Caches");
  public static final TextKey METRIC_3D_ENTRIES = metric("3d_entries", "3D Entries");
  public static final TextKey METRIC_3D_CAPACITY = metric("3d_capacity", "3D Capacity");
  public static final TextKey METRIC_3D_USAGE = metric("3d_usage", "3D Usage");
  public static final TextKey METRIC_OTHER_CACHES = metric("other_caches", "Other Caches");
  public static final TextKey METRIC_OTHER_ENTRIES = metric("other_entries", "Other Entries");
  public static final TextKey METRIC_OTHER_CAPACITY = metric("other_capacity", "Other Capacity");
  public static final TextKey METRIC_OTHER_USAGE = metric("other_usage", "Other Usage");
  public static final TextKey METRIC_ENTITY_SATURATION = metric("entity_saturation", "Entity Saturation");
  public static final TextKey METRIC_PAUSED = metric("paused", "Paused");
  public static final TextKey METRIC_PROGRESS = metric("progress", "Progress");
  public static final TextKey METRIC_TOTAL = metric("total", "Total");
  public static final TextKey METRIC_REMAINING = metric("remaining", "Remaining");
  public static final TextKey METRIC_THROUGHPUT = metric("throughput", "Throughput");
  public static final TextKey METRIC_ETA = metric("eta", "ETA");
  public static final TextKey METRIC_ELAPSED = metric("elapsed", "Elapsed");
  public static final TextKey METRIC_FAILED_CHUNKS = metric("failed_chunks", "Failed Chunks");
  public static final TextKey METRIC_PERMIT_WAIT = metric("permit_wait", "Permit Wait");
  public static final TextKey METRIC_ADAPTIVE_WAIT = metric("adaptive_wait", "Adaptive Wait");
  public static final TextKey METRIC_TERRAIN = metric("terrain", "Terrain");
  public static final TextKey METRIC_BIOME = metric("biome", "Biome");
  public static final TextKey METRIC_POST = metric("post", "Post");
  public static final TextKey METRIC_PERFECTION = metric("perfection", "Perfection");
  public static final TextKey METRIC_DECORATION = metric("decoration", "Decoration");
  public static final TextKey METRIC_UPDATES = metric("updates", "Updates");
  public static final TextKey METRIC_CAVES = metric("caves", "Caves");
  public static final TextKey METRIC_DEPOSITS = metric("deposits", "Deposits");
  public static final TextKey METRIC_CONTEXT_PREFILL = metric("context_prefill", "Context Prefill");
  public static final TextKey METRIC_CARVE_RESOLVE = metric("carve_resolve", "Carve Resolve");
  public static final TextKey METRIC_CARVE_APPLY = metric("carve_apply", "Carve Apply");
  public static final TextKey METRIC_LOADED_CHUNKS = metric("loaded_chunks", "Loaded Chunks");
  public static final TextKey METRIC_SATURATION = metric("saturation", "Saturation");
  public static final TextKey METRIC_GENERATION_LEASES = metric("generation_leases", "Generation Leases");
  public static final TextKey METRIC_IDLE = metric("idle", "Idle");
  public static final TextKey METRIC_ENGINE_RATE = metric("engine_rate", "Engine Rate");
  public static final TextKey METRIC_SESSIONS = metric("sessions", "Sessions");
  public static final TextKey METRIC_MENUS = metric("menus", "Menus");
  public static final TextKey METRIC_PREVIEWS = metric("previews", "Previews");
  public static final TextKey METRIC_VISIBLE = metric("visible", "Visible");
  public static final TextKey METRIC_BREAKS = metric("breaks", "Breaks");
  public static final TextKey METRIC_DROPS = metric("drops", "Drops");
  public static final TextKey METRIC_VEIN_FINDS = metric("vein_finds", "Vein Finds");
  public static final TextKey METRIC_VEIN_CACHE = metric("vein_cache", "Vein Cache");
  public static final TextKey METRIC_PDC_READS = metric("pdc_reads", "PDC Reads");
  public static final TextKey METRIC_PDC_WRITES = metric("pdc_writes", "PDC Writes");
  public static final TextKey METRIC_RULES = metric("rules", "Rules");
  public static final TextKey METRIC_WATCHED_JARS = metric("watched_jars", "Watched Jars");
  public static final TextKey METRIC_DIRTY_PLUGINS = metric("dirty_plugins", "Dirty Plugins");
  public static final TextKey METRIC_RELOADS = metric("reloads", "Reloads");
  public static final TextKey METRIC_LAST_RELOAD = metric("last_reload", "Last Reload");
  public static final TextKey METRIC_REMOTE_SLAVE = metric("remote_slave", "Remote Slave");

  public static final TextKey UNKNOWN_RENDERER_UNAVAILABLE = key("unknown.renderer_unavailable", "Renderer unavailable");
  public static final TextKey UNKNOWN_MAP_WAS = key("unknown.map_was", "This map was");
  public static final TextKey UNKNOWN_BOUND = key("unknown.bound", "bound to a renderer");
  public static final TextKey UNKNOWN_MISSING = key("unknown.missing", "that is missing");
  public static final TextKey UNKNOWN_RESELECT_COMMAND = key("unknown.reselect_command", "Use /re map");
  public static final TextKey UNKNOWN_RESELECT = key("unknown.reselect", "to reselect.");
  public static final TextKey HEATMAP_NO_ACTIVITY = key("heatmap.no_activity", "No activity nearby");
  public static final TextKey HEATMAP_QUIET = key("heatmap.quiet", "quiet");
  public static final TextKey PIE_VIEWER_UNAVAILABLE = key("pie.viewer_unavailable", "Viewer unavailable");
  public static final TextKey PIE_NO_DATA = key("pie.no_data", "No data");
  public static final TextKey PIE_TOTAL = key("pie.total", "Total: {total} {unit}");
  public static final TextKey PIE_UNIT_CHUNKS = key("pie.unit.chunks", "chunks");
  public static final TextKey PIE_UNIT_IMPACT = key("pie.unit.impact", "impact");
  public static final TextKey PIE_UNKNOWN = key("pie.unknown", "Unknown");
  public static final TextKey PIE_OTHER = key("pie.other", "Other");
  public static final TextKey ADAPT_MEASURED_UNAVAILABLE = key("adapt.measured_unavailable", "Measured callback data unavailable");
  public static final TextKey ADAPT_GUARD_ONLY = key("adapt.guard_only", "Adapt reports guard checks only");
  public static final TextKey ADAPT_NO_ACTIVITY = key("adapt.no_activity", "No Adapt ability activity");
  public static final TextKey ADAPT_USE_ABILITIES = key("adapt.use_abilities", "Use abilities to populate this chart");
  public static final TextKey ADAPT_CALLBACK_VALUE = key("adapt.callback_value", "{timing}ms/m {operations}cb/m");
  public static final TextKey ADAPT_TOP_VALUE = key("adapt.top_value", "TOP {timing} ms/m");
  public static final TextKey PLUGIN_NO_IMPACT = key("plugin.no_impact", "No plugin event impact data");
  public static final TextKey PLUGIN_WAIT_FOR_DATA = key("plugin.wait_for_data", "Open this map for a few seconds");
  public static final TextKey PLUGIN_IMPACT_VALUE = key("plugin.impact_value", "{percent}%  {impact} ms/s");
  public static final TextKey SAMPLER_LOW_VALUE = key("sampler.low_value", "L {value}");
  public static final TextKey SAMPLER_HIGH_VALUE = key("sampler.high_value", "H {value}");

  private static final List<TextKey> KEYS = List.of(
      STATUS_LINE, MESSAGE_LINE, AGE_LINE, METRIC_LINE, BRIDGE_UNAVAILABLE, VALUE_NOT_AVAILABLE,
      STATUS_LOCAL, STATUS_OFFLINE, STATUS_MISSING, STATUS_HEALTHY, STATUS_ONLINE, STATUS_OK,
      STATUS_DEGRADED, STATUS_WARN, STATUS_UNAVAILABLE, STATUS_ERROR, STATUS_NO_WORLD, STATUS_FRAME,
      STATUS_WAITING, TITLE_REACT_MAP, TITLE_REACT_METRICS, TITLE_ADAPT_METRICS, TITLE_WORMHOLES_METRICS,
      TITLE_GLOSS_METRICS, TITLE_HIDDENORE_METRICS, TITLE_BILETOOLS_METRICS,
      TITLE_IRIS_OVERVIEW, TITLE_IRIS_ENGINE_ACTIVITY, TITLE_IRIS_LIFECYCLE, TITLE_IRIS_MANTLE_HEALTH,
      TITLE_IRIS_CACHE_OVERVIEW, TITLE_IRIS_STREAM_CACHES, TITLE_IRIS_OTHER_CACHES,
      TITLE_IRIS_PREGEN_PROGRESS, TITLE_IRIS_PREGEN_HEALTH, TITLE_IRIS_GENERATION_PIPELINE,
      TITLE_IRIS_GENERATION_FEATURES, TITLE_IRIS_WORLD_OVERVIEW, TITLE_IRIS_WORLD_ENGINE,
      TITLE_IRIS_WORLD_MANTLE, TITLE_IRIS_WORLD_GENERATION, TITLE_IRIS_WORLD_FEATURES,
      TITLE_IRIS_WORLD_PREGEN, TITLE_IRIS_WORLD_PREGEN_TIME, TITLE_ADAPT_CALLBACK_COST,
      TITLE_PLUGIN_IMPACT, TITLE_WORLD_CHUNK_SHARE, TITLE_PLUGIN_IMPACT_SHARE, TITLE_BIOME_CHUNK_SHARE,
      TITLE_CHUNK_COST_HEAT, TITLE_ADAPT_PRESSURE, TITLE_REDSTONE_ACTIVITY, TITLE_ENTITY_PRESSURE,
      TITLE_PLAYER_IMPACT, TITLE_TICK_SPIKE_REPLAY, TITLE_IRIS_PRESSURE, TITLE_HOPPER_THROUGHPUT,
      TITLE_CHUNK_LOAD_GEN_COST, METRIC_TPS, METRIC_TICK, METRIC_INCIDENT, METRIC_JOBS, METRIC_BACKLOG,
      METRIC_SESSION, METRIC_ABILITY_MODE, METRIC_ALL_PER_TICK, METRIC_POLICY, METRIC_PORTALS,
      METRIC_PROJECTING, METRIC_OBSERVERS, METRIC_RENDER, METRIC_TRAFFIC, METRIC_ENTITIES, METRIC_TRAVEL,
      METRIC_WORLDS, METRIC_ACTIVE, METRIC_CHUNKS, METRIC_CHUNK_RATE, METRIC_GENERATED, METRIC_FAILED,
      METRIC_SESSION_CHUNKS, METRIC_LIFETIME_CHUNKS, METRIC_BLOCK_UPDATES, METRIC_ACTIVE_LEASES,
      METRIC_PARALLELISM, METRIC_HOTLOADS, METRIC_CLOSING, METRIC_STUDIO, METRIC_MAINTENANCE,
      METRIC_WORKERS, METRIC_REGISTERING, METRIC_RESIDENT, METRIC_UNLOAD_QUEUE, METRIC_IDLE_AVERAGE,
      METRIC_IDLE_MAXIMUM, METRIC_IDLE_MINIMUM, METRIC_HEAP, METRIC_RECLAIM, METRIC_CACHES,
      METRIC_ENTRIES, METRIC_CAPACITY, METRIC_USAGE, METRIC_RESOURCE_CACHES, METRIC_RESOURCE_ENTRIES,
      METRIC_RESOURCE_CAPACITY_SHORT, METRIC_RESOURCE_USAGE, METRIC_2D_CACHES, METRIC_2D_ENTRIES,
      METRIC_2D_CAPACITY, METRIC_2D_USAGE, METRIC_3D_CACHES, METRIC_3D_ENTRIES, METRIC_3D_CAPACITY,
      METRIC_3D_USAGE, METRIC_OTHER_CACHES, METRIC_OTHER_ENTRIES, METRIC_OTHER_CAPACITY,
      METRIC_OTHER_USAGE, METRIC_ENTITY_SATURATION, METRIC_PAUSED, METRIC_PROGRESS, METRIC_TOTAL,
      METRIC_REMAINING, METRIC_THROUGHPUT, METRIC_ETA, METRIC_ELAPSED, METRIC_FAILED_CHUNKS,
      METRIC_PERMIT_WAIT, METRIC_ADAPTIVE_WAIT, METRIC_TERRAIN, METRIC_BIOME, METRIC_POST,
      METRIC_PERFECTION, METRIC_DECORATION, METRIC_UPDATES, METRIC_CAVES, METRIC_DEPOSITS,
      METRIC_CONTEXT_PREFILL, METRIC_CARVE_RESOLVE, METRIC_CARVE_APPLY, METRIC_LOADED_CHUNKS,
      METRIC_SATURATION, METRIC_GENERATION_LEASES, METRIC_IDLE, METRIC_ENGINE_RATE,
      METRIC_SESSIONS, METRIC_MENUS, METRIC_PREVIEWS, METRIC_VISIBLE, METRIC_BREAKS, METRIC_DROPS,
      METRIC_VEIN_FINDS, METRIC_VEIN_CACHE, METRIC_PDC_READS, METRIC_PDC_WRITES, METRIC_RULES,
      METRIC_WATCHED_JARS, METRIC_DIRTY_PLUGINS, METRIC_RELOADS, METRIC_LAST_RELOAD, METRIC_REMOTE_SLAVE,
      UNKNOWN_RENDERER_UNAVAILABLE, UNKNOWN_MAP_WAS, UNKNOWN_BOUND, UNKNOWN_MISSING,
      UNKNOWN_RESELECT_COMMAND, UNKNOWN_RESELECT, HEATMAP_NO_ACTIVITY, HEATMAP_QUIET,
      PIE_VIEWER_UNAVAILABLE, PIE_NO_DATA, PIE_TOTAL, PIE_UNIT_CHUNKS, PIE_UNIT_IMPACT, PIE_UNKNOWN,
      PIE_OTHER, ADAPT_MEASURED_UNAVAILABLE, ADAPT_GUARD_ONLY, ADAPT_NO_ACTIVITY, ADAPT_USE_ABILITIES,
      ADAPT_CALLBACK_VALUE, ADAPT_TOP_VALUE, PLUGIN_NO_IMPACT, PLUGIN_WAIT_FOR_DATA, PLUGIN_IMPACT_VALUE,
      SAMPLER_LOW_VALUE, SAMPLER_HIGH_VALUE
  );
  private static final Map<String, TextKey> HEALTH_KEYS = Map.ofEntries(
      Map.entry("LOCAL", STATUS_LOCAL),
      Map.entry("OFFLINE", STATUS_OFFLINE),
      Map.entry("MISSING", STATUS_MISSING),
      Map.entry("HEALTHY", STATUS_HEALTHY),
      Map.entry("ONLINE", STATUS_ONLINE),
      Map.entry("OK", STATUS_OK),
      Map.entry("DEGRADED", STATUS_DEGRADED),
      Map.entry("WARN", STATUS_WARN),
      Map.entry("UNAVAILABLE", STATUS_UNAVAILABLE),
      Map.entry("ERROR", STATUS_ERROR)
  );
  private static final Map<String, TextKey> TITLE_KEYS = Map.ofEntries(
      Map.entry("unknown", TITLE_REACT_MAP),
      Map.entry("react-metrics", TITLE_REACT_METRICS),
      Map.entry("adapt-metrics", TITLE_ADAPT_METRICS),
      Map.entry("wormholes-metrics", TITLE_WORMHOLES_METRICS),
      Map.entry("gloss-metrics", TITLE_GLOSS_METRICS),
      Map.entry("hiddenore-metrics", TITLE_HIDDENORE_METRICS),
      Map.entry("biletools-metrics", TITLE_BILETOOLS_METRICS),
      Map.entry("iris-metrics", TITLE_IRIS_OVERVIEW),
      Map.entry("iris-engine-activity", TITLE_IRIS_ENGINE_ACTIVITY),
      Map.entry("iris-engine-lifecycle", TITLE_IRIS_LIFECYCLE),
      Map.entry("iris-mantle-health", TITLE_IRIS_MANTLE_HEALTH),
      Map.entry("iris-cache-overview", TITLE_IRIS_CACHE_OVERVIEW),
      Map.entry("iris-cache-streams", TITLE_IRIS_STREAM_CACHES),
      Map.entry("iris-cache-other", TITLE_IRIS_OTHER_CACHES),
      Map.entry("iris-pregen-progress", TITLE_IRIS_PREGEN_PROGRESS),
      Map.entry("iris-pregen-health", TITLE_IRIS_PREGEN_HEALTH),
      Map.entry("iris-generation-pipeline", TITLE_IRIS_GENERATION_PIPELINE),
      Map.entry("iris-generation-features", TITLE_IRIS_GENERATION_FEATURES),
      Map.entry("iris-world-overview", TITLE_IRIS_WORLD_OVERVIEW),
      Map.entry("iris-world-engine", TITLE_IRIS_WORLD_ENGINE),
      Map.entry("iris-world-mantle", TITLE_IRIS_WORLD_MANTLE),
      Map.entry("iris-world-generation", TITLE_IRIS_WORLD_GENERATION),
      Map.entry("iris-world-features", TITLE_IRIS_WORLD_FEATURES),
      Map.entry("iris-world-pregen", TITLE_IRIS_WORLD_PREGEN),
      Map.entry("iris-world-pregen-time", TITLE_IRIS_WORLD_PREGEN_TIME),
      Map.entry("adapt-ability-impact-list-map", TITLE_ADAPT_CALLBACK_COST),
      Map.entry("plugin-event-impact-list-map", TITLE_PLUGIN_IMPACT),
      Map.entry("iris-world-chunk-share-pie-map", TITLE_WORLD_CHUNK_SHARE),
      Map.entry("plugin-event-impact-pie-map", TITLE_PLUGIN_IMPACT_SHARE),
      Map.entry("iris-biome-chunk-share-pie-map", TITLE_BIOME_CHUNK_SHARE),
      Map.entry("chunk-sampler-map", TITLE_CHUNK_COST_HEAT),
      Map.entry("adapt-runtime-pressure-overlay", TITLE_ADAPT_PRESSURE),
      Map.entry("redstone-activity-heatmap", TITLE_REDSTONE_ACTIVITY),
      Map.entry("entity-pressure-heatmap", TITLE_ENTITY_PRESSURE),
      Map.entry("player-impact-overlay", TITLE_PLAYER_IMPACT),
      Map.entry("tick-spike-origin-replay-map", TITLE_TICK_SPIKE_REPLAY),
      Map.entry("iris-generation-pressure-overlay", TITLE_IRIS_PRESSURE),
      Map.entry("hopper-container-throughput-map", TITLE_HOPPER_THROUGHPUT),
      Map.entry("chunk-load-gen-cost-map", TITLE_CHUNK_LOAD_GEN_COST)
  );

  private RendererMessages() {
  }

  public static void addTo(MessageCatalog.Builder builder) {
    builder.addAll(KEYS);
  }

  public static String health(String status) {
    if (status == null || status.isBlank()) {
      return ReactLanguage.raw(STATUS_MISSING);
    }
    TextKey key = HEALTH_KEYS.get(status.toUpperCase(Locale.ROOT));
    return key == null ? status : ReactLanguage.raw(key);
  }

  public static String localizedTitle(String rendererId, String fallback) {
    TextKey key = TITLE_KEYS.get(rendererId);
    return key == null ? fallback : ReactLanguage.raw(key);
  }

  public static String metricLine(TextKey labelKey, String value) {
    return metricLine(ReactLanguage.raw(labelKey), value);
  }

  public static String metricLine(String label, String value) {
    return ReactLanguage.raw(
        METRIC_LINE,
        MessageArgument.untrusted("label", label),
        MessageArgument.untrusted("value", value)
    );
  }

  private static TextKey key(String id, String english) {
    return TextKey.of("renderer." + id, english);
  }

  private static TextKey title(String id, String english) {
    return key("title." + id, english);
  }

  private static TextKey metric(String id, String english) {
    return key("metric." + id, english);
  }
}
