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
import art.arcane.react.util.common.scheduling.J;
import art.arcane.react.util.project.world.FastWorld;
import org.bukkit.block.data.type.Snow;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockFormEvent;

@art.arcane.react.util.project.config.ConfigDescription("Configuration for Fast Snow tweak. Short-circuits snow form and melt updates into fast world operations to reduce weather-block churn.")
public class TweakFastSnow extends ReactTweak implements Listener {
  public static final String ID = "fast-snow";

  public TweakFastSnow() {
    super(ID);
  }

  @Override
  public void onActivate() {

  }

  @EventHandler
  public void on(BlockFormEvent e) {
    if (e.getBlock().getBlockData() instanceof Snow s) {
      e.setCancelled(true);
      var location = e.getBlock().getLocation().clone();
      J.s(location, () -> FastWorld.set(location.getBlock(), s), 0);
    }
  }

  @EventHandler
  public void on(BlockFadeEvent e) {
    if (e.getBlock().getBlockData() instanceof Snow s) {
      e.setCancelled(true);
      var location = e.getBlock().getLocation().clone();
      J.s(location, () -> FastWorld.breakNaturally(location.getBlock()), 0);
    }
  }

  @Override
  public void onDeactivate() {

  }

  @Override
  public int getTickInterval() {
    return -1;
  }

  @Override
  public void onTick() {

  }
}
