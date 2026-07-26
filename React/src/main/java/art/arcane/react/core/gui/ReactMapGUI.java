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
import art.arcane.react.api.rendering.MegamapGrid;
import art.arcane.react.api.rendering.ReactRenderer;
import art.arcane.react.api.rendering.RendererUnknown;
import art.arcane.react.api.sampler.Sampler;
import art.arcane.react.core.controller.MapController;
import art.arcane.react.localization.ReactLanguage;
import art.arcane.react.localization.catalog.GuiMessages;
import art.arcane.react.localization.catalog.MapMessages;
import art.arcane.react.localization.catalog.RendererMessages;
import art.arcane.react.localization.catalog.TaxonomyMessages;
import art.arcane.react.util.common.scheduling.J;
import art.arcane.react.util.format.C;
import art.arcane.react.util.inventorygui.UIStaticDecorator;
import art.arcane.volmlib.util.data.MaterialBlock;
import art.arcane.volmlib.util.inventorygui.Element;
import art.arcane.volmlib.util.inventorygui.UIElement;
import art.arcane.volmlib.util.inventorygui.UIWindow;
import art.arcane.volmlib.util.inventorygui.WindowResolution;
import art.arcane.volmlib.util.localization.MessageArgument;
import art.arcane.volmlib.util.localization.TextKey;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class ReactMapGUI {
  private static final int PAGE_JUMP = 5;
  private static final int ALL_GROUPS = -1;
  private static final Set<String> INTEGRATION_METRIC_IDS = Set.of(
      "iris-metrics", "adapt-metrics", "wormholes-metrics",
      "holoui-metrics", "hiddenore-metrics", "biletools-metrics", "react-metrics"
  );

  private ReactMapGUI() {
  }

  public static void open(Player player) {
    openHub(player);
  }

  public static void open(Player player, int page) {
    openGroup(player, ALL_GROUPS, page);
  }

  private static void openHub(Player player) {
    if (player == null) {
      return;
    }

    if (!Bukkit.isPrimaryThread()) {
      J.runEntity(player, () -> openHub(player));
      return;
    }

    MapController controller = React.controller(MapController.class);
    if (controller == null || controller.getRenderers() == null) {
      ReactLanguage.send(player, GuiMessages.MAP_CONTROLLER_UNAVAILABLE);
      return;
    }

    List<ReactRenderer> renderers = getRenderers(controller);
    if (renderers.isEmpty()) {
      ReactLanguage.send(player, GuiMessages.MAP_NONE_AVAILABLE);
      return;
    }

    playPageTurn(player);
    Map<Integer, RendererGroup> groups = new LinkedHashMap<>();
    Map<Integer, Integer> groupCounts = new LinkedHashMap<>();
    for (ReactRenderer renderer : renderers) {
      RendererGroup group = rendererGroup(renderer, normalizeRendererId(renderer));
      groups.putIfAbsent(group.order(), group);
      groupCounts.merge(group.order(), 1, Integer::sum);
    }

    List<Element> tiles = new ArrayList<>();
    tiles.add(new UIElement("map-hub-all")
        .setMaterial(new MaterialBlock(Material.FILLED_MAP))
        .setName(C.WHITE + ReactLanguage.plain(GuiMessages.MAP_HUB_ALL))
        .addLore(C.GRAY + ReactLanguage.plain(GuiMessages.MAP_HUB_ALL_SUMMARY))
        .addLore(C.DARK_GRAY + ReactLanguage.plain(
            GuiMessages.MAP_HUB_GROUP_COUNT,
            MessageArgument.untrusted("count", renderers.size())
        ))
        .onLeftClick((e) -> J.runEntity(player, () -> openGroup(player, ALL_GROUPS, 0))));

    Map<String, MapController.MegamapStatus> megamapStatus = controller.megamapStatusByRenderer();
    if (!megamapStatus.isEmpty()) {
      tiles.add(megamapSummaryTile(megamapStatus));
    }

    for (Map.Entry<Integer, RendererGroup> entry : groups.entrySet()) {
      int groupOrder = entry.getKey();
      RendererGroup group = entry.getValue();
      int count = groupCounts.getOrDefault(groupOrder, 0);
      tiles.add(new UIElement("map-hub-group-" + groupOrder)
          .setMaterial(new MaterialBlock(group.icon()))
          .setName(C.WHITE + group.label())
          .addLore(C.DARK_GRAY + ReactLanguage.plain(
              GuiMessages.MAP_HUB_GROUP_COUNT,
              MessageArgument.untrusted("count", count)
          ))
          .addLore(C.GREEN + ReactLanguage.plain(GuiMessages.MAP_HUB_OPEN_GROUP))
          .onLeftClick((e) -> J.runEntity(player, () -> openGroup(player, groupOrder, 0))));
    }

    int rows = Math.max(1, Math.min(6, (int) Math.ceil(tiles.size() / 9D)));
    UIWindow window = new UIWindow(React.instance, player);
    window.setResolution(WindowResolution.W9_H6);
    window.setViewportHeight(rows);
    window.setTitle(ReactLanguage.plain(GuiMessages.MAP_TITLE));
    window.setDecorator(new UIStaticDecorator(new UIElement("bg")
        .setMaterial(new MaterialBlock(Material.BLACK_STAINED_GLASS_PANE))));

    for (int row = 0; row < rows; row++) {
      int rowStart = row * 9;
      if (rowStart >= tiles.size()) {
        break;
      }

      int rowCount = Math.min(9, tiles.size() - rowStart);
      for (int i = 0; i < rowCount; i++) {
        window.setElement(centeredPosition(i, rowCount), row, tiles.get(rowStart + i));
      }
    }

    window.open();
  }

  private static void openGroup(Player player, int groupOrder, int page) {
    if (player == null) {
      return;
    }

    if (!Bukkit.isPrimaryThread()) {
      int safePage = page;
      J.runEntity(player, () -> openGroup(player, groupOrder, safePage));
      return;
    }

    MapController controller = React.controller(MapController.class);
    if (controller == null || controller.getRenderers() == null) {
      ReactLanguage.send(player, GuiMessages.MAP_CONTROLLER_UNAVAILABLE);
      return;
    }

    List<ReactRenderer> allRenderers = getRenderers(controller);
    if (allRenderers.isEmpty()) {
      ReactLanguage.send(player, GuiMessages.MAP_NONE_AVAILABLE);
      return;
    }

    List<ReactRenderer> renderers = filterByGroup(allRenderers, groupOrder);
    if (renderers.isEmpty()) {
      openHub(player);
      return;
    }

    playPageTurn(player);
    Map<String, MapController.MegamapStatus> megamapStatus = controller.megamapStatusByRenderer();
    PageLayout layout = groupPageLayout(renderers.size());
    int currentPage = clampPage(page, layout.pageCount());
    int start = currentPage * layout.itemsPerPage();
    int end = Math.min(renderers.size(), start + layout.itemsPerPage());

    UIWindow window = new UIWindow(React.instance, player);
    window.setResolution(WindowResolution.W9_H6);
    window.setViewportHeight(layout.rows());
    window.setTitle(groupTitle(renderers, groupOrder));
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
        String rendererName = rendererDisplayName(renderer);
        String scopeTag = rendererScopeTag(normalizedId);
        int w = centeredPosition(i, rowCount);
        int h = row;
        UIElement element = new UIElement("map-renderer-" + renderer.getId())
            .setMaterial(new MaterialBlock(materialFor(renderer)))
            .setName(C.WHITE + rendererName + C.DARK_GRAY + " [" + scopeTag + "]");
        for (String loreLine : loreFor(renderer, megamapStatus.get(normalizedId))) {
          element.addLore(loreLine);
        }

        window.setElement(w, h, element
            .onLeftClick((e) -> {
              controller.openRenderer(player, renderer);
              ReactLanguage.send(
                  player,
                  GuiMessages.MAP_SELECTED,
                  MessageArgument.untrusted("renderer", rendererName),
                  MessageArgument.untrusted("scope", scopeTag)
              );
              player.closeInventory();
            })
            .onShiftLeftClick((e) -> {
              controller.giveMapToInventory(player, renderer);
              ReactLanguage.send(
                  player,
                  GuiMessages.MAP_ADDED,
                  MessageArgument.untrusted("renderer", rendererName),
                  MessageArgument.untrusted("scope", scopeTag)
              );
            })
            .onShiftRightClick((e) -> {
              controller.giveMapToInventory(player, renderer);
              ReactLanguage.send(
                  player,
                  GuiMessages.MAP_ADDED,
                  MessageArgument.untrusted("renderer", rendererName),
                  MessageArgument.untrusted("scope", scopeTag)
              );
            }));
      }
    }

    int controlRow = layout.controlRow();
    window.setElement(-4, controlRow, new UIElement("map-hub-back")
        .setMaterial(new MaterialBlock(Material.ARROW))
        .setName(C.WHITE + ReactLanguage.plain(GuiMessages.MAP_HUB_BACK))
        .onLeftClick((e) -> J.runEntity(player, () -> openHub(player))));

    if (layout.pagination()) {
      int jumpBack = Math.max(0, currentPage - PAGE_JUMP);
      int jumpForward = Math.min(layout.pageCount() - 1, currentPage + PAGE_JUMP);

      if (currentPage > 0) {
        window.setElement(2, controlRow, new UIElement("map-prev")
            .setMaterial(new MaterialBlock(Material.ARROW))
            .setName(C.WHITE + ReactLanguage.plain(GuiMessages.PREVIOUS))
            .onLeftClick((e) -> J.runEntity(player, () -> openGroup(player, groupOrder, currentPage - 1)))
            .onRightClick((e) -> J.runEntity(player, () -> openGroup(player, groupOrder, jumpBack))));
      }

      if (currentPage < layout.pageCount() - 1) {
        window.setElement(4, controlRow, new UIElement("map-next")
            .setMaterial(new MaterialBlock(Material.ARROW))
            .setName(C.WHITE + ReactLanguage.plain(GuiMessages.NEXT))
            .onLeftClick((e) -> J.runEntity(player, () -> openGroup(player, groupOrder, currentPage + 1)))
            .onRightClick((e) -> J.runEntity(player, () -> openGroup(player, groupOrder, jumpForward))));
      }

      int from = start + 1;
      int to = end;
      window.setElement(3, controlRow, new UIElement("map-page-info")
          .setMaterial(new MaterialBlock(Material.PAPER))
          .setName(C.AQUA + ReactLanguage.plain(
              GuiMessages.PAGE,
              MessageArgument.untrusted("current", currentPage + 1),
              MessageArgument.untrusted("total", layout.pageCount()),
              MessageArgument.untrusted("from", from),
              MessageArgument.untrusted("to", to)
          )));
    }

    window.open();
  }

  private static String groupTitle(List<ReactRenderer> renderers, int groupOrder) {
    if (groupOrder == ALL_GROUPS) {
      return ReactLanguage.plain(GuiMessages.MAP_TITLE);
    }

    ReactRenderer first = renderers.get(0);
    RendererGroup group = rendererGroup(first, normalizeRendererId(first));
    return ReactLanguage.plain(
        GuiMessages.MAP_GROUP_TITLE,
        MessageArgument.untrusted("group", group.label())
    );
  }

  private static List<ReactRenderer> filterByGroup(List<ReactRenderer> renderers, int groupOrder) {
    if (groupOrder == ALL_GROUPS) {
      return renderers;
    }

    List<ReactRenderer> filtered = new ArrayList<>();
    for (ReactRenderer renderer : renderers) {
      if (rendererGroup(renderer, normalizeRendererId(renderer)).order() == groupOrder) {
        filtered.add(renderer);
      }
    }
    return filtered;
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
      boolean aMetrics = aNormalized.endsWith("-metrics");
      boolean bMetrics = bNormalized.endsWith("-metrics");
      if (aMetrics != bMetrics) {
        return aMetrics ? -1 : 1;
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

  private static PageLayout groupPageLayout(int totalEntries) {
    int safeEntries = Math.max(0, totalEntries);
    int contentRows = Math.max(1, Math.min(5, (int) Math.ceil(Math.max(1, safeEntries) / 9D)));
    int rows = contentRows + 1;
    int itemsPerPage = contentRows * 9;
    int pageCount = Math.max(1, (int) Math.ceil(safeEntries / (double) itemsPerPage));
    return new PageLayout(rows, contentRows, itemsPerPage, pageCount, pageCount > 1, rows - 1);
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

  private static List<String> loreFor(ReactRenderer renderer, MapController.MegamapStatus megamapStatus) {
    List<String> lore = new ArrayList<>();
    String id = renderer == null ? "unknown" : Objects.toString(renderer.getId(), "unknown");
    String normalizedId = normalizeRendererId(renderer);
    RendererGroup group = rendererGroup(renderer, normalizedId);
    lore.add(C.DARK_GRAY + ReactLanguage.plain(GuiMessages.MAP_SCOPE, MessageArgument.untrusted("scope", rendererScopeLabel(normalizedId))));
    lore.add(C.DARK_GRAY + ReactLanguage.plain(GuiMessages.MAP_GROUP, MessageArgument.untrusted("group", group.label())));
    lore.add(C.DARK_GRAY + ReactLanguage.plain(GuiMessages.MAP_TYPE, MessageArgument.untrusted("type", rendererType(renderer, normalizedId))));
    lore.add(C.GRAY + rendererSummary(renderer, normalizedId));
    String detail = rendererDetail(renderer, normalizedId);
    if (detail != null && !detail.isBlank()) {
      lore.add(C.DARK_GRAY + detail);
    }
    lore.add(C.DARK_GRAY + ReactLanguage.plain(GuiMessages.MAP_ID, MessageArgument.untrusted("id", id)));
    lore.add(C.AQUA + megamapCapabilityLine(renderer));
    if (megamapStatus != null) {
      lore.add(C.YELLOW + megamapStatusLine(megamapStatus));
    }
    lore.add(C.GREEN + ReactLanguage.plain(GuiMessages.MAP_SELECT));
    lore.add(C.GREEN + ReactLanguage.plain(GuiMessages.MAP_ADD_KEEP_OPEN));
    return lore;
  }

  private static Element megamapSummaryTile(Map<String, MapController.MegamapStatus> megamapStatus) {
    int walls = 0;
    int issues = 0;
    for (MapController.MegamapStatus status : megamapStatus.values()) {
      if (status.gridWidth() > 0) {
        walls++;
      }
      if (status.defect() != null) {
        issues++;
      }
    }

    Element tile = new UIElement("map-hub-megamap")
        .setMaterial(new MaterialBlock(Material.ITEM_FRAME))
        .setName(C.WHITE + ReactLanguage.raw(MapMessages.MEGAMAP_WALLS));
    tile.addLore(C.GRAY + ReactLanguage.raw(MapMessages.MEGAMAP_WALLS_SUMMARY));
    tile.addLore(C.DARK_GRAY + ReactLanguage.raw(
        MapMessages.MEGAMAP_WALLS_DETECTED,
        MessageArgument.untrusted("count", Integer.toString(walls))
    ) + (issues > 0 ? C.RED + "  " + ReactLanguage.raw(
        MapMessages.MEGAMAP_WALLS_ISSUES,
        MessageArgument.untrusted("count", Integer.toString(issues))
    ) : ""));

    int listed = 0;
    for (Map.Entry<String, MapController.MegamapStatus> entry : megamapStatus.entrySet()) {
      if (listed >= 8) {
        break;
      }

      tile.addLore(C.DARK_GRAY + displayName(entry.getKey()) + C.GRAY + "  " + megamapStatusLine(entry.getValue()));
      listed++;
    }

    return tile;
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

  private static String megamapStatusLine(MapController.MegamapStatus status) {
    if (status.defect() != null) {
      return ReactLanguage.raw(
          MapMessages.MEGAMAP_ISSUE,
          MessageArgument.untrusted("reason", megamapDefectLabel(status.defect()))
      );
    }

    return ReactLanguage.raw(
        MapMessages.MEGAMAP_ACTIVE,
        MessageArgument.untrusted("size", status.gridWidth() + "x" + status.gridHeight()),
        MessageArgument.untrusted("frames", Integer.toString(status.frames()))
    );
  }

  private static String megamapDefectLabel(MegamapGrid.DefectReason reason) {
    return ReactLanguage.raw(MapMessages.defectLabel(reason));
  }

  private static String rendererDisplayName(ReactRenderer renderer) {
    String normalizedId = normalizeRendererId(renderer);
    String fallback = displayName(renderer == null ? null : renderer.getId());
    if (renderer instanceof Sampler sampler) {
      return MapMessages.localizedSamplerName(normalizedId, sampler.getName());
    }
    return RendererMessages.localizedTitle(normalizedId, fallback);
  }

  private static String rendererType(ReactRenderer renderer, String normalizedId) {
    if (renderer instanceof Sampler) {
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

  private static String rendererSummary(ReactRenderer renderer, String normalizedId) {
    if (renderer instanceof Sampler) {
      return MapMessages.samplerSummary(normalizedId);
    }
    return MapMessages.rendererSummary(normalizedId, rendererDisplayName(renderer));
  }

  private static String rendererDetail(ReactRenderer renderer, String normalizedId) {
    return MapMessages.rendererDetail(normalizedId, renderer instanceof Sampler);
  }

  private static String normalizeRendererId(ReactRenderer renderer) {
    return Objects.toString(renderer == null ? null : renderer.getId(), "")
        .toLowerCase(Locale.ROOT)
        .trim();
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
    if (normalizedId.startsWith("holoui-")) {
      return ReactLanguage.plain(TaxonomyMessages.GROUP_HOLOUI);
    }
    if (normalizedId.startsWith("hiddenore-")) {
      return ReactLanguage.plain(TaxonomyMessages.GROUP_HIDDENORE);
    }
    if (normalizedId.startsWith("biletools-")) {
      return ReactLanguage.plain(TaxonomyMessages.GROUP_BILETOOLS);
    }
    if (normalizedId.startsWith("react-")) {
      return ReactLanguage.plain(TaxonomyMessages.GROUP_REACT);
    }
    if (normalizedId.startsWith("plugin-")) {
      return ReactLanguage.plain(TaxonomyMessages.GROUP_PLUGINS);
    }
    return ReactLanguage.plain(TaxonomyMessages.GROUP_CORE);
  }

  private static String rendererScopeTag(String normalizedId) {
    if (normalizedId.startsWith("iris-")) {
      return ReactLanguage.plain(GuiMessages.MAP_SCOPE_TAG_IRIS);
    }
    if (normalizedId.startsWith("adapt-")) {
      return ReactLanguage.plain(GuiMessages.MAP_SCOPE_TAG_ADAPT);
    }
    if (normalizedId.startsWith("wormholes-")) {
      return ReactLanguage.plain(GuiMessages.MAP_SCOPE_TAG_WORMHOLES);
    }
    if (normalizedId.startsWith("holoui-")) {
      return ReactLanguage.plain(GuiMessages.MAP_SCOPE_TAG_HOLOUI);
    }
    if (normalizedId.startsWith("hiddenore-")) {
      return ReactLanguage.plain(GuiMessages.MAP_SCOPE_TAG_HIDDENORE);
    }
    if (normalizedId.startsWith("biletools-")) {
      return ReactLanguage.plain(GuiMessages.MAP_SCOPE_TAG_BILETOOLS);
    }
    if (normalizedId.startsWith("react-")) {
      return ReactLanguage.plain(GuiMessages.MAP_SCOPE_TAG_REACT);
    }
    if (normalizedId.startsWith("plugin-")) {
      return ReactLanguage.plain(GuiMessages.MAP_SCOPE_TAG_PLUGIN);
    }
    return ReactLanguage.plain(GuiMessages.MAP_SCOPE_TAG_CORE);
  }

  private static RendererGroup rendererGroup(ReactRenderer renderer, String normalizedId) {
    if (normalizedId.startsWith("adapt-")) {
      return rendererGroup(0, TaxonomyMessages.GROUP_ADAPT, Material.BOOKSHELF);
    }
    if (normalizedId.startsWith("iris-")) {
      return rendererGroup(1, TaxonomyMessages.GROUP_IRIS, Material.OAK_SAPLING);
    }
    if (normalizedId.startsWith("react-")) {
      return rendererGroup(2, TaxonomyMessages.GROUP_REACT, Material.REDSTONE_TORCH);
    }
    if (normalizedId.startsWith("wormholes-")) {
      return rendererGroup(3, TaxonomyMessages.GROUP_WORMHOLES, Material.ENDER_PEARL);
    }
    if (normalizedId.startsWith("holoui-")) {
      return rendererGroup(4, TaxonomyMessages.GROUP_HOLOUI, Material.PAINTING);
    }
    if (normalizedId.startsWith("hiddenore-")) {
      return rendererGroup(5, TaxonomyMessages.GROUP_HIDDENORE, Material.DIAMOND_ORE);
    }
    if (normalizedId.startsWith("biletools-")) {
      return rendererGroup(6, TaxonomyMessages.GROUP_BILETOOLS, Material.LIME_DYE);
    }

    if (containsAny(normalizedId, "tick", "tps", "mspt", "incident", "spike")) {
      return rendererGroup(10, TaxonomyMessages.GROUP_TICK, Material.CLOCK);
    }
    if (containsAny(normalizedId, "memory", "gc")) {
      return rendererGroup(11, TaxonomyMessages.GROUP_MEMORY, Material.EXPERIENCE_BOTTLE);
    }
    if (containsAny(normalizedId, "player", "ping")) {
      return rendererGroup(12, TaxonomyMessages.GROUP_PLAYERS, Material.PLAYER_HEAD);
    }
    if (containsAny(normalizedId, "entity", "spawn")) {
      return rendererGroup(13, TaxonomyMessages.GROUP_ENTITIES, Material.ZOMBIE_HEAD);
    }
    if (containsAny(normalizedId, "chunk", "world")) {
      return rendererGroup(14, TaxonomyMessages.GROUP_CHUNKS, Material.MAP);
    }
    if (containsAny(normalizedId, "redstone")) {
      return rendererGroup(15, TaxonomyMessages.GROUP_REDSTONE, Material.REDSTONE);
    }
    if (containsAny(normalizedId, "hopper")) {
      return rendererGroup(16, TaxonomyMessages.GROUP_HOPPERS, Material.HOPPER);
    }
    if (containsAny(normalizedId, "fluid")) {
      return rendererGroup(17, TaxonomyMessages.GROUP_FLUIDS, Material.WATER_BUCKET);
    }
    if (containsAny(normalizedId, "physics")) {
      return rendererGroup(18, TaxonomyMessages.GROUP_PHYSICS, Material.ANVIL);
    }
    if (containsAny(normalizedId, "event", "listener")) {
      return rendererGroup(19, TaxonomyMessages.GROUP_EVENTS, Material.NOTE_BLOCK);
    }
    if (containsAny(normalizedId, "react", "job", "queue", "backlog")) {
      return rendererGroup(20, TaxonomyMessages.GROUP_SCHEDULER, Material.COMPARATOR);
    }
    if (containsAny(normalizedId, "processor", "load", "cpu")) {
      return rendererGroup(21, TaxonomyMessages.GROUP_CPU, Material.BLAST_FURNACE);
    }
    if (renderer instanceof Sampler) {
      return rendererGroup(22, TaxonomyMessages.GROUP_SAMPLER_TRENDS, Material.COMPASS);
    }
    if (normalizedId.endsWith("-map") || normalizedId.endsWith("-heatmap") || normalizedId.endsWith("-overlay")) {
      return rendererGroup(30, TaxonomyMessages.GROUP_WORLD_OVERLAYS, Material.CARTOGRAPHY_TABLE);
    }
    if ("unknown".equals(normalizedId)) {
      return rendererGroup(98, TaxonomyMessages.GROUP_FALLBACK, Material.BARRIER);
    }
    return rendererGroup(99, TaxonomyMessages.GROUP_MISC, Material.FILLED_MAP);
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

  private static RendererGroup rendererGroup(int order, TextKey label, Material icon) {
    return new RendererGroup(order, ReactLanguage.plain(label), icon);
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
