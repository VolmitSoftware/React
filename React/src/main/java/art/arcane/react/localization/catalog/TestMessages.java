package art.arcane.react.localization.catalog;

import art.arcane.react.localization.ReactLanguage;
import art.arcane.volmlib.util.localization.MessageArgument;
import art.arcane.volmlib.util.localization.MessageCatalog;
import art.arcane.volmlib.util.localization.TextKey;

import java.util.Map;

public final class TestMessages {
  public static final TextKey RESULT_PASS = TextKey.of("test.result.pass", "  <green>[PASS]</green> <white>{subsystem}/{name}</white><gray> — {detail}</gray>");
  public static final TextKey RESULT_FAIL = TextKey.of("test.result.fail", "  <red>[FAIL]</red> <white>{subsystem}/{name}</white><gray> — {detail}</gray>");
  public static final TextKey RESULT_WARN = TextKey.of("test.result.warn", "  <yellow>[WARN]</yellow> <white>{subsystem}/{name}</white><gray> — {detail}</gray>");
  public static final TextKey RESULT_SKIP = TextKey.of("test.result.skip", "  <yellow>[SKIP]</yellow> <white>{subsystem}/{name}</white><gray> — {detail}</gray>");
  public static final TextKey RESULT_INFO = TextKey.of("test.result.info", "  <gray>[INFO]</gray> <white>{subsystem}/{name}</white><gray> — {detail}</gray>");
  public static final TextKey ACTION_CONTROLLER_UNAVAILABLE = TextKey.of("test.action.controller_unavailable", "Action controller is unavailable; cannot run the direct action suite.");
  public static final TextKey ACTION_NO_WORLDS = TextKey.of("test.action.no_worlds", "No worlds are loaded; cannot run actions around a world spawn.");
  public static final TextKey ACTION_NOT_REGISTERED = TextKey.of("test.action.not_registered", "Action is not registered.");
  public static final TextKey ACTION_DISABLED = TextKey.of("test.action.disabled", "Action is disabled in config.");
  public static final TextKey ACTION_PREPARE_FAILED = TextKey.of("test.action.prepare_failed", "Failed to prepare params: {reason}");
  public static final TextKey ACTION_CREATE_FAILED = TextKey.of("test.action.create_failed", "Failed to create action: {reason}");
  public static final TextKey ACTION_COMPLETION_FAILED = TextKey.of("test.action.completion_failed", "Completion handler threw: {reason}");
  public static final TextKey BRIDGE_REGISTRY_UNAVAILABLE = TextKey.of("test.bridge.registry_unavailable", "Bridge registry unavailable (plugin not fully started).");
  public static final TextKey BRIDGE_HEALTHY = TextKey.of("test.bridge.healthy", "{available} bridge handle(s) available, 0 unavailable.");
  public static final TextKey BRIDGE_DEGRADED = TextKey.of("test.bridge.degraded", "Bridge degraded or measurement-only on this Minecraft version; expected off bundled versions ({unavailable} unavailable: {detail}; reason: {reason}).");
  public static final TextKey BRIDGE_UNAVAILABLE = TextKey.of("test.bridge.unavailable", "{unavailable} bridge hook(s) unavailable on a supported bundled version: {detail}");
  public static final TextKey BRIDGE_NONE = TextKey.of("test.bridge.none", "none");
  public static final TextKey BRIDGE_NO_WORLDS = TextKey.of("test.bridge.no_worlds", "No worlds loaded; cannot run the live gravity landing test from console.");
  public static final TextKey BRIDGE_NO_HEADROOM = TextKey.of("test.bridge.no_headroom", "No headroom above y={ground_y} in world '{world}' to drop a test block.");
  public static final TextKey BRIDGE_LANDING_PASS = TextKey.of("test.bridge.landing_pass", "SAND dropped at y={spawn_y} over ground y={ground_y} landed or despawned within 100 ticks (gravity hook and landing path OK).");
  public static final TextKey BRIDGE_LANDING_FAIL = TextKey.of("test.bridge.landing_fail", "SAND dropped at y={spawn_y} is still falling after 100 ticks (stuck mid-air).");
  public static final TextKey BRIDGE_LIVE_UNAVAILABLE = TextKey.of("test.bridge.live_unavailable", "Live drop unavailable on this server or thread: {reason}");
  public static final TextKey ERROR_SCAN_FAILED = TextKey.of("test.error.scan_failed", "scan failed: {reason}");
  public static final TextKey ERROR_ASYNC_UNAVAILABLE = TextKey.of("test.error.async_unavailable", "Async log scan could not be scheduled: {reason}");
  public static final TextKey ERROR_REPORTED_FAIL = TextKey.of("test.error.reported_fail", "{since_run} React.reportError call(s) during this run ({total} total since startup).");
  public static final TextKey ERROR_REPORTED_PASS = TextKey.of("test.error.reported_pass", "No React.reportError calls during this run ({total} total since startup).");
  public static final TextKey ERROR_LOG_NOT_SCANNED = TextKey.of("test.error.log_not_scanned", "Server log not scanned ({note}); relying on error counter.");
  public static final TextKey ERROR_CROSSED_MIDNIGHT = TextKey.of("test.error.crossed_midnight", "Run window crossed midnight; skipped time-filtered log scan.");
  public static final TextKey ERROR_LOG_FAIL = TextKey.of("test.error.log_fail", "{tick_crashes} tick crash + {react_errors} React error line(s) in latest.log during run.");
  public static final TextKey ERROR_LOG_PASS = TextKey.of("test.error.log_pass", "No React error or crash lines in latest.log during run ({lines} lines scanned).");
  public static final TextKey ERROR_FILE_MISSING = TextKey.of("test.error.file_missing", "file missing or unreadable");
  public static final TextKey FEATURE_CONTROLLER_UNAVAILABLE = TextKey.of("test.feature.controller_unavailable", "FeatureController unavailable.");
  public static final TextKey FEATURE_REGISTRY_UNAVAILABLE = TextKey.of("test.feature.registry_unavailable", "Feature registry not initialized.");
  public static final TextKey FEATURE_NONE = TextKey.of("test.feature.none", "No enabled features to cycle.");
  public static final TextKey FEATURE_CYCLE_PASS = TextKey.of("test.feature.cycle_pass", "Deactivate, activate, and deactivate cycle clean; activeFeatures tracked every transition.");
  public static final TextKey FEATURE_CYCLE_FAIL = TextKey.of("test.feature.cycle_fail", "activeFeatures map inconsistent: removed1={removed_first} added={added} removed2={removed_second}");
  public static final TextKey FEATURE_AUTOSAVE_NO_WORLDS = TextKey.of("test.feature.autosave_no_worlds", "No worlds loaded to verify autosave restoration.");
  public static final TextKey FEATURE_AUTOSAVE_PASS = TextKey.of("test.feature.autosave_pass", "All {total} worlds retained baseline isAutoSave after full feature lifecycle cycling.");
  public static final TextKey FEATURE_AUTOSAVE_FAIL = TextKey.of("test.feature.autosave_fail", "Autosave drift after cycling: {matched}/{total} worlds match baseline (data-loss guard).");
  public static final TextKey FEATURE_PHASE_FAILED = TextKey.of("test.feature.phase_failed", "{phase} threw {reason}");
  public static final TextKey MAP_CONTROLLER_UNAVAILABLE = TextKey.of("test.map.controller_unavailable", "MapController is not registered.");
  public static final TextKey MAP_REGISTRY_EMPTY = TextKey.of("test.map.registry_empty", "Renderer registry is empty.");
  public static final TextKey MAP_NULL_RENDERER = TextKey.of("test.map.null_renderer", "<null renderer>");
  public static final TextKey MAP_BLANK_IDS = TextKey.of("test.map.blank_ids", "Renderers with blank id: {renderers}");
  public static final TextKey MAP_REGISTRY_PASS = TextKey.of("test.map.registry_pass", "{count} renderers registered; all ids are non-blank.");
  public static final TextKey MAP_TILE_COUNT_FAIL = TextKey.of("test.map.tile_count_fail", "2x2 same-renderer wall produced {count} tiled cells; expected 4.");
  public static final TextKey MAP_DIMENSIONS_MISSING = TextKey.of("test.map.dimensions_missing", "missing");
  public static final TextKey MAP_TILE_GRID_FAIL = TextKey.of("test.map.tile_grid_fail", "2x2 wall did not resolve to a 2x2 grid (got {dimensions}).");
  public static final TextKey MAP_TILE_PASS = TextKey.of("test.map.tile_pass", "2x2 same-renderer wall tiled into a 2x2 grid across 4 maps.");
  public static final TextKey MAP_HOLE_FAIL = TextKey.of("test.map.hole_fail", "Non-rectangular wall with a hole was tiled into {count} cells; expected rejection.");
  public static final TextKey MAP_HOLE_PASS = TextKey.of("test.map.hole_pass", "Non-rectangular wall with a hole was rejected (no tiling).");
  public static final TextKey MAP_BLANK_CANVAS = TextKey.of("test.map.blank_canvas", "Renderer '{renderer}' produced a fully blank canvas.");
  public static final TextKey MAP_NONDETERMINISTIC = TextKey.of("test.map.nondeterministic", "Renderer '{renderer}' produced non-deterministic pixels across two renders.");
  public static final TextKey MAP_RENDER_PASS = TextKey.of("test.map.render_pass", "Renderer '{renderer}' rendered {pixels} non-blank pixels identically twice on a synthetic {width}x{height} canvas.");
  public static final TextKey MAP_HEADLESS_UNAVAILABLE = TextKey.of("test.map.headless_unavailable", "Headless pixel render unavailable: {reason}");
  public static final TextKey MONITOR_CONTROLLER_UNAVAILABLE = TextKey.of("test.monitor.controller_unavailable", "SampleController is not available.");
  public static final TextKey MONITOR_REGISTRY_UNAVAILABLE = TextKey.of("test.monitor.registry_unavailable", "Sampler registry is not initialized.");
  public static final TextKey MONITOR_NONE = TextKey.of("test.monitor.none", "No samplers are registered.");
  public static final TextKey MONITOR_READ_FAILED = TextKey.of("test.monitor.read_failed", "sample() threw {reason}");
  public static final TextKey MONITOR_NO_INTEGRATIONS = TextKey.of("test.monitor.no_integrations", "No Iris, Adapt, Wormholes, HoloUi, HiddenOre, or BileTools integration samplers present.");
  public static final TextKey MONITOR_INTEGRATIONS = TextKey.of("test.monitor.integrations", "{count} integration sampler(s): {samplers}");
  public static final TextKey MONITOR_NONFINITE = TextKey.of("test.monitor.nonfinite", "{offenders} of {total} sampler(s) returned a non-finite sample(): {samplers}");
  public static final TextKey MONITOR_ALL_FINITE = TextKey.of("test.monitor.all_finite", "{finite} samplers, all finite.");
  public static final TextKey MONITOR_ALL_FINITE_UNREADABLE = TextKey.of("test.monitor.all_finite_unreadable", "{finite} samplers, all finite ({unreadable} unreadable).");
  public static final TextKey LOAD_PASS_ON = TextKey.of("test.result.load.pass_on", "Load test pass A (React ON): {players} synthetic players, {seconds}s.");
  public static final TextKey LOAD_PASS_OFF = TextKey.of("test.result.load.pass_off", "Load test pass B (React OFF baseline): {seconds}s.");
  public static final TextKey LOAD_ALL_SLOS_MET = TextKey.of("test.load.all_slos_met", "all SLOs met");
  public static final TextKey LOAD_PASS_DETAIL = TextKey.of("test.load.pass_detail", "avgMSPT={avg_mspt} p95={p95} maxTickGap={max_tick}ms avgTPS={avg_tps} heap={heap_start}->{heap_end}MB exceptions={exceptions}");
  public static final TextKey LOAD_DELTA_DETAIL = TextKey.of("test.load.delta_detail", "React vs baseline: dMSPT={delta_mspt}ms dTPS={delta_tps} dHeapEnd={delta_heap}MB");
  public static final TextKey SLO_LOW_TPS = TextKey.of("test.load.slo.low_tps", "low TPS: avg {average} < {minimum}");
  public static final TextKey SLO_HIGH_MSPT = TextKey.of("test.load.slo.high_mspt", "high MSPT: avg {average}ms >= {maximum}ms");
  public static final TextKey SLO_FREEZE = TextKey.of("test.load.slo.freeze", "main-thread freeze: {tick}ms tick > {maximum}ms");
  public static final TextKey SLO_OOM = TextKey.of("test.load.slo.oom", "OutOfMemory observed during run");
  public static final TextKey SLO_HEAP_GROWTH = TextKey.of("test.load.slo.heap_growth", "unbounded heap growth: +{growth}MB with no GC recovery");
  public static final TextKey SLO_EXCEPTIONS = TextKey.of("test.load.slo.exceptions", "React-path exceptions: {count}");
  public static final TextKey NAME_SAMPLER_READ = TextKey.of("test.label.name.sampler_read", "Sampler read [{sampler}]");

