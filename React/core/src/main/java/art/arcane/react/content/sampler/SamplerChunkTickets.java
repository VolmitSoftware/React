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
import art.arcane.volmlib.util.format.Form;

import java.util.OptionalLong;

public class SamplerChunkTickets extends ReactCachedSampler {
  public static final String ID = "chunk-tickets";
  private transient volatile boolean available = true;

  public SamplerChunkTickets() {
    super(ID, 5000);
  }

  @Override
  public String getIcon() {
    return "minecraft:name_tag";
  }

  @Override
  public void start() {
    available = true;
    super.start();
  }

  @Override
  public double onSample() {
    return sampleOnMainThread(this::countTickets);
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
    return " tickets";
  }

  @Override
  public boolean isSampleAvailable() {
    return available && super.isSampleAvailable();
  }

  private double countTickets() {
    if (!available) {
      return 0D;
    }

    try {
      WorldStatsHost host = SamplerHosts.worlds().orElse(null);
      OptionalLong tickets = host == null ? OptionalLong.empty() : host.pluginChunkTicketCount();
      if (tickets.isEmpty()) {
        available = false;
        return 0D;
      }
      long total = tickets.getAsLong();
      return (double) total;
    } catch (RuntimeException | LinkageError failure) {
      available = false;
      SamplerHosts.warn("Could not sample plugin chunk tickets. The metric is unavailable until its sampler restarts.", failure);
      return 0D;
    }
  }
}
