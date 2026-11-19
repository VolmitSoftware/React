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

import art.arcane.react.api.sampler.ReactEventSampler;
import art.arcane.react.core.controller.EventController;
import art.arcane.react.platform.EventMeasurement;
import art.arcane.react.platform.cost.OwnerCostModel;
import art.arcane.volmlib.util.format.Form;
import net.kyori.adventure.text.Component;

public class SamplerPluginCost extends ReactEventSampler {
  private final transient String pluginName;

  public SamplerPluginCost(String pluginName) {
    super(idFor(pluginName), 1000);
    this.pluginName = pluginName == null ? "" : pluginName.trim();
  }

  public static String idFor(String pluginName) {
    return OwnerCostModel.samplerId(pluginName);
  }

  @Override
  public String getName() {
    return pluginName;
  }

  @Override
  public String getIcon() {
    return "minecraft:note_block";
  }

  @Override
  protected double onMeasuredSample(EventMeasurement measurement) {
    if (!(measurement instanceof EventController controller)) {
      return 0D;
    }
    return controller.getCostModel().sampleAverage(pluginName);
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