  private static final Map<String, TextKey> SUBSYSTEM_LABELS = Map.ofEntries(
      label("subsystem.actions", "actions", "Actions"),
      label("subsystem.bridge", "bridge", "Bridge"),
      label("subsystem.errors", "errors", "Errors"),
      label("subsystem.features", "features", "Features"),
      label("subsystem.maps", "maps", "Maps"),
      label("subsystem.monitoring", "monitoring", "Monitoring"),
      label("subsystem.loadtest", "loadtest", "Load test")
  );
  private static final Map<String, TextKey> NAME_LABELS = Map.ofEntries(
      label("name.exception", "exception", "Exception"),
      label("name.action_suite", "action-suite", "Action suite"),
      label("name.health", "health", "Health"),
      label("name.falling_block", "falling-block", "Falling block"),
      label("name.reported_errors", "reported-errors", "Reported errors"),
      label("name.log_scan", "log-scan", "Log scan"),
      label("name.lifecycle", "lifecycle", "Lifecycle"),
      label("name.autosave_restore_guard", "autosave-restore-guard", "Autosave restore guard"),
      label("name.registry", "registry", "Registry"),
      label("name.megamap_tile", "megamap-tile", "Megamap tile"),
      label("name.megamap_hole", "megamap-hole", "Megamap hole"),
      label("name.render_determinism", "render-determinism", "Render determinism"),
      label("name.sampler_finiteness", "Sampler finiteness", "Sampler finiteness"),
      label("name.integration_samplers", "Integration samplers", "Integration samplers"),
      label("name.slo_gate", "slo-gate", "SLO gate"),
      label("name.react_on", "react-on", "React enabled"),
      label("name.react_off_baseline", "react-off-baseline", "React disabled baseline"),
      label("name.react_overhead_delta", "react-overhead-delta", "React overhead delta")
  );

