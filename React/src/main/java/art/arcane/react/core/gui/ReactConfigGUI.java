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
import art.arcane.react.api.sampler.Sampler;
import art.arcane.react.core.controller.IntegrationController;
import art.arcane.react.core.integration.IntegrationCapabilitySupport;
import org.bukkit.entity.Player;

import java.util.function.Consumer;

/**
 * React's settings menu and monitor editor on Bukkit. The screens and edits live in {@link ReactSettingsMenu} and
 * {@link MonitorConfigMenu}.
 */
public final class ReactConfigGUI {
  private static final ReactSettingsMenu MENU = new ReactSettingsMenu(new BukkitSettingsSource(), BukkitSettingsScreenHost.INSTANCE);
  private static final MonitorConfigMenu MONITOR = new MonitorConfigMenu(BukkitSettingsScreenHost.INSTANCE, ReactConfigGUI::samplerListed);

  private ReactConfigGUI() {
  }

  public static ReactSettingsMenu menu() {
    return MENU;
  }

  public static MonitorConfigMenu monitorMenu() {
    return MONITOR;
  }

  public static void openMonitor(Player player, MonitorConfiguration configuration, Consumer<MonitorConfiguration> saver) {
    if (player != null) {
      MONITOR.open(player.getUniqueId(), configuration, saver);
    }
  }

  public static boolean canConfigure(Player player) {
    return player != null && BukkitSettingsSource.canConfigure(player);
  }

  public static void open(Player player) {
    if (player != null) {
      MENU.open(player.getUniqueId());
    }
  }

  public static boolean applyAndSave(Player actor, String valuePath, Object value) {
    return MENU.applyAndSave(actor == null ? null : actor.getUniqueId(), valuePath, value);
  }

  public static int applyPresetHeadless(String preset) {
    return MENU.applyPresetHeadless(preset);
  }

  private static boolean samplerListed(Sampler sampler) {
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
