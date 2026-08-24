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
import art.arcane.react.core.controller.JobController;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.math.RollingSequence;
import org.bukkit.Material;

public class SamplerBacklogGrowthRate extends ReactCachedSampler {
  public static final String ID = "backlog-growth-rate";
  private transient RollingSequence avg;
  private transient int lastSize = 0;
  private transient long lastSampleMS = 0;
  private int averagingSamples = 12;

  public SamplerBacklogGrowthRate() {
    super(ID, 1000);
  }

  @Override
  public void start() {
    super.start();
    avg = new RollingSequence(Math.max(1, averagingSamples));
    lastSize = 0;
    lastSampleMS = 0;
  }

  @Override
  public Material getIcon() {
    return Material.COMPARATOR;
  }

  @Override
  public double onSample() {
    int currentSize = getQueueSize();
    long now = System.currentTimeMillis();
    if (lastSampleMS == 0) {
      lastSampleMS = now;
      lastSize = currentSize;
      return 0;
    }

    long elapsedMS = Math.max(1L, now - lastSampleMS);
    double elapsedSeconds = elapsedMS / 1000D;
    double growthPerSecond = (currentSize - lastSize) / elapsedSeconds;
    avg.put(growthPerSecond);
    lastSampleMS = now;
    lastSize = currentSize;
    return avg.getAverage();
  }

  private int getQueueSize() {
    JobController controller = React.controller(JobController.class);
    return controller.getQueueSize();
  }

  @Override
  public String formattedValue(double t) {
    return Form.f(t, 2);
  }

  @Override
  public String formattedSuffix(double t) {
    return "BACKLOG/s";
  }
}