  private TestMessages() {
  }

  public static void addTo(MessageCatalog.Builder builder) {
    builder.add(RESULT_PASS);
    builder.add(RESULT_FAIL);
    builder.add(RESULT_WARN);
    builder.add(RESULT_SKIP);
    builder.add(RESULT_INFO);
    builder.add(ACTION_CONTROLLER_UNAVAILABLE);
    builder.add(ACTION_NO_WORLDS);
    builder.add(ACTION_NOT_REGISTERED);
    builder.add(ACTION_DISABLED);
    builder.add(ACTION_PREPARE_FAILED);
    builder.add(ACTION_CREATE_FAILED);
    builder.add(ACTION_COMPLETION_FAILED);
    builder.add(BRIDGE_REGISTRY_UNAVAILABLE);
    builder.add(BRIDGE_HEALTHY);
    builder.add(BRIDGE_DEGRADED);
    builder.add(BRIDGE_UNAVAILABLE);
    builder.add(BRIDGE_NONE);
    builder.add(BRIDGE_NO_WORLDS);
    builder.add(BRIDGE_NO_HEADROOM);
    builder.add(BRIDGE_LANDING_PASS);
    builder.add(BRIDGE_LANDING_FAIL);
    builder.add(BRIDGE_LIVE_UNAVAILABLE);
    builder.add(ERROR_SCAN_FAILED);
    builder.add(ERROR_ASYNC_UNAVAILABLE);
    builder.add(ERROR_REPORTED_FAIL);
    builder.add(ERROR_REPORTED_PASS);
    builder.add(ERROR_LOG_NOT_SCANNED);
    builder.add(ERROR_CROSSED_MIDNIGHT);
    builder.add(ERROR_LOG_FAIL);
    builder.add(ERROR_LOG_PASS);
    builder.add(ERROR_FILE_MISSING);
    builder.add(FEATURE_CONTROLLER_UNAVAILABLE);
    builder.add(FEATURE_REGISTRY_UNAVAILABLE);
    builder.add(FEATURE_NONE);
    builder.add(FEATURE_CYCLE_PASS);
    builder.add(FEATURE_CYCLE_FAIL);
    builder.add(FEATURE_AUTOSAVE_NO_WORLDS);
    builder.add(FEATURE_AUTOSAVE_PASS);
    builder.add(FEATURE_AUTOSAVE_FAIL);
    builder.add(FEATURE_PHASE_FAILED);
    builder.add(MAP_CONTROLLER_UNAVAILABLE);
    builder.add(MAP_REGISTRY_EMPTY);
    builder.add(MAP_NULL_RENDERER);
    builder.add(MAP_BLANK_IDS);
    builder.add(MAP_REGISTRY_PASS);
    builder.add(MAP_TILE_COUNT_FAIL);
    builder.add(MAP_DIMENSIONS_MISSING);
    builder.add(MAP_TILE_GRID_FAIL);
    builder.add(MAP_TILE_PASS);
    builder.add(MAP_HOLE_FAIL);
    builder.add(MAP_HOLE_PASS);
    builder.add(MAP_BLANK_CANVAS);
    builder.add(MAP_NONDETERMINISTIC);
    builder.add(MAP_RENDER_PASS);
    builder.add(MAP_HEADLESS_UNAVAILABLE);
    builder.add(MONITOR_CONTROLLER_UNAVAILABLE);
    builder.add(MONITOR_REGISTRY_UNAVAILABLE);
    builder.add(MONITOR_NONE);
    builder.add(MONITOR_READ_FAILED);
    builder.add(MONITOR_NO_INTEGRATIONS);
    builder.add(MONITOR_INTEGRATIONS);
    builder.add(MONITOR_NONFINITE);
    builder.add(MONITOR_ALL_FINITE);
    builder.add(MONITOR_ALL_FINITE_UNREADABLE);
    builder.add(LOAD_PASS_ON);
    builder.add(LOAD_PASS_OFF);
    builder.add(LOAD_ALL_SLOS_MET);
    builder.add(LOAD_PASS_DETAIL);
    builder.add(LOAD_DELTA_DETAIL);
    builder.add(SLO_LOW_TPS);
    builder.add(SLO_HIGH_MSPT);
    builder.add(SLO_FREEZE);
    builder.add(SLO_OOM);
    builder.add(SLO_HEAP_GROWTH);
    builder.add(SLO_EXCEPTIONS);
    builder.add(NAME_SAMPLER_READ);
    builder.addAll(SUBSYSTEM_LABELS.values());
    builder.addAll(NAME_LABELS.values());
  }

  public static String subsystemLabel(String subsystem) {
    TextKey key = SUBSYSTEM_LABELS.get(subsystem);
    return key == null ? subsystem : ReactLanguage.raw(key);
  }

  public static String nameLabel(String name) {
    TextKey key = NAME_LABELS.get(name);
    if (key != null) {
      return ReactLanguage.raw(key);
    }
    if (name != null && name.startsWith("Sampler read [") && name.endsWith("]")) {
      return ReactLanguage.raw(
          NAME_SAMPLER_READ,
          MessageArgument.untrusted("sampler", name.substring(14, name.length() - 1))
      );
    }
    return name;
  }

  private static Map.Entry<String, TextKey> label(String id, String source, String english) {
    return Map.entry(source, TextKey.of("test.label." + id, english));
  }
}
