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

import art.arcane.react.api.sampler.ReactCachedSampler;
import art.arcane.react.api.sampler.SamplerHosts;
import art.arcane.react.platform.WorldStatsHost;
import art.arcane.react.util.common.scheduling.J;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.scheduling.ChronoLatch;
import lombok.Getter;

import java.util.concurrent.atomic.AtomicInteger;

public class SamplerEntities extends ReactCachedSampler {
  public static final String ID = "entities";
  @Getter
  private transient final AtomicInteger entities;
  private transient ChronoLatch realEntityUpdate;
  private transient WorldStatsHost.EntityTracking tracking;
  private int realityCheckMS = 10000;

  public SamplerEntities() {
    super(ID, 50);
    entities = new AtomicInteger(0);
    realEntityUpdate = new ChronoLatch(realityCheckMS);
  }

  @Override
  public String getIcon() {
    return "minecraft:chicken_spawn_egg";
  }

  @Override
  public boolean isChunkGauge() {
    return true;
  }

  public int getRealCheck() {
    return countWorldEntities();
  }

  @Override
  public void start() {
    super.start();
    stopTracking();
    entities.set(0);
    realEntityUpdate = new ChronoLatch(realityCheckMS);
    tracking = SamplerHosts.worlds().map(host -> host.trackEntities(this, entities)).orElse(null);
    refreshEntityCount();
  }

  @Override
  public void stop() {
    stopTracking();
    super.stop();
  }

  @Override
  public double onSample() {
    if (realEntityUpdate.flip() || entities.get() < 0) {
      refreshEntityCount();
    }

    return entities.get();
  }

  static int countWorldEntities() {
    return SamplerHosts.worlds().map(WorldStatsHost::entityCount).orElse(0);
  }

  private void stopTracking() {
    WorldStatsHost.EntityTracking current = tracking;
    tracking = null;
    if (current != null) {
      current.stop();
    }
  }

  private void refreshEntityCount() {
    J.sync(() -> entities.set(countWorldEntities()));
  }

  @Override
  public String formattedValue(double t) {
    return Form.f(Math.round(t));
  }

  @Override
  public String formattedSuffix(double t) {
    return "ENT";
  }
}
