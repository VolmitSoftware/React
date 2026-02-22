/*
 *  Copyright (c) 2016-2025 Arcane Arts (Volmit Software)
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 *
 */

package art.arcane.react.core.gui;

import art.arcane.react.React;
import art.arcane.react.api.rendering.ReactRenderer;
import art.arcane.react.api.rendering.RendererUnknown;
import art.arcane.react.api.sampler.Sampler;
import art.arcane.react.core.controller.MapController;
import art.arcane.react.util.format.C;
import art.arcane.react.util.inventorygui.UIStaticDecorator;
import art.arcane.react.util.common.scheduling.J;
import art.arcane.volmlib.util.data.MaterialBlock;
import art.arcane.volmlib.util.inventorygui.UIElement;
import art.arcane.volmlib.util.inventorygui.UIWindow;
import art.arcane.volmlib.util.inventorygui.WindowResolution;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.*;

public final class ReactMapGUI {
  private static final int PAGE_JUMP = 5;

  private ReactMapGUI() {
  }

  public static void open(Player player) {
    open(player, 0);
  }

  public static void open(Player player, int page) {
    if (player == null) {
      return;
    }

    if (!Bukkit.isPrimaryThread()) {
      int safePage = page;
      J.runEntity(player, () -> open(player, safePage));
      return;
    }

    MapController controller = React.controller(MapController.class);
    if (controller == null || controller.getRenderers() == null) {
      player.sendMessage(C.RED + "Renderer controller is unavailable.");
      return;
    }

    List<ReactRenderer> renderers = getRenderers(controller);
    if (renderers.isEmpty()) {
      player.sendMessage(C.RED + "No map renderers are available.");
      return;
    }

    playPageTurn(player);
    PageLayout layout = pageLayout(renderers.size());
    int currentPage = clampPage(page, layout.pageCount());
    int start = currentPage * layout.itemsPerPage();
    int end = Math.min(renderers.size(), start + layout.itemsPerPage());

    UIWindow window = new UIWindow(React.instance, player);
    window.setResolution(WindowResolution.W9_H6);
    window.setViewportHeight(layout.rows());
    window.setTitle("React Maps");
    window.setDecorator(new UIStaticDecorator(new UIElement("bg")
        .setMaterial(new MaterialBlock(Material.BLACK_STAINED_GLASS_PANE))));

    for (int row = 0; row < layout.contentRows(); row++) {
      int rowStart = start + (row * 9);
      if (rowStart >= end) {
        break;
      }

      int rowCount = Math.min(9, end - rowStart);
      for (int i = 0; i < rowCount; i++) {
        ReactRenderer renderer = renderers.get(rowStart + i);
        String normalizedId = normalizeRendererId(renderer);
        String scopeTag = rendererScopeTag(normalizedId);
        int w = centeredPosition(i, rowCount);
        int h = row;
        UIElement element = new UIElement("map-renderer-" + renderer.getId())
            .setMaterial(new MaterialBlock(materialFor(renderer)))
            .setName(C.WHITE + displayName(renderer.getId()) + C.DARK_GRAY + " [" + scopeTag + "]");
        for (String loreLine : loreFor(renderer)) {
          element.addLore(loreLine);
        }

        window.setElement(w, h, element
            .onLeftClick((e) -> {
              controller.openRenderer(player, renderer);
              player.sendMessage(C.GREEN + "Selected map: " + C.WHITE + displayName(renderer.getId()) + C.DARK_GRAY + " [" + scopeTag + "]");
              player.closeInventory();
            })
            .onShiftLeftClick((e) -> {
              controller.giveMapToInventory(player, renderer);
              player.sendMessage(C.GREEN + "Added map: " + C.WHITE + displayName(renderer.getId()) + C.DARK_GRAY + " [" + scopeTag + "]");
            })
            .onShiftRightClick((e) -> {
              controller.giveMapToInventory(player, renderer);
              player.sendMessage(C.GREEN + "Added map: " + C.WHITE + displayName(renderer.getId()) + C.DARK_GRAY + " [" + scopeTag + "]");
            }));
      }
    }

    if (layout.pagination()) {
      int controlRow = layout.controlRow();
      int jumpBack = Math.max(0, currentPage - PAGE_JUMP);
      int jumpForward = Math.min(layout.pageCount() - 1, currentPage + PAGE_JUMP);

      if (currentPage > 0) {
        window.setElement(2, controlRow, new UIElement("map-prev")
            .setMaterial(new MaterialBlock(Material.ARROW))
            .setName(C.WHITE + "Prev")
            .onLeftClick((e) -> open(player, currentPage - 1))
            .onRightClick((e) -> open(player, jumpBack)));
      }

      if (currentPage < layout.pageCount() - 1) {
        window.setElement(4, controlRow, new UIElement("map-next")
            .setMaterial(new MaterialBlock(Material.ARROW))
            .setName(C.WHITE + "Next")
            .onLeftClick((e) -> open(player, currentPage + 1))
            .onRightClick((e) -> open(player, jumpForward)));
      }

      int from = start + 1;
      int to = end;
      window.setElement(3, controlRow, new UIElement("map-page-info")
          .setMaterial(new MaterialBlock(Material.PAPER))
          .setName(C.AQUA + "Page " + (currentPage + 1) + "/" + layout.pageCount()
              + C.GRAY + " (" + from + "-" + to + ")"));
    }

    window.open();
  }

