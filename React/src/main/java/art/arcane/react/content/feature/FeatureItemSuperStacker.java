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

package art.arcane.react.content.feature;

import art.arcane.chrono.ChronoLatch;
import art.arcane.react.React;
import art.arcane.react.api.feature.FeatureIntegrityListener;
import art.arcane.react.api.feature.ReactFeature;
import art.arcane.react.content.sampler.SamplerEntities;
import art.arcane.react.core.controller.EntityController;
import art.arcane.react.util.project.world.BundleUtils;
import art.arcane.volmlib.util.math.RNG;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Stacks items into bundles
 */
@art.arcane.react.util.project.config.ConfigDescription("Configuration for Item Super Stacker feature. This feature continuously monitors server behavior and applies guardrails during runtime.")
public class FeatureItemSuperStacker extends ReactFeature implements FeatureIntegrityListener {
  public static final String ID = "item-super-stacker";
  @art.arcane.react.util.project.config.ConfigDoc(value = "Maximum items allowed per bundle in item super stacker.", impact = "Higher values permit larger bursts before control engages; lower values clamp spikes sooner.")
  private int maxItemsPerBundle = 64;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Search radius used by item super stacker (blocks).", impact = "Higher values widen the search area and cost more work; lower values narrow scope and run cheaper.")
  private double searchRadius = 3;
  private transient ChronoLatch cl = new ChronoLatch(10);
  private transient final Consumer<Entity> entityTickListener = this::onItemTick;
  private transient volatile boolean active;

  public FeatureItemSuperStacker() {
    super(ID);
  }

  public boolean isSuperStack(Item item) {
    return BundleUtils.isBundle(item.getItemStack()) && BundleUtils.isFlagged(item.getItemStack());
  }

  public List<ItemStack> explode(Item item) {
    ItemStack m = item.getItemStack();
    List<ItemStack> contents = BundleUtils.isFlagged(m) ? BundleUtils.explode(m) : List.of(m);
    removeTrackedItem(item);
    return contents;
  }

  public void effectMerge(Item item, Item into) {
    Location buf = item.getLocation().clone();
    item.getWorld().spawnParticle(Particle.ITEM, item.getLocation(), 7, 0.1, 0.1, 0.1, 0.1, item.getItemStack());

    Vector j = into.getLocation().clone().subtract(item.getLocation()).toVector().normalize().multiply(into.getLocation().clone().distance(item.getLocation()) / (searchRadius * 2));
    for (int i = 0; i < searchRadius * 2; i++) {
      buf = buf.clone().add(j);
      item.getWorld().spawnParticle(Particle.ITEM, buf, 3, 0, 0, 0, 0, item.getItemStack());
    }

    if (cl.flip()) {
      // audience delivery: spigot Player has no playSound(net.kyori Sound)
      item.getWorld().getPlayers().forEach(player ->
          React.audiences().player(player).playSound(Sound.sound(
              Key.key("minecraft:item.bundle.insert"),
              Sound.Source.NEUTRAL,
              0.5f,
              1.2f + RNG.r.f(-0.1f, 0.1f)
          ), item.getLocation().getX(), item.getLocation().getY(), item.getLocation().getZ())
      );
    }
  }

