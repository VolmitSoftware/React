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

import io.papermc.paper.event.player.PlayerItemFrameChangeEvent;
import org.bukkit.entity.ItemFrame;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

// PlayerItemFrameChangeEvent is Paper-only, so it lives in its own listener: a missing
// type here kills only this class instead of every MapController handler on Spigot.
public class MapFrameChangeListener implements Listener {
  private final MapController controller;

  public MapFrameChangeListener(MapController controller) {
    this.controller = controller;
  }

  @EventHandler(ignoreCancelled = true)
  public void on(PlayerItemFrameChangeEvent e) {
    ItemFrame frame = e.getItemFrame();
    if (frame == null) {
      return;
    }

    // PLACE/REMOVE/ROTATE all fire before the frame item settles; the controller
    // re-reads the frame next tick.
    controller.scheduleFrameRefresh(frame);
  }
}
