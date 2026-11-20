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
package art.arcane.react.core.controller;

import art.arcane.react.core.gui.ReactConfigGUI;
import art.arcane.react.core.gui.SettingsInputSessions;
import art.arcane.react.util.plugin.IController;
import art.arcane.react.util.project.config.ConfigDoc;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class ConfigInputController implements IController, Listener {
  private final transient SettingsInputSessions sessions = new SettingsInputSessions(ReactConfigGUI::menu, () -> sessionTimeoutSeconds());
  @ConfigDoc(
      value = "Timeout for chat-based config edit sessions in seconds.",
      impact = "Higher values keep edit prompts open longer before they are cancelled."
  )
  private int sessionTimeoutSeconds = 45;

  @Override
  public String getName() {
    return "Config Input";
  }

  @Override
  public String getId() {
    return "config-input";
  }

  @Override
  public void start() {
    sessions.clear();
  }

  @Override
  public void stop() {
    sessions.clear();
  }

  @Override
  public void postStart() {

  }

  public SettingsInputSessions sessions() {
    return sessions;
  }

  private int sessionTimeoutSeconds() {
    return sessionTimeoutSeconds;
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void onAsyncChat(AsyncPlayerChatEvent event) {
    if (sessions.onChat(event.getPlayer().getUniqueId(), event.getMessage())) {
      event.setCancelled(true);
    }
  }

  @EventHandler
  public void onQuit(PlayerQuitEvent event) {
    sessions.forget(event.getPlayer().getUniqueId());
  }
}