  private static List<ReactRenderer> getRenderers(MapController controller) {
    Map<String, ReactRenderer> unique = new LinkedHashMap<>();
    for (ReactRenderer renderer : controller.getRenderers().values()) {
      if (renderer == null || renderer.getId() == null || renderer.getId().isBlank()) {
        continue;
      }

      if (normalizeRendererId(renderer).equals(RendererUnknown.ID)) {
        continue;
      }

      unique.putIfAbsent(renderer.getId(), renderer);
    }

    List<ReactRenderer> renderers = new ArrayList<>(unique.values());
    renderers.sort((a, b) -> {
      String aNormalized = normalizeRendererId(a);
      String bNormalized = normalizeRendererId(b);
      RendererGroup aGroup = rendererGroup(a, aNormalized);
      RendererGroup bGroup = rendererGroup(b, bNormalized);
      int groupCompare = Integer.compare(aGroup.order(), bGroup.order());
      if (groupCompare != 0) {
        return groupCompare;
      }
      return normalizeSortKey(a.getId()).compareTo(normalizeSortKey(b.getId()));
    });
    return renderers;
  }

  private static Material materialFor(ReactRenderer renderer) {
    String normalizedId = normalizeRendererId(renderer);
    return rendererGroup(renderer, normalizedId).icon();
  }

  private static int clampPage(int page, int pageCount) {
    if (pageCount <= 0) {
      return 0;
    }
    return Math.max(0, Math.min(page, pageCount - 1));
  }

  private static PageLayout pageLayout(int totalEntries) {
    int safeEntries = Math.max(0, totalEntries);
    int rows = Math.max(1, Math.min(6, (int) Math.ceil(Math.max(1, safeEntries) / 9D)));
    int itemsPerPage = rows * 9;
    if (safeEntries <= itemsPerPage) {
      return new PageLayout(rows, rows, itemsPerPage, 1, false, -1);
    }

    int contentRows = 5;
    rows = contentRows + 1;
    itemsPerPage = contentRows * 9;
    int pageCount = Math.max(1, (int) Math.ceil(safeEntries / (double) itemsPerPage));
    return new PageLayout(rows, contentRows, itemsPerPage, pageCount, true, rows - 1);
  }

  private static int centeredPosition(int index, int rowCount) {
    int safeCount = Math.max(1, Math.min(9, rowCount));
    int safeIndex = Math.max(0, Math.min(index, safeCount - 1));
    int start = -(safeCount / 2);
    if ((safeCount & 1) == 1) {
      start = -((safeCount - 1) / 2);
    }
    return start + safeIndex;
  }

  private static String displayName(String key) {
    if (key == null || key.isBlank()) {
      return "Unknown";
    }

    String spaced = key
        .replace('_', ' ')
        .replace('-', ' ')
        .replaceAll("([a-z])([A-Z])", "$1 $2")
        .trim();
    if (spaced.isBlank()) {
      return key;
    }
    return Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
  }

