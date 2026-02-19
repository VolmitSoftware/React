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
import art.arcane.react.util.project.config.ConfigDoc;
import art.arcane.react.util.format.C;
import art.arcane.react.util.plugin.IController;
import art.arcane.react.util.common.scheduling.J;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ConfigInputController implements IController, Listener {
  private final transient Map<UUID, PendingInput> sessions = new ConcurrentHashMap<>();
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

  public void beginSession(Player player, String valuePath, String returnSectionPath, int returnPage, Class<?> targetType, String label) {
    if (player == null) {
      return;
    }

    long timeoutMs = Math.max(5, sessionTimeoutSeconds) * 1000L;
    PendingInput pending = new PendingInput(
        player.getUniqueId(),
        valuePath,
        returnSectionPath == null ? "" : returnSectionPath,
        Math.max(0, returnPage),
        targetType,
        label == null ? valuePath : label,
        System.currentTimeMillis() + timeoutMs
    );
    sessions.put(player.getUniqueId(), pending);

    player.closeInventory();
    player.sendMessage(C.AQUA + "Enter value for " + C.WHITE + pending.label());
    player.sendMessage(C.AQUA + "Path: " + C.WHITE + pending.valuePath());
    player.sendMessage(C.AQUA + "Expected type: " + C.WHITE + ReactConfigGUI.typeName(targetType));
    player.sendMessage(C.GRAY + "Type " + C.WHITE + "cancel" + C.GRAY + " to abort.");
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void onAsyncChat(AsyncPlayerChatEvent event) {
    Player player = event.getPlayer();
    PendingInput pending = sessions.get(player.getUniqueId());
    if (pending == null) {
      return;
    }

    event.setCancelled(true);

    if (pending.isExpired()) {
      sessions.remove(player.getUniqueId());
      J.s(() -> {
        player.sendMessage(C.RED + "Config input timed out.");
        ReactConfigGUI.open(player, pending.returnSectionPath(), pending.returnPage());
      });
      return;
    }

    String message = event.getMessage() == null ? "" : event.getMessage();
    if (message.equalsIgnoreCase("cancel")) {
      sessions.remove(player.getUniqueId());
      J.s(() -> {
        player.sendMessage(C.YELLOW + "Config edit cancelled.");
        ReactConfigGUI.open(player, pending.returnSectionPath(), pending.returnPage());
      });
      return;
    }

    ReactConfigGUI.ParseResult parsed = ReactConfigGUI.parseInputValue(pending.targetType(), message);
    if (!parsed.success()) {
      J.s(() -> {
        player.sendMessage(C.RED + parsed.error());
        player.sendMessage(C.GRAY + "Try again or type " + C.WHITE + "cancel");
      });
      return;
    }

    sessions.remove(player.getUniqueId());
    Object value = parsed.value();
    J.s(() -> ReactConfigGUI.confirmAndApply(player, pending.returnSectionPath(), pending.returnPage(), pending.valuePath(), value));
  }

  @EventHandler
  public void onQuit(PlayerQuitEvent event) {
    sessions.remove(event.getPlayer().getUniqueId());
  }

  private record PendingInput(
      UUID playerId,
      String valuePath,
      String returnSectionPath,
      int returnPage,
      Class<?> targetType,
      String label,
      long expiresAt
  ) {
    private boolean isExpired() {
      return System.currentTimeMillis() >= expiresAt;
    }
  }
}
