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

package art.arcane.react.core.controller;

import art.arcane.react.React;
import art.arcane.react.core.integration.IntegrationCapabilitySupport;
import art.arcane.react.core.plugincost.SamplerPluginCost;
import art.arcane.react.util.plugin.IController;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;

import java.util.Locale;

public class PluginCostController implements IController, Listener {
  @Override
  public String getName() {
    return "Plugin Cost";
  }

  @Override
  public String getId() {
    return "plugin-cost";
  }

  @Override
  public void start() {
  }

  @Override
  public void stop() {
  }

  @Override
  public void postStart() {
    PluginManager pluginManager = Bukkit.getPluginManager();
    if (pluginManager == null) {
      return;
    }

    for (Plugin plugin : pluginManager.getPlugins()) {
      registerFor(plugin);
    }
  }

  @EventHandler
  public void on(PluginEnableEvent event) {
    registerFor(event.getPlugin());
  }

  @EventHandler
  public void on(PluginDisableEvent event) {
    unregisterFor(event.getPlugin());
  }

  private void registerFor(Plugin plugin) {
    if (plugin == null || isExcluded(plugin)) {
      return;
    }

    SampleController sampleController = React.controller(SampleController.class);
    if (sampleController == null) {
      return;
    }

    sampleController.registerSampler(new SamplerPluginCost(plugin.getName()));
  }

  private void unregisterFor(Plugin plugin) {
    if (plugin == null) {
      return;
    }

    SampleController sampleController = React.controller(SampleController.class);
    if (sampleController == null) {
      return;
    }

    sampleController.unregisterSampler(SamplerPluginCost.idFor(plugin.getName()));
  }

  private boolean isExcluded(Plugin plugin) {
    if (plugin == React.instance) {
      return true;
    }

    String name = plugin.getName();
    if (name == null || name.isBlank()) {
      return true;
    }

    if (name.equalsIgnoreCase("React")) {
      return true;
    }

    return IntegrationCapabilitySupport.integrationPluginFor(name.toLowerCase(Locale.ROOT) + "-") != null;
  }
}
