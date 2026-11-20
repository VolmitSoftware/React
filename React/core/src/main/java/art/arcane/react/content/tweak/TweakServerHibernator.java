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
import art.arcane.react.platform.ReactPlatform;
import art.arcane.react.platform.ReactPlatforms;
import art.arcane.react.platform.WorldHost;
import art.arcane.react.util.common.scheduling.J;

@art.arcane.react.util.project.config.ConfigDescription("Configuration for Server Hibernator tweak. Experimental empty-server hibernation mode that sleeps between ticks when no players are online.")
public class TweakServerHibernator extends ReactTweak {
  public static final String ID = "server-hibernator";
  private transient boolean firstRun = true;
  private transient int taskId = -1;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Safety gate for the experimental server hibernation path.", impact = "Keep this false unless you explicitly want React to sleep ticks while the server is empty.")
  private boolean imACloudServerEnableMe = false;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Seconds of delay applied per tick by server hibernator.", impact = "Higher values slow the loop more aggressively; lower values keep the server more responsive.")
  private double secondsPerTick = 1.0;


  public TweakServerHibernator() {
    super(ID);
  }

  @Override
  public void onActivate() {
    ReactPlatform platform = ReactPlatforms.get();
    if (!imACloudServerEnableMe) {
      platform.verbose("React Server Hibernator Disabled. Enable imACloudServerEnableMe in the config, if you know what you are doing.");
      return;
    }
    WorldHost worlds = platform.worldHost().orElse(null);
    if (worlds == null) {
      return;
    }
    taskId = J.sr(() -> {
      if (worlds.onlinePlayerCount() == 0) {
        if (this.firstRun) {
          platform.info("React Engaged Catatonic Sleep.");
          platform.dispatchConsoleCommand("save-all");
        }
        try {
          Thread.sleep((long) (1000 * secondsPerTick));
          this.firstRun = false;
        } catch (InterruptedException ex) {
          Thread.currentThread().interrupt();
          platform.verbose("Server hibernator sleep interrupted");
        } catch (Exception ex) {
          platform.warn("Server hibernator sleep failed", ex);
        }
      } else {
        if (!firstRun) {
          platform.info("React Disengaged Catatonic Sleep, Waking Up.");
        }
        this.firstRun = true;
      }

    }, 1);

  }

  @Override
  public void onDeactivate() {
    if (!imACloudServerEnableMe) {
      ReactPlatforms.get().verbose("React Server Hibernator Disabled. Enable imACloudServerEnableMe in the config, if you know what you are doing.");
      return;
    }
    if (taskId != -1) {
      J.csr(taskId);
      taskId = -1;
    }
  }
}
