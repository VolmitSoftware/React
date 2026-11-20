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
import art.arcane.react.api.rendering.MapHubModel;
import art.arcane.react.api.rendering.MegamapStatus;
import art.arcane.react.api.rendering.ReactRenderer;
import art.arcane.react.api.rendering.SamplerRenderer;
import art.arcane.react.api.sampler.Sampler;
import art.arcane.react.core.controller.MapController;
import art.arcane.react.localization.ReactLanguage;
import art.arcane.react.localization.catalog.GuiMessages;
import art.arcane.react.platform.MapHubAction;
import art.arcane.react.platform.MapHubScreen;
import art.arcane.react.platform.MapHubScreenHost;
import art.arcane.react.util.common.scheduling.J;
import art.arcane.volmlib.util.localization.LanguageAudience;
import art.arcane.volmlib.util.localization.MessageArgument;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ReactMapGUI {
  private static final MapHubModel MODEL = new MapHubModel(renderer -> SamplerRenderer.samplerOf(renderer) instanceof Sampler sampler ? sampler.getName() : null);
  private static final MapHubScreenHost HOST = BukkitMapHubScreenHost.INSTANCE;

  private ReactMapGUI() {
  }

  public static void open(Player player) {
    openHub(player);
  }

  public static void open(Player player, int page) {
    openGroup(player, MapHubModel.ALL_GROUPS, page);
  }

  private static void openHub(Player player) {
    if (player != null) {
      LanguageAudience.run(player.getUniqueId(), () -> openHubForPlayer(player));
    }
  }

  private static void openHubForPlayer(Player player) {
    if (player == null) {
      return;
    }

    if (!Bukkit.isPrimaryThread()) {
      J.runEntity(player, () -> openHub(player));
      return;
    }

    MapController controller = availableController(player);
    List<ReactRenderer> renderers = controller == null ? List.of() : rendererList(player, controller);
    if (renderers.isEmpty()) {
      return;
    }

    HOST.playPageTurn(player.getUniqueId());
    MapHubScreen screen = MODEL.hub(renderers, controller.megamapStatusByRenderer());
    HOST.open(player.getUniqueId(), screen, (viewerId, tile, click, action) -> handle(player, controller, byId(renderers), action));
  }

  private static void openGroup(Player player, int groupOrder, int page) {
    if (player != null) {
      LanguageAudience.run(player.getUniqueId(), () -> openGroupForPlayer(player, groupOrder, page));
    }
  }

  private static void openGroupForPlayer(Player player, int groupOrder, int page) {
    if (player == null) {
      return;
    }

    if (!Bukkit.isPrimaryThread()) {
      J.runEntity(player, () -> openGroup(player, groupOrder, page));
      return;
    }

    MapController controller = availableController(player);
    List<ReactRenderer> renderers = controller == null ? List.of() : rendererList(player, controller);
    if (renderers.isEmpty()) {
      return;
    }

    Map<String, MegamapStatus> megamapStatus = controller.megamapStatusByRenderer();
    MapHubScreen screen = MODEL.group(renderers, groupOrder, page, megamapStatus);
    if (screen == null) {
      openHub(player);
      return;
    }

    HOST.playPageTurn(player.getUniqueId());
    HOST.open(player.getUniqueId(), screen, (viewerId, tile, click, action) -> handle(player, controller, byId(renderers), action));
  }

  private static MapController availableController(Player player) {
    MapController controller = React.controller(MapController.class);
    if (controller == null || controller.getRenderers() == null) {
      ReactLanguage.send(player, GuiMessages.MAP_CONTROLLER_UNAVAILABLE);
      return null;
    }
    return controller;
  }

  private static List<ReactRenderer> rendererList(Player player, MapController controller) {
    List<ReactRenderer> renderers = MODEL.sortedRenderers(controller.getRenderers().values());
    if (renderers.isEmpty()) {
      ReactLanguage.send(player, GuiMessages.MAP_NONE_AVAILABLE);
    }
    return renderers;
  }

  private static Map<String, ReactRenderer> byId(List<ReactRenderer> renderers) {
    Map<String, ReactRenderer> byId = new LinkedHashMap<>();
    for (ReactRenderer renderer : renderers) {
      byId.put(renderer.getId(), renderer);
    }
    return byId;
  }

  private static void handle(Player player, MapController controller, Map<String, ReactRenderer> renderers, MapHubAction action) {
    switch (action) {
      case MapHubAction.OpenHub ignored -> J.runEntity(player, () -> openHub(player));
      case MapHubAction.OpenGroup group -> J.runEntity(player, () -> openGroup(player, group.groupOrder(), group.page()));
      case MapHubAction.SelectMap select -> {
        ReactRenderer renderer = renderers.get(select.rendererId());
        if (renderer != null) {
          controller.openRenderer(player, renderer);
        }
        ReactLanguage.send(
            player,
            GuiMessages.MAP_SELECTED,
            MessageArgument.untrusted("renderer", select.rendererName()),
            MessageArgument.untrusted("scope", select.scopeTag())
        );
        HOST.close(player.getUniqueId());
      }
      case MapHubAction.AddMap add -> {
        ReactRenderer renderer = renderers.get(add.rendererId());
        if (renderer != null) {
          controller.giveMapToInventory(player, renderer);
        }
        ReactLanguage.send(
            player,
            GuiMessages.MAP_ADDED,
            MessageArgument.untrusted("renderer", add.rendererName()),
            MessageArgument.untrusted("scope", add.scopeTag())
        );
      }
    }
  }
}
