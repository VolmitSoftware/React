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
import art.arcane.react.api.sampler.Sampler;
import art.arcane.react.content.sampler.SamplerUnknown;
import art.arcane.react.core.controller.IntegrationController;
import art.arcane.react.core.controller.SampleController;
import art.arcane.react.core.integration.IntegrationCapabilitySupport;
import art.arcane.react.localization.ReactLanguage;
import art.arcane.react.localization.catalog.GuiMessages;
import art.arcane.react.localization.catalog.MapMessages;
import art.arcane.react.util.common.scheduling.J;
import art.arcane.react.util.inventorygui.UIStaticDecorator;
import art.arcane.volmlib.util.data.MaterialBlock;
import art.arcane.volmlib.util.inventorygui.UIElement;
import art.arcane.volmlib.util.inventorygui.UIWindow;
import art.arcane.volmlib.util.inventorygui.WindowResolution;
import art.arcane.volmlib.util.localization.MessageArgument;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;

public class SamplerGUI {
  public static void pickSampler(Player p, Consumer<Sampler> onPicked, int page) {
    openGroup(p, onPicked, null, null, page);
  }

  public static void pickSampler(Player p, Consumer<Sampler> onPicked, List<String> without) {
    openHub(p, onPicked, without);
  }

  public static void pickSampler(Player p, Consumer<Sampler> onPicked) {
    openHub(p, onPicked, null);
  }

  public static void pickSampler(Player p, Consumer<Sampler> onPicked, List<String> without, int page) {
    openGroup(p, onPicked, without, null, page);
  }

  private static void openHub(Player p, Consumer<Sampler> onPicked, List<String> without) {
    if (p == null) {
      return;
    }

    if (!J.runEntity(p, () -> {
      UIWindow window = new UIWindow(React.instance, p);
      window.setTitle(ReactLanguage.plain(GuiMessages.SAMPLER_TITLE));
      window.setResolution(WindowResolution.W9_H6);
      window.setDecorator(new UIStaticDecorator(new UIElement("bg").setMaterial(new MaterialBlock(Material.BLACK_STAINED_GLASS_PANE))));
      List<Sampler> samplers = listedSamplers(without);
      Map<Integer, ReactGuiTaxonomy.EntryGroup> groups = new TreeMap<>();
      Map<Integer, Integer> counts = new TreeMap<>();
      for (Sampler i : samplers) {
        ReactGuiTaxonomy.EntryGroup group = ReactGuiTaxonomy.groupForId(i.getId());
        groups.putIfAbsent(group.order(), group);
        counts.merge(group.order(), 1, Integer::sum);
      }

      int rp = 0;
      int allRow = window.getRow(rp);
      int allPosition = window.getPosition(rp);
      rp++;
      window.setElement(allPosition, allRow, new UIElement("hub-all")
          .setMaterial(new MaterialBlock(Material.SPYGLASS))
          .setName(ReactLanguage.plain(GuiMessages.SAMPLER_HUB_ALL))
          .addLore(ReactLanguage.plain(GuiMessages.SAMPLER_HUB_ALL_SUMMARY))
          .addLore(ReactLanguage.plain(GuiMessages.SAMPLER_HUB_GROUP_COUNT, MessageArgument.untrusted("count", samplers.size())))
          .onLeftClick((e) -> {
            window.close();
            J.a(() -> openGroup(p, onPicked, without, null, 0));
          })
      );

      for (ReactGuiTaxonomy.EntryGroup group : groups.values()) {
        int count = counts.get(group.order());
        int row = window.getRow(rp);
        int position = window.getPosition(rp);
        rp++;
        window.setElement(position, row, new UIElement("hub-group-" + group.order())
            .setMaterial(new MaterialBlock(group.icon()))
            .setName(group.label())
            .addLore(ReactLanguage.plain(GuiMessages.SAMPLER_HUB_GROUP_COUNT, MessageArgument.untrusted("count", count)))
            .addLore(ReactLanguage.plain(GuiMessages.SAMPLER_HUB_OPEN_GROUP))
            .onLeftClick((e) -> {
              window.close();
              J.a(() -> openGroup(p, onPicked, without, group, 0));
            })
        );
      }

      window.open();
    })) {
      React.verbose(() -> "Failed to schedule sampler picker UI for " + p.getName());
    }
  }

