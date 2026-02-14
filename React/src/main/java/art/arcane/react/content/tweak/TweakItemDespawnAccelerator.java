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
import org.bukkit.Material;
import org.bukkit.entity.Item;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ItemSpawnEvent;

import java.util.List;

public class TweakItemDespawnAccelerator extends ReactTweak implements Listener {
    public static final String ID = "item-despawn-accelerator";
    private int targetTicksLived = 5600;
    private double noPlayerRadius = 48;
    private boolean ignoreNamedItems = true;
    private boolean ignoreValuables = true;
    private List<Material> valuableItems = List.of(
            Material.NETHERITE_INGOT,
            Material.NETHERITE_SCRAP,
            Material.NETHER_STAR,
            Material.DIAMOND,
            Material.ANCIENT_DEBRIS,
            Material.ELYTRA,
            Material.TOTEM_OF_UNDYING
    );

    public TweakItemDespawnAccelerator() {
        super(ID);
    }

    @Override
    public void onActivate() {

    }

    @Override
    public void onDeactivate() {

    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void on(ItemSpawnEvent event) {
        Item item = event.getEntity();
        if (item.isDead()) {
            return;
        }

        if (ignoreNamedItems && item.getCustomName() != null) {
            return;
        }

        if (ignoreValuables && valuableItems.contains(item.getItemStack().getType())) {
            return;
        }

        if (!React.hasNearbyPlayer(item.getLocation(), noPlayerRadius)) {
            item.setTicksLived(Math.max(item.getTicksLived(), targetTicksLived));
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
