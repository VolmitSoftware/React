package art.arcane.react.api.rendering;

import art.arcane.react.core.telemetry.timing.WorldTickSeries;
import art.arcane.react.localization.ReactLanguage;
import art.arcane.react.platform.cost.OwnerCostModel;
import art.arcane.react.platform.cost.OwnerNoun;

import art.arcane.react.localization.catalog.GuiMessages;
import art.arcane.react.localization.catalog.MapMessages;
import art.arcane.react.localization.catalog.RendererMessages;
import art.arcane.react.localization.catalog.TaxonomyMessages;
import art.arcane.react.platform.MapHubAction;
import art.arcane.react.platform.MapHubClick;
import art.arcane.react.platform.MapHubIcon;
import art.arcane.react.platform.MapHubScreen;
import art.arcane.react.platform.MapHubTile;
import art.arcane.volmlib.util.localization.MessageArgument;
import art.arcane.volmlib.util.localization.TextKey;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

public final class MapHubModel {
  public static final int PAGE_JUMP = 5;
  public static final int ALL_GROUPS = -1;
  public static final String UNKNOWN_RENDERER_ID = "unknown";

  private static final String WHITE = "§f";
  private static final String GRAY = "§7";
  private static final String DARK_GRAY = "§8";
  private static final String GREEN = "§a";
  private static final String AQUA = "§b";
  private static final String RED = "§c";
  private static final String YELLOW = "§e";
  private static final Pattern STRIP_COLOR = Pattern.compile("(?i)§[0-9A-FK-OR]");
  private static final Set<String> INTEGRATION_METRIC_IDS = Set.of(
      "iris-metrics", "adapt-metrics", "wormholes-metrics",
      "gloss-metrics", "hiddenore-metrics", "biletools-metrics", "react-metrics"
  );

  private final SamplerLookup samplers;

  public MapHubModel(SamplerLookup samplers) {
    this.samplers = samplers == null ? renderer -> null : samplers;
  }

  public List<ReactRenderer> sortedRenderers(Collection<ReactRenderer> registered) {
    Map<String, ReactRenderer> unique = new LinkedHashMap<>();
    if (registered != null) {
      for (ReactRenderer renderer : registered) {
        if (renderer == null || renderer.getId() == null || renderer.getId().isBlank()) {
          continue;
        }

        if (normalizeRendererId(renderer).equals(UNKNOWN_RENDERER_ID)) {
          continue;
        }

        unique.putIfAbsent(renderer.getId(), renderer);
      }
    }

    List<ReactRenderer> renderers = new ArrayList<>(unique.values());
    renderers.sort((a, b) -> {
      String aNormalized = normalizeRendererId(a);
      String bNormalized = normalizeRendererId(b);
      int groupCompare = Integer.compare(groupOf(a).order(), groupOf(b).order());
      if (groupCompare != 0) {
        return groupCompare;
      }
      boolean aMetrics = aNormalized.endsWith("-metrics");
      boolean bMetrics = bNormalized.endsWith("-metrics");
      if (aMetrics != bMetrics) {
        return aMetrics ? -1 : 1;
      }
      return normalizeSortKey(a.getId()).compareTo(normalizeSortKey(b.getId()));
    });
    return renderers;
  }

