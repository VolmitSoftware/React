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

package art.arcane.react.content.tweak;

import art.arcane.react.api.sampler.Sampler;
import art.arcane.react.api.sampler.SamplerHosts;
import art.arcane.react.api.tweak.ReactTweak;
import art.arcane.react.content.sampler.SamplerHopperEventSpan;
import art.arcane.react.platform.HopperHost;
import art.arcane.react.platform.ReactPlatforms;
import art.arcane.react.platform.Subscription;

@art.arcane.react.util.project.config.ConfigDescription("Configuration for Hopper Limit tweak. Cancels hopper transfers when hopper event-span pressure exceeds the configured threshold.")
public class TweakHopperLimit extends ReactTweak {
  public static final String ID = "hopper-limit";
  @art.arcane.react.util.project.config.ConfigDoc(value = "Maximum hopper event span allowed by hopper limit.", impact = "Higher values allow more throughput before intervention; lower values make mitigation more aggressive.")
  private double maxHopperEventSpan = 0.75;
  private transient Subscription moves = Subscription.NONE;

  public TweakHopperLimit() {
    super(ID);
  }

  @Override
  public void onActivate() {
    moves = ReactPlatforms.get().hopperHost().map(host -> host.onMove(this::on)).orElse(Subscription.NONE);
  }

  @Override
  public void onDeactivate() {
    moves.cancel();
    moves = Subscription.NONE;
  }

  void on(HopperHost.Move move) {
    if (move.destinationIsHopper()) {
      Sampler span = SamplerHosts.sampler(SamplerHopperEventSpan.ID);
      if (span != null && span.sample() > maxHopperEventSpan) {
        move.cancel();
      }
    }
  }
}