  private static List<String> loreFor(ReactRenderer renderer) {
    List<String> lore = new ArrayList<>();
    String id = renderer == null ? "unknown" : Objects.toString(renderer.getId(), "unknown");
    String normalizedId = normalizeRendererId(renderer);
    RendererGroup group = rendererGroup(renderer, normalizedId);
    lore.add(C.DARK_GRAY + "Scope: " + C.GOLD + rendererScopeLabel(normalizedId));
    lore.add(C.DARK_GRAY + "Group: " + C.YELLOW + group.label());
    lore.add(C.DARK_GRAY + "Type: " + C.AQUA + rendererType(renderer, normalizedId));
    lore.add(C.GRAY + rendererSummary(renderer, normalizedId));
    String detail = rendererDetail(renderer, normalizedId);
    if (detail != null && !detail.isBlank()) {
      lore.add(C.DARK_GRAY + detail);
    }
    lore.add(C.DARK_GRAY + "ID: " + C.GRAY + id);
    lore.add(C.GREEN + "Left Click: " + C.GRAY + "Select this monitor");
    lore.add(C.GREEN + "Shift + Click: " + C.GRAY + "Add map and keep menu open");
    return lore;
  }

  private static String rendererType(ReactRenderer renderer, String normalizedId) {
    if (renderer instanceof Sampler) {
      return "Sampler Trend";
    }
    if ("iris-metrics".equals(normalizedId) || "adapt-metrics".equals(normalizedId) || "react-metrics".equals(normalizedId)) {
      return "Integration Metrics";
    }
    if (normalizedId.contains("-list-map")) {
      return "Ranked List";
    }
    if (normalizedId.contains("-pie-map")) {
      return "Pie Chart";
    }
    if (normalizedId.endsWith("-map") || normalizedId.endsWith("-heatmap") || normalizedId.endsWith("-overlay")) {
      return "World Overlay";
    }
    return "Renderer";
  }

  private static String rendererSummary(ReactRenderer renderer, String normalizedId) {
    if (renderer instanceof Sampler) {
      return samplerSummary(normalizedId);
    }

    return switch (normalizedId) {
      case "chunk-sampler-map" ->
          "Chunk heatmap of React observer cost around your current position.";
      case "entity-pressure-heatmap" ->
          "Chunk heatmap of entity pressure; hotter chunks carry heavier mob or item load.";
      case "redstone-activity-heatmap" ->
          "Chunk heatmap of redstone update pressure and clock-heavy circuitry.";
      case "chunk-load-gen-cost-map" ->
          "Chunk heatmap of load and generation cost using load/gen rate and ms pressure.";
      case "hopper-container-throughput-map" ->
          "Chunk heatmap of hopper transfer throughput to locate transport bottlenecks.";
      case "player-impact-overlay" ->
          "Composite pressure map with per-player markers showing likely world impact.";
      case "tick-spike-origin-replay-map" ->
          "Replay map of recent spike origins with decaying heat over nearby chunks.";
      case "iris-generation-pressure-overlay" ->
          "Iris-aware pressure overlay using pregen queue and chunk stream latency.";
      case "adapt-runtime-pressure-overlay" ->
          "Adapt-aware pressure overlay using session load and ability operation rate.";
      case "iris-world-chunk-share-pie-map" ->
          "Pie chart showing chunk-share distribution across all loaded server worlds.";
      case "iris-biome-chunk-share-pie-map" ->
          "Pie chart showing chunk-share distribution across biomes in the map world.";
      case "plugin-event-impact-pie-map" ->
          "Pie chart showing rolling plugin event-impact share over time.";
      case "plugin-event-impact-list-map" ->
          "Ranked list of plugins by rolling event-impact score.";
      case "iris-metrics" ->
          "Iris integration panel for pregen queue depth and active stream latency.";
      case "adapt-metrics" ->
          "Adapt integration panel for session load, ability ops/min, and world policy latency.";
      case "react-metrics" ->
          "React local metrics panel for TPS, tick latency, incident pressure, and queue health.";
      case "unknown" ->
          "Fallback renderer shown when a specific monitor cannot be resolved.";
      default -> {
        if (normalizedId.startsWith("iris-")) {
          yield "Iris integration renderer for " + displayName(normalizedId) + ".";
        }
        if (normalizedId.startsWith("adapt-")) {
          yield "Adapt integration renderer for " + displayName(normalizedId) + ".";
        }
        if (normalizedId.startsWith("react-")) {
          yield "React local metrics renderer for " + displayName(normalizedId) + ".";
        }
        yield "Map renderer for " + displayName(normalizedId) + ".";
      }
    };
  }

