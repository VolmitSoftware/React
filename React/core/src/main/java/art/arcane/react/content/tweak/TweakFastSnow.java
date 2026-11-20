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
import art.arcane.react.platform.GameLocation;
import art.arcane.react.platform.GrowthHost;
import art.arcane.react.platform.ListenerPriority;
import art.arcane.react.platform.ReactPlatforms;
import art.arcane.react.platform.Subscription;
import art.arcane.react.util.common.scheduling.J;

@art.arcane.react.util.project.config.ConfigDescription("Configuration for Fast Snow tweak. Short-circuits snow form and melt updates into fast world operations to reduce weather-block churn.")
public class TweakFastSnow extends ReactTweak {
  public static final String ID = "fast-snow";
  private static final String SNOW = "SNOW";
  private transient Subscription forms = Subscription.NONE;
  private transient Subscription fades = Subscription.NONE;
  private transient GrowthHost growth;

  public TweakFastSnow() {
    super(ID);
  }

  @Override
  public void onActivate() {
    growth = ReactPlatforms.get().growthHost().orElse(null);
    if (growth == null) {
      return;
    }
    forms = growth.onForm(ListenerPriority.NORMAL, false, this::onForm);
    fades = growth.onFade(ListenerPriority.NORMAL, false, this::onFade);
  }

  @Override
  public void onDeactivate() {
    forms.cancel();
    fades.cancel();
    forms = Subscription.NONE;
    fades = Subscription.NONE;
  }

  void onForm(GrowthHost.ChangeEvent e) {
    if (SNOW.equals(e.type())) {
      e.cancel();
      GameLocation location = e.location();
      Object snow = e.state();
      J.s(location, () -> growth.fastSet(location, snow), 0);
    }
  }

  void onFade(GrowthHost.ChangeEvent e) {
    if (SNOW.equals(e.type())) {
      e.cancel();
      GameLocation location = e.location();
      J.s(location, () -> growth.breakNaturally(location, true), 0);
    }
  }
}
