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

import art.arcane.react.api.tweak.ReactTweak;
import art.arcane.react.platform.BlockHost;
import art.arcane.react.platform.GameLocation;
import art.arcane.react.platform.GrowthHost;
import art.arcane.react.platform.ListenerPriority;
import art.arcane.react.platform.ReactPlatforms;
import art.arcane.react.platform.Subscription;
import art.arcane.react.util.common.scheduling.J;

@art.arcane.react.util.project.config.ConfigDescription("Configuration for Fast Fire tweak. Short-circuits fire spread and burnout updates into fast world operations to lower fire-physics overhead.")
public class TweakFastFire extends ReactTweak {
  public static final String ID = "fast-fire";
  private static final String FIRE = "FIRE";
  private transient Subscription spreads = Subscription.NONE;
  private transient Subscription fades = Subscription.NONE;
  private transient Subscription burns = Subscription.NONE;
  private transient GrowthHost growth;

  public TweakFastFire() {
    super(ID);
  }

  @Override
  public void onActivate() {
    growth = ReactPlatforms.get().growthHost().orElse(null);
    if (growth == null) {
      return;
    }
    spreads = growth.onSpread(ListenerPriority.NORMAL, false, this::onSpread);
    fades = growth.onFade(ListenerPriority.NORMAL, false, this::onFade);
    burns = growth.onBurn(ListenerPriority.NORMAL, false, this::onBurn);
  }

  @Override
  public void onDeactivate() {
    spreads.cancel();
    fades.cancel();
    burns.cancel();
    spreads = Subscription.NONE;
    fades = Subscription.NONE;
    burns = Subscription.NONE;
  }

  void onSpread(GrowthHost.SpreadEvent e) {
    if (FIRE.equals(e.type())) {
      e.cancel();
      GameLocation location = e.location();
      Object fire = e.state();
      J.s(location, () -> growth.fastSet(location, fire), 0);
    }
  }

  void onFade(GrowthHost.ChangeEvent e) {
    if (FIRE.equals(e.type())) {
      e.cancel();
      GameLocation location = e.location();
      J.s(location, () -> growth.breakNaturally(location, true), 0);
    }
  }

  void onBurn(BlockHost.BlockEvent e) {
    e.cancel();
    GameLocation location = e.location();
    J.s(location, () -> growth.breakNaturally(location, true), 0);
  }
}