  private static String rendererDetail(ReactRenderer renderer, String normalizedId) {
    if (renderer instanceof Sampler) {
      return "Live graph with current, minimum, and maximum values.";
    }

    return switch (normalizedId) {
      case "chunk-sampler-map",
           "entity-pressure-heatmap",
           "redstone-activity-heatmap",
           "chunk-load-gen-cost-map",
           "hopper-container-throughput-map",
           "player-impact-overlay",
           "tick-spike-origin-replay-map",
           "iris-generation-pressure-overlay",
           "adapt-runtime-pressure-overlay" ->
          "Hotter colors indicate more pressure in the sampled chunk.";
      case "iris-metrics" ->
          "Monitors: queue backlog; stream ms appears only while Iris pregen is active.";
      case "adapt-metrics" ->
          "Monitors: session load, ability throughput, policy latency.";
      case "react-metrics" ->
          "Monitors: TPS, tick ms, incident score, jobs queue, scheduler backlog.";
      case "iris-world-chunk-share-pie-map" ->
          "Each slice represents the percent of loaded chunks per world.";
      case "iris-biome-chunk-share-pie-map" ->
          "Each slice represents the percent of loaded chunks per biome (Iris custom names when available).";
      case "plugin-event-impact-pie-map" ->
          "Each slice represents the rolling share of plugin event cost over recent windows.";
      case "plugin-event-impact-list-map" ->
          "Rows are ordered from highest rolling plugin impact to lowest.";
      default -> null;
    };
  }

  private static String samplerSummary(String normalizedId) {
    return switch (normalizedId) {
      case "tick-time" ->
          "Average server MSPT (milliseconds per tick) across recent samples.";
      case "ticks-per-second" ->
          "Effective TPS trend from the live server tick loop.";
      case "tick-ms-p50" ->
          "Median tick time to show normal baseline server load.";
      case "tick-ms-p95" ->
          "95th percentile tick time to expose high-tail latency spikes.";
      case "tick-ms-p99" ->
          "99th percentile tick time for worst-case tick latency outliers.";
      case "tick-spike-rate" ->
          "Rate of severe tick spikes crossing critical tick-time thresholds.";
      case "incident-score" ->
          "Composite incident score built from multiple pressure samplers.";
      case "memory-used" -> "Current JVM heap memory usage.";
      case "memory-free" -> "Available JVM heap memory before further growth.";
      case "memory-pressure" ->
          "Heap pressure ratio used by React to detect memory saturation.";
      case "memory-garbage" ->
          "Estimated reclaimable heap memory pending garbage collection.";
      case "memory-used-after-gc" ->
          "Post-GC heap footprint trend after recent collections.";
      case "gc-time-percent" ->
          "Percent of time spent in GC relative to runtime.";
      case "gc-pause-p95" ->
          "95th percentile garbage collection pause duration.";
      case "player-ping-p95" ->
          "95th percentile player ping to reveal network latency tails.";
      case "ping-jitter" ->
          "Player ping jitter showing latency instability over time.";
      case "players" -> "Online player count over time.";
      case "entities" -> "Tracked loaded-entity pressure across active chunks.";
      case "entities-spawns" ->
          "Entity spawn rate observed from creature spawn events.";
      case "entity-ai-active-count" ->
          "Approximate count of entities actively running AI logic.";
      case "chunks" -> "Loaded chunk count trend.";
      case "chunks-loaded" -> "Chunk-load event rate.";
      case "chunks-generated" -> "Chunk-generation event rate.";
      case "chunk-load-ms" -> "Chunk load latency in milliseconds.";
      case "chunk-gen-ms" -> "Chunk generation latency in milliseconds.";
      case "top-chunk-cost" ->
          "Highest observed per-chunk cost from React chunk sampling.";
      case "top-world-mspt" ->
          "Worst world-level MSPT observed across loaded worlds.";
      case "redstone" -> "Redstone update activity rate.";
      case "redstone-burst-rate" ->
          "Burst intensity of redstone transitions over short windows.";
      case "redstone-tick-time" ->
          "Tick time spent inside redstone processing paths.";
      case "hopper" -> "Hopper transfer/update activity rate.";
      case "hopper-tick-time" -> "Tick time spent in hopper processing paths.";
      case "fluid" -> "Fluid update activity rate.";
      case "fluid-tick-time" -> "Tick time spent in fluid simulation paths.";
      case "physics" -> "Physics update activity rate.";
      case "physics-tick-time" ->
          "Tick time spent in physics simulation paths.";
      case "event-time" -> "Total time spent processing server events.";
      case "event-handles-per-tick" ->
          "Event-handler invocation count per tick.";
      case "events-listeners" ->
          "Registered event-listener count on the server.";
      case "scheduler-backlog" ->
          "Scheduler backlog depth waiting for execution.";
      case "backlog-growth-rate" ->
          "Rate at which scheduler backlog is growing or shrinking.";
      case "react-jobs-queue" -> "Queued React jobs waiting to be processed.";
      case "react-job-budget" ->
          "Remaining React job budget available per processing cycle.";
      case "react-job-queue-time" -> "Time jobs spend waiting in React queues.";
      case "react-sync-tick-time" ->
          "Time React spends on synchronous work per tick.";
      case "react-async-tick-time" ->
          "Time React spends on asynchronous work per cycle.";
      case "processor-system-load" -> "Host system CPU load trend.";
      case "processor-process-load" -> "JVM process CPU load trend.";
      case "processor-outside" -> "Non-React external process load pressure.";
      case "iris-pregen-queue" -> "Iris remote pregen queue depth.";
      case "iris-chunk-stream-ms" ->
          "Iris chunk-stream latency from integration metrics.";
      case "iris-biome-cache-hit-rate" -> "Iris biome-cache hit ratio.";
      case "adapt-session-load" -> "Adapt runtime session load percentage.";
      case "adapt-ability-ops" ->
          "Adapt ability operations per minute (mode selected by config adaptAbilityOpsMetricMode).";
      case "adapt-ability-checks-per-tick" ->
          "Adapt all ability checks averaged per tick over the telemetry window.";
      case "adapt-world-policy-latency" ->
          "Adapt world-policy evaluation latency.";
      default -> samplerSummaryByKeyword(normalizedId);
    };
  }

