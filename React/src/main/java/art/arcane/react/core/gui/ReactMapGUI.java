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
import art.arcane.react.localization.ReactLanguage;
import art.arcane.react.localization.catalog.GuiMessages;
import art.arcane.react.localization.catalog.MapMessages;
import art.arcane.react.localization.catalog.RendererMessages;
import art.arcane.react.localization.catalog.TaxonomyMessages;
import art.arcane.react.util.common.scheduling.J;
import art.arcane.react.util.format.C;
import art.arcane.react.util.inventorygui.UIStaticDecorator;
import art.arcane.volmlib.util.data.MaterialBlock;
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
  private static final Set<String> INTEGRATION_METRIC_IDS = Set.of(
      "iris-metrics", "adapt-metrics", "wormholes-metrics",
      "holoui-metrics", "hiddenore-metrics", "biletools-metrics", "react-metrics"
  );

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
      ReactLanguage.send(player, GuiMessages.MAP_CONTROLLER_UNAVAILABLE);
      return;
    }

    List<ReactRenderer> renderers = getRenderers(controller);
    if (renderers.isEmpty()) {
      ReactLanguage.send(player, GuiMessages.MAP_NONE_AVAILABLE);
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
    window.setTitle(ReactLanguage.plain(GuiMessages.MAP_TITLE));
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
        for (String loreLine : loreFor(renderer)) {
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

    if (layout.pagination()) {
      int controlRow = layout.controlRow();
      int jumpBack = Math.max(0, currentPage - PAGE_JUMP);
      int jumpForward = Math.min(layout.pageCount() - 1, currentPage + PAGE_JUMP);

      if (currentPage > 0) {
        window.setElement(2, controlRow, new UIElement("map-prev")
            .setMaterial(new MaterialBlock(Material.ARROW))
            .setName(C.WHITE + ReactLanguage.plain(GuiMessages.PREVIOUS))
            .onLeftClick((e) -> open(player, currentPage - 1))
            .onRightClick((e) -> open(player, jumpBack)));
      }

      if (currentPage < layout.pageCount() - 1) {
        window.setElement(4, controlRow, new UIElement("map-next")
            .setMaterial(new MaterialBlock(Material.ARROW))
            .setName(C.WHITE + ReactLanguage.plain(GuiMessages.NEXT))
            .onLeftClick((e) -> open(player, currentPage + 1))
            .onRightClick((e) -> open(player, jumpForward)));
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

  private static List<String> loreFor(ReactRenderer renderer) {
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
    lore.add(C.GREEN + ReactLanguage.plain(GuiMessages.MAP_SELECT));
    lore.add(C.GREEN + ReactLanguage.plain(GuiMessages.MAP_ADD_KEEP_OPEN));
    return lore;
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