  public MapHubScreen hub(List<ReactRenderer> renderers, Map<String, MegamapStatus> megamapStatus) {
    Map<Integer, Group> groups = new LinkedHashMap<>();
    Map<Integer, Integer> groupCounts = new LinkedHashMap<>();
    for (ReactRenderer renderer : renderers) {
      Group group = groupOf(renderer);
      groups.putIfAbsent(group.order(), group);
      groupCounts.merge(group.order(), 1, Integer::sum);
    }

    List<TileContent> contents = new ArrayList<>();
    contents.add(new TileContent(
        "map-hub-all",
        MapHubIcon.ALL_MAPS,
        WHITE + ReactLanguage.plain(GuiMessages.MAP_HUB_ALL),
        List.of(
            GRAY + ReactLanguage.plain(GuiMessages.MAP_HUB_ALL_SUMMARY),
            DARK_GRAY + ReactLanguage.plain(GuiMessages.MAP_HUB_GROUP_COUNT, MessageArgument.untrusted("count", renderers.size()))
        ),
        Map.of(MapHubClick.LEFT, new MapHubAction.OpenGroup(ALL_GROUPS, 0))
    ));

    if (megamapStatus != null && !megamapStatus.isEmpty()) {
      contents.add(megamapSummaryTile(megamapStatus));
    }

    for (Map.Entry<Integer, Group> entry : groups.entrySet()) {
      int groupOrder = entry.getKey();
      Group group = entry.getValue();
      int count = groupCounts.getOrDefault(groupOrder, 0);
      contents.add(new TileContent(
          "map-hub-group-" + groupOrder,
          group.icon(),
          WHITE + group.label(),
          List.of(
              DARK_GRAY + ReactLanguage.plain(GuiMessages.MAP_HUB_GROUP_COUNT, MessageArgument.untrusted("count", count)),
              GREEN + ReactLanguage.plain(GuiMessages.MAP_HUB_OPEN_GROUP)
          ),
          Map.of(MapHubClick.LEFT, new MapHubAction.OpenGroup(groupOrder, 0))
      ));
    }

    int rows = Math.max(1, Math.min(6, (int) Math.ceil(contents.size() / 9D)));
    List<MapHubTile> tiles = new ArrayList<>();
    for (int row = 0; row < rows; row++) {
      int rowStart = row * 9;
      if (rowStart >= contents.size()) {
        break;
      }

      int rowCount = Math.min(9, contents.size() - rowStart);
      for (int i = 0; i < rowCount; i++) {
        tiles.add(contents.get(rowStart + i).at(centeredPosition(i, rowCount), row));
      }
    }

    return new MapHubScreen(ReactLanguage.plain(GuiMessages.MAP_TITLE), rows, MapHubIcon.BACKGROUND, tiles);
  }