  private static String samplerSummaryByKeyword(String normalizedId) {
    if (normalizedId.contains("tick")) {
      return "Server tick-path timing and spike behavior.";
    }
    if (normalizedId.contains("memory") || normalizedId.contains("gc")) {
      return "JVM memory pressure and garbage-collection behavior.";
    }
    if (normalizedId.contains("chunk")) {
      return "Chunk activity, latency, and load pressure.";
    }
    if (normalizedId.contains("entity")) {
      return "Entity population, spawn activity, or AI pressure.";
    }
    if (normalizedId.contains("player") || normalizedId.contains("ping")) {
      return "Player activity and network latency quality.";
    }
    if (normalizedId.contains("redstone")) {
      return "Redstone transition activity and processing cost.";
    }
    if (normalizedId.contains("hopper")) {
      return "Hopper transfer activity and processing cost.";
    }
    if (normalizedId.contains("fluid")) {
      return "Fluid update activity and simulation cost.";
    }
    if (normalizedId.contains("physics")) {
      return "Physics update activity and simulation cost.";
    }
    if (normalizedId.contains("event")) {
      return "Server event volume and processing cost.";
    }
    if (normalizedId.contains("react") || normalizedId.contains("job") || normalizedId.contains("queue") || normalizedId.contains("backlog")) {
      return "React scheduler queue depth and execution pressure.";
    }
    if (normalizedId.contains("processor") || normalizedId.contains("load")) {
      return "CPU and host load pressure around the server runtime.";
    }
    if (normalizedId.startsWith("iris-")) {
      return "Iris integration metric exposed through the remote sampler bridge.";
    }
    if (normalizedId.startsWith("adapt-")) {
      return "Adapt integration metric exposed through the remote sampler bridge.";
    }
    return "Live sampler trend for this monitor.";
  }

  private static String normalizeRendererId(ReactRenderer renderer) {
    return Objects.toString(renderer == null ? null : renderer.getId(), "")
        .toLowerCase(Locale.ROOT)
        .trim();
  }

  private static String rendererScopeLabel(String normalizedId) {
    if (normalizedId.startsWith("iris-")) {
      return "Iris Integration";
    }
    if (normalizedId.startsWith("adapt-")) {
      return "Adapt Integration";
    }
    if (normalizedId.startsWith("react-")) {
      return "React Integration";
    }
    return "React Core";
  }

