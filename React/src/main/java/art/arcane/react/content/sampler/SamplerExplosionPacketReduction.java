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

import art.arcane.react.React;
import art.arcane.react.api.sampler.ReactCachedSampler;
import art.arcane.react.content.feature.FeatureExplosionPacketBatching;
import art.arcane.volmlib.util.format.Form;
import org.bukkit.Material;

import java.util.function.LongSupplier;

public class SamplerExplosionPacketReduction extends ReactCachedSampler {
  public static final String ID = "explosion-packet-reduction";
  private static final int WINDOWS = 5;
  private static final long WINDOW_EXPIRY_MS = 60_000L;

  private transient final LongSupplier clock;
  private transient final long[] windowExplosions = new long[WINDOWS];
  private transient final long[] windowClusters = new long[WINDOWS];
  private transient final long[] windowRecordedAtMs = new long[WINDOWS];
  private transient int windowCursor;
  private transient int windowCount;
  private transient volatile boolean hasReduction;

  public SamplerExplosionPacketReduction() {
    this(System::currentTimeMillis);
  }

  SamplerExplosionPacketReduction(LongSupplier clock) {
    super(ID, 1000);
    this.clock = clock;
  }

  @Override
  public Material getIcon() {
    return Material.TNT;
  }

  @Override
  public void start() {
    super.start();
    windowCursor = 0;
    windowCount = 0;
    hasReduction = false;
  }

  @Override
  public double onSample() {
    long now = clock.getAsLong();
    FeatureExplosionPacketBatching feature = React.feature(FeatureExplosionPacketBatching.class);
    if (feature != null) {
      long explosions = feature.readAndResetExplosions();
      long clusters = feature.readAndResetClusters();
      if (explosions > 0L || clusters > 0L) {
        recordWindow(explosions, clusters, now);
      }
    }

    long totalExplosions = 0L;
    long totalClusters = 0L;
    for (int i = 0; i < windowCount; i++) {
      if (now - windowRecordedAtMs[i] > WINDOW_EXPIRY_MS) {
        continue;
      }
      totalExplosions += windowExplosions[i];
      totalClusters += windowClusters[i];
    }

    hasReduction = totalExplosions > 0L;
    if (!hasReduction) {
      return 0D;
    }

    return SamplerMath.clip(1D - ((double) totalClusters / (double) totalExplosions), 0D, 1D);
  }

  @Override
  public boolean isSampleAvailable() {
    return hasReduction;
  }

  @Override
  public String formattedValue(double t) {
    return Form.f(Math.round(t * 100D));
  }

  @Override
  public String formattedSuffix(double t) {
    return "%";
  }

  private void recordWindow(long explosions, long clusters, long now) {
    windowExplosions[windowCursor] = explosions;
    windowClusters[windowCursor] = clusters;
    windowRecordedAtMs[windowCursor] = now;
    windowCursor = (windowCursor + 1) % WINDOWS;
    windowCount = Math.min(WINDOWS, windowCount + 1);
  }
}
