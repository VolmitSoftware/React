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

import org.bukkit.entity.Player;

/**
 * React's settings menu on Bukkit. The screens and edits live in {@link ReactSettingsMenu}.
 */
public final class ReactConfigGUI {
  private static final ReactSettingsMenu MENU = new ReactSettingsMenu(new BukkitSettingsSource(), BukkitSettingsScreenHost.INSTANCE);

  private ReactConfigGUI() {
  }

  public static ReactSettingsMenu menu() {
    return MENU;
  }

  public static boolean canConfigure(Player player) {
    return player != null && BukkitSettingsSource.canConfigure(player);
  }

  public static void open(Player player) {
    if (player != null) {
      MENU.open(player.getUniqueId());
    }
  }

  public static boolean applyAndSave(Player actor, String valuePath, Object value) {
    return MENU.applyAndSave(actor == null ? null : actor.getUniqueId(), valuePath, value);
  }

  public static int applyPresetHeadless(String preset) {
    return MENU.applyPresetHeadless(preset);
  }
}
