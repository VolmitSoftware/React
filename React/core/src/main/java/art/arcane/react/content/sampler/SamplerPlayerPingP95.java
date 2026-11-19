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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class SamplerPlayerPingP95 extends ReactCachedSampler {
  public static final String ID = "player-ping-p95";

  public SamplerPlayerPingP95() {
    super(ID, 1000);
  }

  @Override
  public String getIcon() {
    return "minecraft:clock";
  }

  @Override
  public double onSample() {
    return sampleOnMainThread(() -> {
      List<Double> pings = new ArrayList<>();
      for (Integer ping : playerPings().values()) {
        pings.add((double) Math.max(0, ping));
      }

      return SamplerMath.percentile(pings, 0.95D);
    });
  }

  private static Map<UUID, Integer> playerPings() {
    return SamplerHosts.players().map(PlayerStatsHost::playerPings).orElse(Map.of());
  }

  @Override
  public String formattedValue(double t) {
    return Form.f(t, 1);
  }

  @Override
  public String formattedSuffix(double t) {
    return "ms P95PING";
  }
}
