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
import art.arcane.react.platform.EntityEventHost;
import art.arcane.react.platform.GameEntity;
import art.arcane.react.platform.ItemHost;
import art.arcane.react.platform.ReactPlatform;
import art.arcane.react.platform.ReactPlatforms;
import art.arcane.react.platform.Subscription;

import java.util.List;

@art.arcane.react.util.project.config.ConfigDescription("Configuration for Item Despawn Accelerator tweak. Pushes eligible dropped items toward despawn when no players are nearby.")
public class TweakItemDespawnAccelerator extends ReactTweak {
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
  private List<String> valuableItems = List.of(
      "NETHERITE_INGOT",
      "NETHERITE_SCRAP",
      "NETHER_STAR",
      "DIAMOND",
      "ANCIENT_DEBRIS",
      "ELYTRA",
      "TOTEM_OF_UNDYING"
  );
  private transient Subscription spawns = Subscription.NONE;
  private transient volatile ItemHost items;
  private transient volatile EntityEventHost events;

  public TweakItemDespawnAccelerator() {
    super(ID);
  }

  @Override
  public void onActivate() {
    ItemHost host = items();
    spawns = host == null ? Subscription.NONE : host.onSpawn(this::on);
  }

  @Override
  public void onDeactivate() {
    spawns.cancel();
    spawns = Subscription.NONE;
  }

  void on(GameEntity item) {
    ItemHost host = items();
    EntityEventHost checks = events();
    if (host == null || checks == null || !item.isValid()) {
      return;
    }

    if (ignoreNamedItems && item.hasCustomName()) {
      return;
    }

    if (ignoreValuables && valuableItems.contains(host.material(item))) {
      return;
    }

    if (!checks.nearPlayer(item, noPlayerRadius)) {
      host.setTicksLived(item, Math.max(item.ticksLived(), targetTicksLived));
    }
  }

  private ItemHost items() {
    ItemHost host = items;
    if (host == null) {
      ReactPlatform platform = ReactPlatforms.getOrNull();
      host = platform == null ? null : platform.itemHost().orElse(null);
      items = host;
    }
    return host;
  }

  private EntityEventHost events() {
    EntityEventHost host = events;
    if (host == null) {
      ReactPlatform platform = ReactPlatforms.getOrNull();
      host = platform == null ? null : platform.entityEventHost().orElse(null);
      events = host;
    }
    return host;
  }
}