  /**
   * Null when the group holds no renderers; the caller falls back to the hub.
   */
  public MapHubScreen group(
      List<ReactRenderer> allRenderers,
      int groupOrder,
      int page,
      Map<String, MegamapStatus> megamapStatus
  ) {
    List<ReactRenderer> renderers = filterByGroup(allRenderers, groupOrder);
    if (renderers.isEmpty()) {
      return null;
    }

    Map<String, MegamapStatus> statuses = megamapStatus == null ? Map.of() : megamapStatus;
    PageLayout layout = pageLayout(renderers.size());
    int currentPage = clampPage(page, layout.pageCount());
    int start = currentPage * layout.itemsPerPage();
    int end = Math.min(renderers.size(), start + layout.itemsPerPage());
    List<MapHubTile> tiles = new ArrayList<>();

    for (int row = 0; row < layout.contentRows(); row++) {
      int rowStart = start + (row * 9);
      if (rowStart >= end) {
        break;
      }

      int rowCount = Math.min(9, end - rowStart);
      for (int i = 0; i < rowCount; i++) {
        ReactRenderer renderer = renderers.get(rowStart + i);
        String normalizedId = normalizeRendererId(renderer);
        String rendererName = rendererDisplayName(renderer);
        String scopeTag = rendererScopeTag(normalizedId);
        MapHubAction.AddMap add = new MapHubAction.AddMap(renderer.getId(), rendererName, scopeTag);
        Map<MapHubClick, MapHubAction> actions = new EnumMap<>(MapHubClick.class);
        actions.put(MapHubClick.LEFT, new MapHubAction.SelectMap(renderer.getId(), rendererName, scopeTag));
        actions.put(MapHubClick.SHIFT_LEFT, add);
        actions.put(MapHubClick.SHIFT_RIGHT, add);
        tiles.add(new MapHubTile(
            "map-renderer-" + renderer.getId(),
            centeredPosition(i, rowCount),
            row,
            groupOf(renderer).icon(),
            WHITE + rendererName + DARK_GRAY + " [" + scopeTag + "]",
            loreFor(renderer, statuses.get(normalizedId)),
            actions
        ));
      }
    }

    int controlRow = layout.controlRow();
    tiles.add(new MapHubTile(
        "map-hub-back",
        -4,
        controlRow,
        MapHubIcon.BACK,
        WHITE + ReactLanguage.plain(GuiMessages.MAP_HUB_BACK),
        List.of(),
        Map.of(MapHubClick.LEFT, new MapHubAction.OpenHub())
    ));

    if (layout.pagination()) {
      int jumpBack = Math.max(0, currentPage - PAGE_JUMP);
      int jumpForward = Math.min(layout.pageCount() - 1, currentPage + PAGE_JUMP);

      if (currentPage > 0) {
        tiles.add(new MapHubTile(
            "map-prev",
            2,
            controlRow,
            MapHubIcon.PREVIOUS,
            WHITE + ReactLanguage.plain(GuiMessages.PREVIOUS),
            List.of(),
            Map.of(
                MapHubClick.LEFT, new MapHubAction.OpenGroup(groupOrder, currentPage - 1),
                MapHubClick.RIGHT, new MapHubAction.OpenGroup(groupOrder, jumpBack)
            )
        ));
      }

      if (currentPage < layout.pageCount() - 1) {
        tiles.add(new MapHubTile(
            "map-next",
            4,
            controlRow,
            MapHubIcon.NEXT,
            WHITE + ReactLanguage.plain(GuiMessages.NEXT),
            List.of(),
            Map.of(
                MapHubClick.LEFT, new MapHubAction.OpenGroup(groupOrder, currentPage + 1),
                MapHubClick.RIGHT, new MapHubAction.OpenGroup(groupOrder, jumpForward)
            )
        ));
      }

      tiles.add(new MapHubTile(
          "map-page-info",
          3,
          controlRow,
          MapHubIcon.PAGE_INFO,
          AQUA + ReactLanguage.plain(
              GuiMessages.PAGE,
              MessageArgument.untrusted("current", currentPage + 1),
              MessageArgument.untrusted("total", layout.pageCount()),
              MessageArgument.untrusted("from", start + 1),
              MessageArgument.untrusted("to", end)
          ),
          List.of(),
          Map.of()
      ));
    }

    return new MapHubScreen(groupTitle(renderers, groupOrder), layout.rows(), MapHubIcon.BACKGROUND, tiles);
  }

  public List<ReactRenderer> filterByGroup(List<ReactRenderer> renderers, int groupOrder) {
    if (groupOrder == ALL_GROUPS) {
      return renderers;
    }

    List<ReactRenderer> filtered = new ArrayList<>();
    for (ReactRenderer renderer : renderers) {
      if (groupOf(renderer).order() == groupOrder) {
        filtered.add(renderer);
      }
    }
    return filtered;
  }

