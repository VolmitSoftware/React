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
import art.arcane.react.api.web.WebRevisions;
import art.arcane.react.core.integration.IntegrationCapabilitySupport;
import art.arcane.react.core.plugincost.SamplerPluginCost;
import art.arcane.react.platform.cost.OwnerGauge;
import art.arcane.react.platform.cost.OwnerGaugeSampler;
import art.arcane.react.util.common.scheduling.J;
import art.arcane.react.util.plugin.IController;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;

import java.util.Collection;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class PluginCostController implements IController, Listener {
  private static final int CHUNK_LOADER_INTERVAL_TICKS = 100;
  private volatile Map<String, Integer> chunkLoaders = Map.of();
  private int chunkLoaderTask = -1;

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
    if (chunkLoaderTask != -1) {
      J.csr(chunkLoaderTask);
      chunkLoaderTask = -1;
    }
    chunkLoaders = Map.of();
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
    if (!J.isFoliaThreading()) {
      chunkLoaderTask = J.sr(this::countChunkLoaders, CHUNK_LOADER_INTERVAL_TICKS);
    }
  }

  private void countChunkLoaders() {
    Map<String, Integer> counts = new HashMap<>();
    for (World world : Bukkit.getWorlds()) {
      for (Map.Entry<Plugin, Collection<Chunk>> tickets : world.getPluginChunkTickets().entrySet()) {
        counts.merge(tickets.getKey().getName(), tickets.getValue().size(), Integer::sum);
      }
    }
    chunkLoaders = counts;
    SampleController sampleController = React.controller(SampleController.class);
    if (sampleController == null) {
      return;
    }
    for (String plugin : counts.keySet()) {
      if (sampleController.getSampler(OwnerGauge.CHUNK_LOADERS.samplerId(plugin)) == null) {
        sampleController.registerSampler(new OwnerGaugeSampler(OwnerGauge.CHUNK_LOADERS, plugin, plugin,
            (String owner) -> chunkLoaders.getOrDefault(owner, 0)));
      }
    }
  }

  @EventHandler
  public void on(PluginEnableEvent event) {
    registerFor(event.getPlugin());
    WebRevisions.bump(WebRevisions.Kind.OWNERS);
  }

  @EventHandler
  public void on(PluginDisableEvent event) {
    unregisterFor(event.getPlugin());
    WebRevisions.bump(WebRevisions.Kind.OWNERS);
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
    sampleController.unregisterSampler(OwnerGauge.CHUNK_LOADERS.samplerId(plugin.getName()));
    EventController events = React.controller(EventController.class);
    if (events != null) {
      events.getCostModel().forgetOwner(plugin.getName());
    }
  }

  private boolean isExcluded(Plugin plugin) {
    String name = plugin.getName();
    if (name == null || name.isBlank()) {
      return true;
    }

    return IntegrationCapabilitySupport.integrationPluginFor(name.toLowerCase(Locale.ROOT) + "-") != null;
  }
}