  public void mergeWithNearbyItems(Item item) {
    if (!active || item.isDead()) {
      return;
    }


    for (Entity i : item.getWorld().getNearbyEntities(item.getLocation(), searchRadius, searchRadius, searchRadius)) {
      if (i instanceof Item into) {
        if (into.isDead() || into.getUniqueId().equals(item.getUniqueId())) {
          continue;
        }

        ItemStack is = BundleUtils.merge(item.getItemStack(), into.getItemStack(), maxItemsPerBundle);

        if (is != null) {
          effectMerge(item, into);
          removeTrackedItem(item);
          into.setItemStack(is);
          break;
        }
      }
    }
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void on(EntityPickupItemEvent e) {
    if (e.getEntity() instanceof Player p) {
      if (isSuperStack(e.getItem())) {
        e.setCancelled(true);
        for (ItemStack i : explode(e.getItem())) {
          p.getInventory().addItem(i).values().forEach((g) -> p.getWorld().dropItem(p.getLocation(), g));
        }

        if (cl.flip()) {
          React.audiences().player(p).playSound(Sound.sound(
              Key.key("minecraft:item.bundle.drop_contents"),
              Sound.Source.PLAYER,
              1f,
              0.85f + RNG.r.f(-0.1f, 0.1f)
          ), e.getItem().getLocation().getX(), e.getItem().getLocation().getY(), e.getItem().getLocation().getZ());
        }
      }
    }
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void on(InventoryClickEvent e) {
    if (e.getWhoClicked() instanceof Player p) {
      if (e.getCurrentItem() != null && BundleUtils.isFlagged(e.getCurrentItem())) {
        ItemStack i = e.getCurrentItem();
        List<ItemStack> items = BundleUtils.explode(i);
        e.setCancelled(true);
        e.setCurrentItem(null);
        for (ItemStack j : items) {
          p.getInventory().addItem(j).values().forEach((g) -> p.getWorld().dropItem(p.getLocation(), g));
        }
      }
    }
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void on(InventoryPickupItemEvent event) {
    Item item = event.getItem();
    if (!isSuperStack(item)) {
      return;
    }

    event.setCancelled(true);
    List<ItemStack> leftovers = insertContents(event.getInventory(), BundleUtils.explode(item.getItemStack()));
    if (leftovers.isEmpty()) {
      removeTrackedItem(item);
      return;
    }

    ItemStack residualBundle = BundleUtils.createBundle(leftovers);
    if (residualBundle != null) {
      item.setItemStack(residualBundle);
      return;
    }

    Location location = item.getLocation();
    World world = item.getWorld();
    removeTrackedItem(item);
    for (ItemStack leftover : leftovers) {
      world.dropItemNaturally(location, leftover);
    }
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(ItemSpawnEvent e) {
    mergeWithNearbyItems(e.getEntity());
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void on(EntityDamageEvent e) {
    if (!(e.getEntity() instanceof Item item)) {
      return;
    }

    if (e.getCause() == EntityDamageEvent.DamageCause.VOID) {
      return;
    }

    if (isSuperStack(item)) {
      e.setCancelled(true);
    }
  }

  @Override
  public void onActivate() {
    active = true;
    EntityController controller = React.controller(EntityController.class);
    if (controller != null) {
      controller.registerEntityTickListener(EntityType.ITEM, entityTickListener);
    }
  }

  @Override
  public void onDeactivate() {
    active = false;
    EntityController controller = React.controller(EntityController.class);
    if (controller != null) {
      controller.unregisterEntityTickListener(entityTickListener);
    }
  }

  private void onItemTick(Entity entity) {
    if (entity instanceof Item item) {
      mergeWithNearbyItems(item);
    }
  }

  private List<ItemStack> insertContents(Inventory inventory, List<ItemStack> contents) {
    List<ItemStack> leftovers = new ArrayList<>();
    for (ItemStack content : contents) {
      Map<Integer, ItemStack> overflow = inventory.addItem(content.clone());
      leftovers.addAll(overflow.values());
    }
    return leftovers;
  }

  private void removeTrackedItem(Item item) {
    if (React.instance != null) {
      FeatureHopperItemIndex hopperItemIndex = React.feature(FeatureHopperItemIndex.class);
      if (hopperItemIndex != null && hopperItemIndex.getItemIndex() != null) {
        hopperItemIndex.getItemIndex().removeItem(item.getUniqueId());
      }

      SamplerEntities sampler = React.sampler(SamplerEntities.ID);
      if (sampler != null) {
        sampler.getEntities().decrementAndGet();
      }
    }
    item.remove();
  }
}