  public Group groupOf(ReactRenderer renderer) {
    String normalizedId = normalizeRendererId(renderer);
    if (normalizedId.startsWith("adapt-")) {
      return group(0, TaxonomyMessages.GROUP_ADAPT, MapHubIcon.GROUP_ADAPT);
    }
    if (normalizedId.startsWith("iris-")) {
      return group(1, TaxonomyMessages.GROUP_IRIS, MapHubIcon.GROUP_IRIS);
    }
    if (normalizedId.startsWith("react-")) {
      return group(2, TaxonomyMessages.GROUP_REACT, MapHubIcon.GROUP_REACT);
    }
    if (normalizedId.startsWith("wormholes-")) {
      return group(3, TaxonomyMessages.GROUP_WORMHOLES, MapHubIcon.GROUP_WORMHOLES);
    }
    if (normalizedId.startsWith("gloss-")) {
      return group(4, TaxonomyMessages.GROUP_GLOSS, MapHubIcon.GROUP_GLOSS);
    }
    if (normalizedId.startsWith("hiddenore-")) {
      return group(5, TaxonomyMessages.GROUP_HIDDENORE, MapHubIcon.GROUP_HIDDENORE);
    }
    if (normalizedId.startsWith("biletools-")) {
      return group(6, TaxonomyMessages.GROUP_BILETOOLS, MapHubIcon.GROUP_BILETOOLS);
    }
    if (normalizedId.startsWith("animus-")) {
      return group(7, TaxonomyMessages.GROUP_ANIMUS, MapHubIcon.GROUP_ANIMUS);
    }

    if (WorldTickSeries.isWorldTickId(normalizedId)) {
      return group(9, TaxonomyMessages.GROUP_DIMENSIONS, MapHubIcon.GROUP_DIMENSIONS);
    }
    if (containsAny(normalizedId, "tick", "tps", "mspt", "incident", "spike")) {
      return group(10, TaxonomyMessages.GROUP_TICK, MapHubIcon.GROUP_TICK);
    }
    if (containsAny(normalizedId, "memory", "gc")) {
      return group(11, TaxonomyMessages.GROUP_MEMORY, MapHubIcon.GROUP_MEMORY);
    }
    if (containsAny(normalizedId, "player", "ping")) {
      return group(12, TaxonomyMessages.GROUP_PLAYERS, MapHubIcon.GROUP_PLAYERS);
    }
    if (containsAny(normalizedId, "entity", "spawn")) {
      return group(13, TaxonomyMessages.GROUP_ENTITIES, MapHubIcon.GROUP_ENTITIES);
    }
    if (containsAny(normalizedId, "chunk", "world")) {
      return group(14, TaxonomyMessages.GROUP_CHUNKS, MapHubIcon.GROUP_CHUNKS);
    }
    if (containsAny(normalizedId, "redstone")) {
      return group(15, TaxonomyMessages.GROUP_REDSTONE, MapHubIcon.GROUP_REDSTONE);
    }
    if (containsAny(normalizedId, "hopper")) {
      return group(16, TaxonomyMessages.GROUP_HOPPERS, MapHubIcon.GROUP_HOPPERS);
    }
    if (containsAny(normalizedId, "fluid")) {
      return group(17, TaxonomyMessages.GROUP_FLUIDS, MapHubIcon.GROUP_FLUIDS);
    }
    if (containsAny(normalizedId, "physics")) {
      return group(18, TaxonomyMessages.GROUP_PHYSICS, MapHubIcon.GROUP_PHYSICS);
    }
    if (containsAny(normalizedId, "event", "listener")) {
      return group(19, TaxonomyMessages.GROUP_EVENTS, MapHubIcon.GROUP_EVENTS);
    }
    if (containsAny(normalizedId, "react", "job", "queue", "backlog")) {
      return group(20, TaxonomyMessages.GROUP_SCHEDULER, MapHubIcon.GROUP_SCHEDULER);
    }
    if (containsAny(normalizedId, "processor", "load", "cpu")) {
      return group(21, TaxonomyMessages.GROUP_CPU, MapHubIcon.GROUP_CPU);
    }
    if (isSampler(renderer)) {
      return group(22, TaxonomyMessages.GROUP_SAMPLER_TRENDS, MapHubIcon.GROUP_SAMPLER_TRENDS);
    }
    if (normalizedId.endsWith("-map") || normalizedId.endsWith("-heatmap") || normalizedId.endsWith("-overlay")) {
      return group(30, TaxonomyMessages.GROUP_WORLD_OVERLAYS, MapHubIcon.GROUP_WORLD_OVERLAYS);
    }
    if (UNKNOWN_RENDERER_ID.equals(normalizedId)) {
      return group(98, TaxonomyMessages.GROUP_FALLBACK, MapHubIcon.GROUP_FALLBACK);
    }
    return group(99, TaxonomyMessages.GROUP_MISC, MapHubIcon.GROUP_MISC);
  }

  public String rendererDisplayName(ReactRenderer renderer) {
    String normalizedId = normalizeRendererId(renderer);
    String samplerName = renderer == null ? null : samplers.samplerName(renderer);
    if (samplerName != null) {
      return MapMessages.localizedSamplerName(normalizedId, samplerName);
    }
    return RendererMessages.localizedTitle(normalizedId, displayName(renderer == null ? null : renderer.getId()));
  }