  private static void openGroup(Player p, Consumer<Sampler> onPicked, List<String> without, ReactGuiTaxonomy.EntryGroup group, int page) {
    if (p == null) {
      return;
    }

    if (!J.runEntity(p, () -> {
      UIWindow window = new UIWindow(React.instance, p);
      window.setTitle(group == null
          ? ReactLanguage.plain(GuiMessages.SAMPLER_TITLE)
          : ReactLanguage.plain(GuiMessages.SAMPLER_GROUP_TITLE, MessageArgument.untrusted("group", group.label())));
      window.setResolution(WindowResolution.W9_H6);
      window.setDecorator(new UIStaticDecorator(new UIElement("bg").setMaterial(new MaterialBlock(Material.BLACK_STAINED_GLASS_PANE))));
      List<Sampler> samplers = listedSamplers(without);
      if (group != null) {
        samplers = samplers.stream()
            .filter(i -> ReactGuiTaxonomy.groupOrder(i.getId()) == group.order())
            .toList();
      }

      boolean hasMore = samplers.size() > (9 * 2) * (page + 1);
      int pge = page;
      if (samplers.size() <= pge * (9 * 2)) {
        pge = 0;
        React.verbose(() -> "Sampler picker reset an out-of-range page for " + p.getName());
      }

      samplers = samplers.subList(pge * (9 * 2), Math.min(samplers.size(), (pge + 1) * (9 * 2)));

      int rp = 0;
      for (Sampler i : samplers) {
        int h = window.getRow(rp);
        int w = window.getPosition(rp);
        rp++;
        window.setElement(w, h, new UIElement("sample-" + i.getId())
            .setMaterial(new MaterialBlock(ReactGuiTaxonomy.iconForId(i.getId())))
            .setName(MapMessages.localizedSamplerName(i.getId(), i.getName()))
            .addLore(ReactLanguage.plain(GuiMessages.SAMPLER_GROUP, MessageArgument.untrusted("group", ReactGuiTaxonomy.groupLabel(i.getId()))))
            .addLore(i.format(i.sample()))
            .onLeftClick((e) -> {
              onPicked.accept(i);
              window.close();
            })
        );
      }

      int pg = pge;

      if (pg > 0) {
        window.setElement(-4, 2, new UIElement("page-back")
            .setMaterial(new MaterialBlock(Material.ARROW))
            .setName(ReactLanguage.plain(GuiMessages.PREVIOUS_PAGE))
            .onLeftClick((e) -> {
              window.close();
              J.a(() -> openGroup(p, onPicked, without, group, pg - 1));
            })
        );
      }

      if (hasMore) {
        window.setElement(4, 2, new UIElement("page-forward")
            .setMaterial(new MaterialBlock(Material.ARROW))
            .setName(ReactLanguage.plain(GuiMessages.NEXT_PAGE))
            .onLeftClick((e) -> {
              window.close();
              J.a(() -> openGroup(p, onPicked, without, group, pg + 1));
            })
        );
      }

      window.setElement(0, 2, new UIElement("hub-back")
          .setMaterial(new MaterialBlock(Material.ARROW))
          .setName(ReactLanguage.plain(GuiMessages.SAMPLER_HUB_BACK))
          .onLeftClick((e) -> {
            window.close();
            J.a(() -> openHub(p, onPicked, without));
          })
      );

      window.open();
    })) {
      React.verbose(() -> "Failed to schedule sampler picker UI for " + p.getName());
    }
  }

  private static List<Sampler> listedSamplers(List<String> without) {
    return React.controller(SampleController.class)
        .getSamplers()
        .all()
        .stream()
        .filter(i -> i != null && !SamplerUnknown.ID.equalsIgnoreCase(i.getId()))
        .filter(SamplerGUI::isSamplerListed)
        .filter(i -> without == null || !without.contains(i.getId()))
        .sorted(Comparator
            .comparingInt((Sampler i) -> ReactGuiTaxonomy.groupOrder(i.getId()))
            .thenComparing(i -> ReactGuiTaxonomy.normalizeSortKey(i.getName()))
            .thenComparing(i -> ReactGuiTaxonomy.normalizeSortKey(i.getId())))
        .toList();
  }

  private static boolean isSamplerListed(Sampler sampler) {
    String pluginId = IntegrationCapabilitySupport.integrationPluginFor(ReactGuiTaxonomy.normalizeId(sampler.getId()));
    if (pluginId == null) {
      return true;
    }

    return IntegrationCapabilitySupport.isCapabilityPresent(
        React.controller(IntegrationController.class),
        pluginId
    );
  }
}
