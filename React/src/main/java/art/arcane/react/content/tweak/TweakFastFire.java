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
import art.arcane.react.util.scheduling.J;
import art.arcane.react.util.world.FastWorld;
import org.bukkit.block.data.type.Fire;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockSpreadEvent;

@art.arcane.react.util.config.ConfigDescription("Configuration for Fast Fire tweak. Short-circuits fire spread and burnout updates into fast world operations to lower fire-physics overhead.")
public class TweakFastFire extends ReactTweak implements Listener {
    public static final String ID = "fast-fire";

    public TweakFastFire() {
        super(ID);
    }

    @Override
    public void onActivate() {

    }

    @EventHandler
    public void on(BlockSpreadEvent e) {
        if (e.getBlock().getBlockData() instanceof Fire f) {
            e.setCancelled(true);
            J.s(() -> FastWorld.set(e.getBlock(), f));
        }
    }

    @EventHandler
    public void on(BlockFadeEvent e) {
        if (e.getBlock().getBlockData() instanceof Fire) {
            e.setCancelled(true);
            J.s(() -> FastWorld.breakNaturally(e.getBlock()));
        }
    }

    @EventHandler
    public void on(BlockBurnEvent e) {
        e.setCancelled(true);
        J.s(() -> FastWorld.breakNaturally(e.getBlock()));
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