  public String rendererScopeTag(String normalizedId) {
    if (normalizedId.startsWith("iris-")) {
      return ReactLanguage.plain(GuiMessages.MAP_SCOPE_TAG_IRIS);
    }
    if (normalizedId.startsWith("adapt-")) {
      return ReactLanguage.plain(GuiMessages.MAP_SCOPE_TAG_ADAPT);
    }
    if (normalizedId.startsWith("wormholes-")) {
      return ReactLanguage.plain(GuiMessages.MAP_SCOPE_TAG_WORMHOLES);
    }
    if (normalizedId.startsWith("gloss-")) {
      return ReactLanguage.plain(GuiMessages.MAP_SCOPE_TAG_GLOSS);
    }
    if (normalizedId.startsWith("hiddenore-")) {
      return ReactLanguage.plain(GuiMessages.MAP_SCOPE_TAG_HIDDENORE);
    }
    if (normalizedId.startsWith("biletools-")) {
      return ReactLanguage.plain(GuiMessages.MAP_SCOPE_TAG_BILETOOLS);
    }
    if (normalizedId.startsWith("animus-")) {
      return ReactLanguage.plain(GuiMessages.MAP_SCOPE_TAG_ANIMUS);
    }
    if (normalizedId.startsWith("react-")) {
      return ReactLanguage.plain(GuiMessages.MAP_SCOPE_TAG_REACT);
    }
    if (OwnerCostModel.isOwnerScopedId(normalizedId)) {
      return ReactLanguage.plain(OwnerNoun.pick(GuiMessages.MAP_SCOPE_TAG_PLUGIN, GuiMessages.MAP_SCOPE_TAG_MOD));
    }
    return ReactLanguage.plain(GuiMessages.MAP_SCOPE_TAG_CORE);
  }

  public static PageLayout pageLayout(int totalEntries) {
    int safeEntries = Math.max(0, totalEntries);
    int contentRows = Math.max(1, Math.min(5, (int) Math.ceil(Math.max(1, safeEntries) / 9D)));
    int rows = contentRows + 1;
    int itemsPerPage = contentRows * 9;
    int pageCount = Math.max(1, (int) Math.ceil(safeEntries / (double) itemsPerPage));
    return new PageLayout(rows, contentRows, itemsPerPage, pageCount, pageCount > 1, rows - 1);
  }

  public static int clampPage(int page, int pageCount) {
    if (pageCount <= 0) {
      return 0;
    }
    return Math.max(0, Math.min(page, pageCount - 1));
  }

  public static int centeredPosition(int index, int rowCount) {
    int safeCount = Math.max(1, Math.min(9, rowCount));
    int safeIndex = Math.max(0, Math.min(index, safeCount - 1));
    int start = -(safeCount / 2);
    if ((safeCount & 1) == 1) {
      start = -((safeCount - 1) / 2);
    }
    return start + safeIndex;
  }

  public static String normalizeRendererId(ReactRenderer renderer) {
    return Objects.toString(renderer == null ? null : renderer.getId(), "")
        .toLowerCase(Locale.ROOT)
        .trim();
  }

  private String groupTitle(List<ReactRenderer> renderers, int groupOrder) {
    if (groupOrder == ALL_GROUPS) {
      return ReactLanguage.plain(GuiMessages.MAP_TITLE);
    }

    return ReactLanguage.plain(GuiMessages.MAP_GROUP_TITLE, MessageArgument.untrusted("group", groupOf(renderers.get(0)).label()));
  }

