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

package art.arcane.react.content.sampler;

import art.arcane.react.api.sampler.SamplerHosts;
import art.arcane.react.platform.WorldStatsHost;
import art.arcane.react.api.sampler.ReactCachedSampler;
import art.arcane.react.util.common.scheduling.J;
import art.arcane.volmlib.util.format.Form;

public class SamplerEntitiesHostile extends ReactCachedSampler {
  public static final String ID = "entities-hostile";

  public SamplerEntitiesHostile() {
    super(ID, 2000);
  }

  @Override
  public void start() {
    super.start();
    SamplerHosts.worlds().ifPresent(WorldStatsHost::acquireEntityCensus);
  }

  @Override
  public void stop() {
    SamplerHosts.worlds().ifPresent(WorldStatsHost::releaseEntityCensus);
    super.stop();
  }

  @Override
  public String getIcon() {
    return "minecraft:zombie_head";
  }

  @Override
  public double onSample() {
    if (J.isFoliaThreading()) {
      SamplerHosts.worlds().ifPresent(WorldStatsHost::refreshEntityCensusConcurrently);
      return census();
    }

    return sampleOnMainThread(() -> {
      SamplerHosts.worlds().ifPresent(WorldStatsHost::refreshEntityCensusOnMainThread);
      return census();
    });
  }

  private static double census() {
    return SamplerHosts.worlds().map(host -> (double) host.entityCensus(WorldStatsHost.EntityCategory.HOSTILE)).orElse(0D);
  }

  @Override
  public String format(double t) {
    return formattedValue(t) + formattedSuffix(t);
  }

  @Override
  public String formattedValue(double t) {
    return Form.f(Math.round(t));
  }

  @Override
  public String formattedSuffix(double t) {
    return " mobs";
  }
}
