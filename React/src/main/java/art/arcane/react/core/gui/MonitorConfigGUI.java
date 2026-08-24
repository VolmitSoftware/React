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
import art.arcane.react.api.monitor.configuration.MonitorConfiguration;
import art.arcane.react.api.monitor.configuration.MonitorGroup;
import art.arcane.react.api.sampler.Sampler;
import art.arcane.react.localization.ReactLanguage;
import art.arcane.react.localization.catalog.GuiMessages;
import art.arcane.react.util.common.scheduling.J;
import art.arcane.react.util.inventorygui.CustomUIElement;
import art.arcane.react.util.inventorygui.UIStaticDecorator;
import art.arcane.volmlib.util.data.MaterialBlock;
import art.arcane.volmlib.util.inventorygui.UIElement;
import art.arcane.volmlib.util.inventorygui.UIWindow;
import art.arcane.volmlib.util.inventorygui.WindowResolution;
import art.arcane.volmlib.util.localization.MessageArgument;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public class MonitorConfigGUI {
  public static void editMonitorConfigurationGroup(Player p, MonitorConfiguration configuration, MonitorGroup group, Consumer<MonitorConfiguration> saver) {
    if (p == null) {
      return;
    }

    if (!J.runEntity(p, () -> {
      group.setSamplers(new ArrayList<>(group.getSamplers()));
      UIWindow window = new UIWindow(React.instance, p);
      window.setTitle(ReactLanguage.plain(GuiMessages.MONITOR_GROUP_TITLE, MessageArgument.untrusted("group", group.getName())));
      window.setResolution(WindowResolution.W9_H6);
      window.setDecorator(new UIStaticDecorator(new UIElement("bg").setMaterial(new MaterialBlock(Material.BLACK_STAINED_GLASS_PANE))));
      AtomicBoolean refresh = new AtomicBoolean(false);
      int rp = 0;
      String head = group.getHeadOrSomething();

      for (String ii : group.getSamplers()) {
        int h = window.getRow(rp);
        int w = window.getPosition(rp);
        rp++;
        Sampler i = React.sampler(ii);
        if (i == null) {
          continue;
        }
        window.setElement(w, h, new UIElement("sample-" + i.getId())
            .setMaterial(new MaterialBlock(ReactGuiTaxonomy.iconForId(i.getId())))
            .setName(i.getName())
            .addLore(ReactLanguage.plain(GuiMessages.SAMPLER_GROUP, MessageArgument.untrusted("group", ReactGuiTaxonomy.groupLabel(i.getId()))))
            .addLore(i.format(i.sample()))
            .setEnchanted(head.equals(ii))
            .addLore(ReactLanguage.plain(GuiMessages.SAMPLER_SET_HEADER))
            .addLore(ReactLanguage.plain(GuiMessages.SAMPLER_REMOVE))
            .onLeftClick((e) -> {
              group.setHeadSampler(i.getId());
              saver.accept(configuration);
              refresh.set(true);
              J.a(() -> editMonitorConfigurationGroup(p, configuration, group, saver));
            })
            .onShiftRightClick((e) -> {
              group.getSamplers().remove(i.getId());

              if (head.equals(ii)) {
                group.setHeadSampler(null);
              }

              refresh.set(true);
              saver.accept(configuration);
              J.a(() -> editMonitorConfigurationGroup(p, configuration, group, saver));
            })
        );
      }

      window.setElement(0, 2, new UIElement("addnew")
          .setName(ReactLanguage.plain(GuiMessages.SAMPLER_ADD))
          .addLore(ReactLanguage.plain(GuiMessages.SAMPLER_ADD_LORE))
          .setMaterial(new MaterialBlock(Material.EMERALD))
          .onLeftClick((e) -> {
            refresh.set(true);
            J.a(() -> {
              SamplerGUI.pickSampler(p, (s) -> {
                group.getSamplers().add(s.getId());
                saver.accept(configuration);
                refresh.set(true);
                J.a(() -> editMonitorConfigurationGroup(p, configuration, group, saver));
              }, new ArrayList<>(group.getSamplers()));
            });
          })
      );

      window.setElement(-1, 2, new UIElement("renamegroup")
          .setName(ReactLanguage.plain(GuiMessages.GROUP_RENAME))
          .addLore(ReactLanguage.plain(GuiMessages.GROUP_RENAME_LORE))
          .setMaterial(new MaterialBlock(Material.WRITABLE_BOOK))
          .onLeftClick((e) -> {
            refresh.set(true);
            J.s(() -> {
              window.close();

              J.a(() -> {
                ReactLanguage.send(p, GuiMessages.GROUP_RENAME_PROMPT);
                String n = TextInputGui.captureText(p);

                if (n != null) {
                  group.setName(n);
                  saver.accept(configuration);
                  J.a(() -> editMonitorConfigurationGroup(p, configuration, group, saver));
                }
              });
            });
          })
      );

      window.setElement(-2, 2, new CustomUIElement("recolorgroup", Guis.generateColorIcon(ReactLanguage.plain(GuiMessages.GROUP_COLOR), new Color(group.getColorValue())))
          .setName(ReactLanguage.plain(GuiMessages.GROUP_COLOR))
          .addLore(ReactLanguage.plain(GuiMessages.GROUP_COLOR_LORE))
          .onLeftClick((e) -> {
            refresh.set(true);
            J.a(() -> {
              Color s = ColorPickerGUI.pickColor(p, new Color(group.getColorValue()));

              if (s != null) {
                group.setColor("#" + Integer.toHexString(s.getRGB()).substring(2));
                saver.accept(configuration);
                refresh.set(true);
                J.a(() -> editMonitorConfigurationGroup(p, configuration, group, saver));
              }
            });
          })
      );

      window.setElement(1, 2, new UIElement("deletegroup")
          .setName(ReactLanguage.plain(GuiMessages.GROUP_DELETE))
          .addLore(ReactLanguage.plain(GuiMessages.GROUP_DELETE_LORE))
          .setMaterial(new MaterialBlock(Material.BARRIER))
          .onShiftLeftClick((e) -> {
            refresh.set(true);
            configuration.getGroups().remove(group);
            saver.accept(configuration);
            J.a(() -> editMonitorConfiguration(p, configuration, saver));
          })
      );

      window.open();
      window.onClosed((w) -> {
        saver.accept(configuration);
        if (!refresh.get()) {
          J.a(() -> editMonitorConfiguration(p, configuration, saver));
        }
      });
    })) {
      React.verbose(() -> "Failed to schedule monitor group config UI for " + p.getName());
    }
  }

  public static void editMonitorConfiguration(Player p, MonitorConfiguration configuration, Consumer<MonitorConfiguration> saver) {
    if (p == null) {
      return;
    }

    if (!J.runEntity(p, () -> {
      UIWindow window = new UIWindow(React.instance, p);
      window.setTitle(ReactLanguage.plain(GuiMessages.MONITOR_TITLE));
      window.setResolution(WindowResolution.W9_H6);
      window.setDecorator(new UIStaticDecorator(new UIElement("bg").setMaterial(new MaterialBlock(Material.BLACK_STAINED_GLASS_PANE))));

      int rp = 0;
      for (MonitorGroup i : configuration.getGroups()) {
        int h = window.getRow(rp);
        int w = window.getPosition(rp);
        rp++;
        UIElement ee = new CustomUIElement("group-" + i.getName(), Guis.generateColorIcon(i.getName(), new Color(i.getColorValue())))
            .setName(i.getName());

        ee.addLore("[" + i.getHeadOrSomething() + "]");

        for (String j : i.getSamplers()) {
          ee.addLore(j);
        }

        window.setElement(w, h, ee
            .onLeftClick((e) -> J.a(() -> editMonitorConfigurationGroup(p, configuration, i, saver)))
        );
      }

      window.setElement(0, 2, new UIElement("creategroup")
          .setName(ReactLanguage.plain(GuiMessages.GROUP_CREATE))
          .addLore(ReactLanguage.plain(GuiMessages.GROUP_CREATE_LORE))
          .setMaterial(new MaterialBlock(Material.EMERALD))
          .onLeftClick((e) -> {
            J.a(() -> {
              J.s(window::close);
              ReactLanguage.send(p, GuiMessages.GROUP_CREATE_PROMPT);
              String name = TextInputGui.captureText(p);
              Color c = ColorPickerGUI.presetColors.get(new Random().nextInt(ColorPickerGUI.presetColors.size()));
              if (name != null) {
                MonitorGroup g = MonitorGroup.builder()
                    .name(name)
                    .color("#" + Integer.toHexString(c.getRGB()).substring(2))
                    .build();
                configuration.getGroups().add(g);
                saver.accept(configuration);
                J.a(() -> editMonitorConfigurationGroup(p, configuration, g, saver));
              } else {
                ReactLanguage.send(p, GuiMessages.GROUP_INVALID_NAME);
                J.a(() -> editMonitorConfiguration(p, configuration, saver));
              }
            });
          })
      );


      window.open();
      window.onClosed((w) -> {
        saver.accept(configuration);
      });
    })) {
      React.verbose(() -> "Failed to schedule monitor config UI for " + p.getName());
    }
  }
}