  private List<String> loreFor(ReactRenderer renderer, MegamapStatus megamapStatus) {
    List<String> lore = new ArrayList<>();
    String id = renderer == null ? "unknown" : Objects.toString(renderer.getId(), "unknown");
    String normalizedId = normalizeRendererId(renderer);
    Group group = groupOf(renderer);
    boolean sampler = isSampler(renderer);
    lore.add(DARK_GRAY + ReactLanguage.plain(GuiMessages.MAP_SCOPE, MessageArgument.untrusted("scope", rendererScopeLabel(normalizedId))));
    lore.add(DARK_GRAY + ReactLanguage.plain(GuiMessages.MAP_GROUP, MessageArgument.untrusted("group", group.label())));
    lore.add(DARK_GRAY + ReactLanguage.plain(GuiMessages.MAP_TYPE, MessageArgument.untrusted("type", rendererType(sampler, normalizedId))));
    lore.add(GRAY + (sampler
        ? MapMessages.samplerSummary(normalizedId)
        : MapMessages.rendererSummary(normalizedId, rendererDisplayName(renderer))));
    String detail = MapMessages.rendererDetail(normalizedId, sampler);
    if (detail != null && !detail.isBlank()) {
      lore.add(DARK_GRAY + detail);
    }
    lore.add(DARK_GRAY + ReactLanguage.plain(GuiMessages.MAP_ID, MessageArgument.untrusted("id", id)));
    lore.add(AQUA + megamapCapabilityLine(renderer));
    if (megamapStatus != null) {
      lore.add(YELLOW + megamapStatusLine(megamapStatus));
    }
    lore.add(GREEN + ReactLanguage.plain(GuiMessages.MAP_SELECT));
    lore.add(GREEN + ReactLanguage.plain(GuiMessages.MAP_ADD_KEEP_OPEN));
    return lore;
  }

  private TileContent megamapSummaryTile(Map<String, MegamapStatus> megamapStatus) {
    int walls = 0;
    int issues = 0;
    for (MegamapStatus status : megamapStatus.values()) {
      if (status.gridWidth() > 0) {
        walls++;
      }
      if (status.defect() != null) {
        issues++;
      }
    }

    List<String> lore = new ArrayList<>();
    lore.add(GRAY + ReactLanguage.raw(MapMessages.MEGAMAP_WALLS_SUMMARY));
    lore.add(DARK_GRAY + ReactLanguage.raw(
        MapMessages.MEGAMAP_WALLS_DETECTED,
        MessageArgument.untrusted("count", Integer.toString(walls))
    ) + (issues > 0 ? RED + "  " + ReactLanguage.raw(
        MapMessages.MEGAMAP_WALLS_ISSUES,
        MessageArgument.untrusted("count", Integer.toString(issues))
    ) : ""));

    int listed = 0;
    for (Map.Entry<String, MegamapStatus> entry : megamapStatus.entrySet()) {
      if (listed >= 8) {
        break;
      }

      lore.add(DARK_GRAY + displayName(entry.getKey()) + GRAY + "  " + megamapStatusLine(entry.getValue()));
      listed++;
    }

    return new TileContent("map-hub-megamap", MapHubIcon.MEGAMAP_SUMMARY, WHITE + ReactLanguage.raw(MapMessages.MEGAMAP_WALLS), lore, Map.of());
  }

  private static String megamapCapabilityLine(ReactRenderer renderer) {
    MegamapGrid.MegamapCapability capability = renderer == null
        ? MegamapGrid.MegamapCapability.magnify()
        : renderer.megamapCapability();
    if (!capability.adaptive()) {
      return ReactLanguage.raw(MapMessages.MEGAMAP_MAGNIFY);
    }

    return ReactLanguage.raw(
        MapMessages.MEGAMAP_ADAPTIVE,
        MessageArgument.untrusted("size", capability.maxGridWidth() + "x" + capability.maxGridHeight())
    );
  }

  private static String megamapStatusLine(MegamapStatus status) {
    if (status.defect() != null) {
      return ReactLanguage.raw(
          MapMessages.MEGAMAP_ISSUE,
          MessageArgument.untrusted("reason", ReactLanguage.raw(MegamapGrid.defectLabel(status.defect())))
      );
    }

    return ReactLanguage.raw(
        MapMessages.MEGAMAP_ACTIVE,
        MessageArgument.untrusted("size", status.gridWidth() + "x" + status.gridHeight()),
        MessageArgument.untrusted("frames", Integer.toString(status.frames()))
    );
  }

