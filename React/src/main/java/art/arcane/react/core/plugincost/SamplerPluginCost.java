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

package art.arcane.react.core.plugincost;

import art.arcane.react.React;
import art.arcane.react.api.sampler.ReactCachedSampler;
import art.arcane.react.core.controller.EventController;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.math.RollingSequence;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;

import java.util.Locale;

public class SamplerPluginCost extends ReactCachedSampler {
  private final transient String pluginName;
  private final transient RollingSequence average = new RollingSequence(5);
  private transient EventController eventController;

  public SamplerPluginCost(String pluginName) {
    super(idFor(pluginName), 1000);
    this.pluginName = pluginName == null ? "" : pluginName.trim();
  }

  public static String idFor(String pluginName) {
    String base = (pluginName == null ? "" : pluginName).trim().toLowerCase(Locale.ROOT);
    return "plugin-" + base.replaceAll("[^a-z0-9_-]+", "-");
  }

  @Override
  public String getName() {
    return pluginName;
  }

  @Override
  public Material getIcon() {
    return Material.NOTE_BLOCK;
  }

  @Override
  public double onSample() {
    EventController controller = eventController;
    if (controller == null) {
      controller = React.controller(EventController.class);
      eventController = controller;
    }

    if (controller == null) {
      return 0D;
    }

    controller.markSamplerActivity();
    average.put(controller.getPluginEventTimeMS(pluginName));
    double windowSeconds = Math.max(1L, controller.getTinterval()) / 1000.0D;
    return average.getAverage() / windowSeconds;
  }

  @Override
  public void start() {
    super.start();
    eventController = React.controller(EventController.class);
  }

  @Override
  public String format(double t) {
    return formattedValue(t) + formattedSuffix(t);
  }

  @Override
  public Component format(Component value, Component suffix) {
    return Component.empty().append(value).append(suffix);
  }

  @Override
  public String formattedValue(double t) {
    return Form.f(t, 2);
  }

  @Override
  public String formattedSuffix(double t) {
    return " ms/s";
  }

  @Override
  public void loadConfiguration() {
  }

  @Override
  public boolean reloadConfiguration() {
    return true;
  }
}
