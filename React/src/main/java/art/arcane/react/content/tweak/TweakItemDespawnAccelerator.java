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

@art.arcane.react.util.project.config.ConfigDescription("Configuration for Item Despawn Accelerator tweak. Pushes eligible dropped items toward despawn when no players are nearby.")
public class TweakItemDespawnAccelerator extends ReactTweak implements Listener {
  public static final String ID = "item-despawn-accelerator";
  @art.arcane.react.util.project.config.ConfigDoc(value = "Ticks-lived value forced onto eligible items to accelerate vanilla despawn timing.", impact = "Higher values push items closer to despawn immediately; lower values keep more natural lifetime.")
  private int targetTicksLived = 5600;
  @art.arcane.react.util.project.config.ConfigDoc(value = "No player radius used by item despawn accelerator (blocks).", impact = "Higher values widen the search area and cost more work; lower values narrow scope and run cheaper.")
  private double noPlayerRadius = 48;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Skips named items when item despawn accelerator evaluates targets.", impact = "Enable this to exclude matching cases; disable it to include them in enforcement.")
  private boolean ignoreNamedItems = true;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Skips valuables when item despawn accelerator evaluates targets.", impact = "Enable this to exclude matching cases; disable it to include them in enforcement.")
  private boolean ignoreValuables = true;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Material list treated as valuables and excluded when `ignoreValuables` is enabled.", impact = "Add materials to protect more items from accelerated despawn, or remove materials to allow faster cleanup.")
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
}