  private static String displayName(String key) {
    if (key == null || key.isBlank()) {
      return ReactLanguage.plain(GuiMessages.MAP_UNKNOWN);
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

  private static String rendererType(boolean sampler, String normalizedId) {
    if (sampler) {
      return ReactLanguage.plain(GuiMessages.MAP_TYPE_SAMPLER);
    }
    if (INTEGRATION_METRIC_IDS.contains(normalizedId)) {
      return ReactLanguage.plain(GuiMessages.MAP_TYPE_INTEGRATION);
    }
    if (normalizedId.contains("-list-map")) {
      return ReactLanguage.plain(GuiMessages.MAP_TYPE_RANKED_LIST);
    }
    if (normalizedId.contains("-pie-map")) {
      return ReactLanguage.plain(GuiMessages.MAP_TYPE_PIE);
    }
    if (normalizedId.endsWith("-map") || normalizedId.endsWith("-heatmap") || normalizedId.endsWith("-overlay")) {
      return ReactLanguage.plain(GuiMessages.MAP_TYPE_OVERLAY);
    }
    return ReactLanguage.plain(GuiMessages.MAP_TYPE_RENDERER);
  }

  private static String rendererScopeLabel(String normalizedId) {
    if (normalizedId.startsWith("iris-")) {
      return ReactLanguage.plain(TaxonomyMessages.GROUP_IRIS);
    }
    if (normalizedId.startsWith("adapt-")) {
      return ReactLanguage.plain(TaxonomyMessages.GROUP_ADAPT);
    }
    if (normalizedId.startsWith("wormholes-")) {
      return ReactLanguage.plain(TaxonomyMessages.GROUP_WORMHOLES);
    }
    if (normalizedId.startsWith("gloss-")) {
      return ReactLanguage.plain(TaxonomyMessages.GROUP_GLOSS);
    }
    if (normalizedId.startsWith("hiddenore-")) {
      return ReactLanguage.plain(TaxonomyMessages.GROUP_HIDDENORE);
    }
    if (normalizedId.startsWith("biletools-")) {
      return ReactLanguage.plain(TaxonomyMessages.GROUP_BILETOOLS);
    }
    if (normalizedId.startsWith("animus-")) {
      return ReactLanguage.plain(TaxonomyMessages.GROUP_ANIMUS);
    }
    if (normalizedId.startsWith("react-")) {
      return ReactLanguage.plain(TaxonomyMessages.GROUP_REACT);
    }
    if (OwnerCostModel.isOwnerScopedId(normalizedId)) {
      return ReactLanguage.plain(OwnerNoun.pick(TaxonomyMessages.GROUP_PLUGINS, TaxonomyMessages.GROUP_MODS));
    }
    return ReactLanguage.plain(TaxonomyMessages.GROUP_CORE);
  }

  private boolean isSampler(ReactRenderer renderer) {
    return renderer != null && samplers.samplerName(renderer) != null;
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

  private static Group group(int order, TextKey label, MapHubIcon icon) {
    return new Group(order, ReactLanguage.plain(label), icon);
  }

  private static String normalizeSortKey(String input) {
    return STRIP_COLOR.matcher(Objects.toString(input, "")).replaceAll("").toLowerCase(Locale.ROOT).replace(" ", "");
  }

  /**
   * Returns the sampler's display name when the renderer is a sampler, otherwise null.
   */
  @FunctionalInterface
  public interface SamplerLookup {
    String samplerName(ReactRenderer renderer);
  }

  public record Group(int order, String label, MapHubIcon icon) {
  }

  public record PageLayout(int rows, int contentRows, int itemsPerPage, int pageCount, boolean pagination, int controlRow) {
  }

  private record TileContent(String key, MapHubIcon icon, String name, List<String> lore, Map<MapHubClick, MapHubAction> actions) {
    private MapHubTile at(int position, int row) {
      return new MapHubTile(key, position, row, icon, name, lore, actions);
    }
  }
}
