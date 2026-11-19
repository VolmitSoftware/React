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
import art.arcane.react.platform.SamplerEventCounters;
import art.arcane.react.platform.WorldStatsHost;
import art.arcane.volmlib.util.scheduling.ChronoLatch;
import art.arcane.react.api.sampler.ReactCachedSampler;
import art.arcane.react.util.common.scheduling.J;
import art.arcane.volmlib.util.format.Form;

import java.util.concurrent.atomic.AtomicInteger;

public class SamplerChunks extends ReactCachedSampler {
  public static final String ID = "chunks";
  private transient final AtomicInteger loadedChunks;
  private transient ChronoLatch realCheckUpdate;
  private int realityCheckMS = 10000;

  public SamplerChunks() {
    super(ID, 50);
    loadedChunks = new AtomicInteger(0);
    realCheckUpdate = new ChronoLatch(realityCheckMS);
    countWhileRunning(SamplerEventCounters.Counter.CHUNK_LOAD, (chunk, amount) -> loadedChunks.incrementAndGet());
    countWhileRunning(SamplerEventCounters.Counter.CHUNK_UNLOAD, (chunk, amount) -> loadedChunks.decrementAndGet());
    countWhileRunning(SamplerEventCounters.Counter.WORLD_UNLOAD, (world, amount) -> loadedChunks.set(Math.max(0, loadedChunks.get())));
  }

  @Override
  public String getIcon() {
    return "minecraft:chest_minecart";
  }

  public int getRealCheck() {
    return countWorldChunks();
  }

  @Override
  public void start() {
    super.start();
    realCheckUpdate = new ChronoLatch(realityCheckMS);
    refreshChunkCount();
  }

  @Override
  public double onSample() {
    if (realCheckUpdate.flip() || loadedChunks.get() < 0) {
      refreshChunkCount();
    }

    return loadedChunks.get();
  }

  static int countWorldChunks() {
    return SamplerHosts.worlds().map(WorldStatsHost::loadedChunkCount).orElse(0);
  }

  private void refreshChunkCount() {
    J.sync(() -> loadedChunks.set(countWorldChunks()));
  }

  @Override
  public String formattedValue(double t) {
    return Form.f(Math.round(t));
  }

  @Override
  public String formattedSuffix(double t) {
    return "CHK";
  }
}
