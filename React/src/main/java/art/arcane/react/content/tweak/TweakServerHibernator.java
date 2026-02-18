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

import art.arcane.react.React;
import art.arcane.react.api.tweak.ReactTweak;
import art.arcane.react.util.scheduling.J;
import org.bukkit.Bukkit;
import org.bukkit.event.Listener;

@art.arcane.react.util.config.ConfigDescription("Configuration for Server Hibernator tweak. Experimental empty-server hibernation mode that sleeps between ticks when no players are online.")
public class TweakServerHibernator extends ReactTweak implements Listener {
  public static final String ID = "server-hibernator";
  private transient boolean firstRun = true;
  private transient int taskId = -1;
  @art.arcane.react.util.config.ConfigDoc(value = "Safety gate for the experimental server hibernation path.", impact = "Keep this false unless you explicitly want React to sleep ticks while the server is empty.")
  private boolean imACloudServerEnableMe = false;
  @art.arcane.react.util.config.ConfigDoc(value = "Seconds of delay applied per tick by server hibernator.", impact = "Higher values slow the loop more aggressively; lower values keep the server more responsive.")
  private double secondsPerTick = 1.0;


  public TweakServerHibernator() {
    super(ID);
  }

  @Override
  public void onActivate() {
    if (!imACloudServerEnableMe) {
      React.info("React Server Hibernator Disabled. Enable imACloudServerEnableMe in the config, if you know what you are doing.");
      return;
    }
    taskId = J.sr(() -> {
      if (Bukkit.getOnlinePlayers().size() == 0) {
        if (this.firstRun) {
          React.info("React Engaged Catatonic Sleep.");
          Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "save-all");
        }
        try {
          Thread.sleep((long) (1000 * secondsPerTick));
          this.firstRun = false;
        } catch (InterruptedException ex) {
          Thread.currentThread().interrupt();
          React.warn("Server hibernator sleep interrupted: " + ex.getClass().getSimpleName()
              + (ex.getMessage() == null ? "" : " - " + ex.getMessage()));
        } catch (Exception ex) {
          React.warn("Server hibernator sleep failed: " + ex.getClass().getSimpleName()
              + (ex.getMessage() == null ? "" : " - " + ex.getMessage()));
          React.reportError(ex);
        }
      } else {
        if (!firstRun) {
          React.info("React Disengaged Catatonic Sleep, Waking Up.");
        }
        this.firstRun = true;
      }

    }, 1);

  }

  @Override
  public void onDeactivate() {
    if (!imACloudServerEnableMe) {
      React.info("React Server Hibernator Disabled. Enable imACloudServerEnableMe in the config, if you know what you are doing.");
      return;
    }
    if (taskId != -1) {
      J.csr(taskId);
      taskId = -1;
    }
  }

  @Override
  public int getTickInterval() {
    return -1;
  }

  @Override
  public void onTick() {
  }
}
