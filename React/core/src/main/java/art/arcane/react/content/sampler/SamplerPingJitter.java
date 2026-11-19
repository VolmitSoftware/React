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
import art.arcane.react.platform.PlayerStatsHost;
import art.arcane.react.api.sampler.ReactCachedSampler;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.math.RollingSequence;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class SamplerPingJitter extends ReactCachedSampler {
  public static final String ID = "ping-jitter";
  private transient RollingSequence avg;
  private transient Map<UUID, Integer> lastPing;
  private int averagingSamples = 20;

  public SamplerPingJitter() {
    super(ID, 1000);
  }

  @Override
  public void start() {
    super.start();
    avg = new RollingSequence(Math.max(1, averagingSamples));
    lastPing = new HashMap<>();
  }

  @Override
  public String getIcon() {
    return "minecraft:clock";
  }

  @Override
  public double onSample() {
    return sampleOnMainThread(() -> {
      int count = 0;
      double jitter = 0;
      Set<UUID> online = new HashSet<>();
      for (Map.Entry<UUID, Integer> entry : playerPings().entrySet()) {
        Integer ping = entry.getValue();
        UUID id = entry.getKey();
        online.add(id);
        Integer previous = lastPing.put(id, ping);
        if (previous != null) {
          jitter += Math.abs(ping - previous);
          count++;
        }
      }

      lastPing.keySet().retainAll(online);
      avg.put(count <= 0 ? 0 : jitter / count);
      return avg.getAverage();
    });
  }

  private static Map<UUID, Integer> playerPings() {
    return SamplerHosts.players().map(PlayerStatsHost::playerPings).orElse(Map.of());
  }

  @Override
  public String formattedValue(double t) {
    return Form.f(t, 2);
  }

  @Override
  public String formattedSuffix(double t) {
    return "ms JITTER";
  }
}
