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

package art.arcane.react.core.gui;

import art.arcane.react.React;
import art.arcane.react.util.common.scheduling.J;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class TextInputGui implements Listener {
  private final Player player;
  private volatile String response;
  private volatile boolean responded;

  public TextInputGui(Player player) {
    this.player = player;
    Bukkit.getPluginManager().registerEvents(this, React.instance);
    responded = false;
    response = null;
  }

  public static String captureText(Player p) {
    if (Bukkit.isPrimaryThread()) {
      throw new RuntimeException("Cannot open gui on main thread");
    }

    TextInputGui gui = new TextInputGui(p);

    try {
      while (!gui.responded) {
        if (!captureAvailable()) {
          return null;
        }
        if (!J.sleep(50)) {
          Thread.currentThread().interrupt();
          return null;
        }
      }

      return gui.response;
    } finally {
      gui.responded = true;
      HandlerList.unregisterAll(gui);
    }
  }

  @EventHandler
  public void on(PlayerQuitEvent e) {
    if (e.getPlayer().equals(player)) {
      responded = true;
      response = null;
      HandlerList.unregisterAll(this);
    }
  }

  @EventHandler(ignoreCancelled = false, priority = org.bukkit.event.EventPriority.HIGHEST)
  public void on(AsyncPlayerChatEvent e) {
    if (e.getPlayer().equals(player)) {
      e.setCancelled(true);
      response = e.getMessage();
      responded = true;
      HandlerList.unregisterAll(this);
    }
  }

  private static boolean captureAvailable() {
    return React.instance != null && React.instance.isEnabled() && React.instance.isReady();
  }
}