  private static String rendererScopeTag(String normalizedId) {
    if (normalizedId.startsWith("iris-")) {
      return "IRIS";
    }
    if (normalizedId.startsWith("adapt-")) {
      return "ADAPT";
    }
    if (normalizedId.startsWith("react-")) {
      return "REACT";
    }
    return "CORE";
  }

  private static RendererGroup rendererGroup(ReactRenderer renderer, String normalizedId) {
    if (normalizedId.startsWith("adapt-")) {
      return new RendererGroup(0, "Adapt Integration", Material.BOOKSHELF);
    }
    if (normalizedId.startsWith("iris-")) {
      return new RendererGroup(1, "Iris Integration", Material.OAK_SAPLING);
    }
    if (normalizedId.startsWith("react-")) {
      return new RendererGroup(2, "React Integration", Material.REDSTONE_TORCH);
    }

    if (containsAny(normalizedId, "tick", "tps", "mspt", "incident", "spike")) {
      return new RendererGroup(10, "Tick & Stability", Material.CLOCK);
    }
    if (containsAny(normalizedId, "memory", "gc")) {
      return new RendererGroup(11, "Memory & GC", Material.EXPERIENCE_BOTTLE);
    }
    if (containsAny(normalizedId, "player", "ping")) {
      return new RendererGroup(12, "Players & Network", Material.PLAYER_HEAD);
    }
    if (containsAny(normalizedId, "entity", "spawn")) {
      return new RendererGroup(13, "Entities & Spawns", Material.ZOMBIE_HEAD);
    }
    if (containsAny(normalizedId, "chunk", "world")) {
      return new RendererGroup(14, "Chunks & World", Material.MAP);
    }
    if (containsAny(normalizedId, "redstone")) {
      return new RendererGroup(15, "Redstone", Material.REDSTONE);
    }
    if (containsAny(normalizedId, "hopper")) {
      return new RendererGroup(16, "Hoppers", Material.HOPPER);
    }
    if (containsAny(normalizedId, "fluid")) {
      return new RendererGroup(17, "Fluids", Material.WATER_BUCKET);
    }
    if (containsAny(normalizedId, "physics")) {
      return new RendererGroup(18, "Physics", Material.ANVIL);
    }
    if (containsAny(normalizedId, "event", "listener")) {
      return new RendererGroup(19, "Events", Material.NOTE_BLOCK);
    }
    if (containsAny(normalizedId, "react", "job", "queue", "backlog")) {
      return new RendererGroup(20, "React Scheduler", Material.COMPARATOR);
    }
    if (containsAny(normalizedId, "processor", "load", "cpu")) {
      return new RendererGroup(21, "CPU & Host", Material.BLAST_FURNACE);
    }
    if (renderer instanceof Sampler) {
      return new RendererGroup(22, "Sampler Trends", Material.COMPASS);
    }
    if (normalizedId.endsWith("-map") || normalizedId.endsWith("-heatmap") || normalizedId.endsWith("-overlay")) {
      return new RendererGroup(30, "World Overlays", Material.CARTOGRAPHY_TABLE);
    }
    if ("unknown".equals(normalizedId)) {
      return new RendererGroup(98, "Fallback", Material.BARRIER);
    }
    return new RendererGroup(99, "Misc", Material.FILLED_MAP);
  }

  private static boolean containsAny(String value, String... keys) {
    if (value == null || keys == null) {
      return false;
    }

    for (String key : keys) {
      if (key != null && !key.isBlank() && value.contains(key)) {
        return true;
      }
    }
    return false;
  }

  private static String normalizeSortKey(String input) {
    return C.stripColor(Objects.toString(input, "")).toLowerCase(Locale.ROOT).replace(" ", "");
  }

  private static void playPageTurn(Player player) {
    if (player == null || !player.isOnline()) {
      return;
    }

    try {
      player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1.1f, 1.255f);
      player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.7f, 1.455f);
      player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.3f, 1.855f);
    } catch (Throwable ignored) {
    }
  }

  private record PageLayout(
      int rows,
      int contentRows,
      int itemsPerPage,
      int pageCount,
      boolean pagination,
      int controlRow
  ) {
  }

  private record RendererGroup(
      int order,
      String label,
      Material icon
  ) {
  }
}
