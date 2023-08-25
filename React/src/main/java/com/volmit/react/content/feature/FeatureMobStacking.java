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

package com.volmit.react.content.feature;

import com.volmit.react.React;
import com.volmit.react.api.feature.ReactFeature;
import com.volmit.react.content.sampler.SamplerEntities;
import com.volmit.react.core.NMS;
import com.volmit.react.core.controller.EntityController;
import com.volmit.react.model.ReactEntity;
import com.volmit.react.util.format.Form;
import com.volmit.react.util.scheduling.J;
import com.volmit.react.util.world.CustomMobChecker;
import org.bukkit.ChatColor;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.metadata.FixedMetadataValue;

import java.util.HashSet;
import java.util.Set;

public class FeatureMobStacking extends ReactFeature implements Listener {
    public static final String ID = "mob-stacking";
    private int maxStackSize = 10;
    private double maxHealth = 100;
    private Set<EntityType> stackableTypes = defaultStackableTypes();
    private boolean customNames = true;
    private double searchRadius = 6;
    private boolean vacuumEffect = true;
    private boolean skipCustomMobs = true;
    private boolean onlySpawnerMobs = false;

    public FeatureMobStacking() {
        super(ID);
    }

    public static Set<EntityType> defaultStackableTypes() {
        Set<EntityType> e = new HashSet<>();

        for (EntityType i : EntityType.values()) {
            if (i.isAlive() && i.isSpawnable()) {
                e.add(i);
            }
        }

        e.remove(EntityType.PLAYER);
        e.remove(EntityType.ARMOR_STAND);
        e.remove(EntityType.VILLAGER);
        e.remove(EntityType.WANDERING_TRADER);
        e.remove(EntityType.FALLING_BLOCK);

        return e;
    }

    @Override
    public void onActivate() {
        for (EntityType i : stackableTypes) {
            React.controller(EntityController.class).registerEntityTickListener(i, this::onTick);
        }
    }

    public void onDamage(EntityDamageEvent e) {
        if (getStackCount(e.getEntity()) > 1 && e.getEntity() instanceof LivingEntity l && l.getHealth() - e.getFinalDamage() <= 0) {
            int s = getStackCount(l) - 1;

            LivingEntity next;
            if (l instanceof Slime) {
                Slime oldSlime = (Slime) l;
                next = (LivingEntity) l.getWorld().spawnEntity(l.getLocation(), l.getType());
                if (oldSlime.getSize() > 1) { // This is to ensure no infinite loop of slime spawning
                    ((Slime) next).setSize(oldSlime.getSize() / 2); // setting the new size
                } else {
                    ((Slime) next).setSize(oldSlime.getSize());
                }
            } else if (l instanceof Sheep) {
                Sheep oldSheep = (Sheep) l;
                next = (LivingEntity) l.getWorld().spawnEntity(l.getLocation(), l.getType());
                ((Sheep) next).setColor(oldSheep.getColor()); // setting the new sheep color
            } else {
                next = (LivingEntity) l.getWorld().spawnEntity(l.getLocation(), l.getType());
            }

            next.setSwimming(l.isSwimming());
            next.setAI(l.hasAI());
            next.setCollidable(l.isCollidable());
            next.setCustomName(l.getCustomName());
            next.setCustomNameVisible(l.isCustomNameVisible());
            next.setGlowing(l.isGlowing());
            next.setGravity(l.hasGravity());
            next.setInvulnerable(l.isInvulnerable());
            next.setSilent(l.isSilent());
            next.setRemoveWhenFarAway(l.getRemoveWhenFarAway());
            next.setPersistent(l.isPersistent());
            next.setCanPickupItems(l.getCanPickupItems());
            next.setArrowsInBody(l.getArrowsInBody());
            next.setArrowCooldown(l.getArrowCooldown());
            next.setFreezeTicks(l.getFreezeTicks());
            next.setTicksLived(l.getTicksLived());
            next.setLastDamage(l.getLastDamage());
            next.setLastDamageCause(l.getLastDamageCause());
            next.setAbsorptionAmount(l.getAbsorptionAmount());
            next.setFireTicks(l.getFireTicks());
            next.setPortalCooldown(l.getPortalCooldown());
            next.setRotation(l.getEyeLocation().getYaw(), l.getEyeLocation().getPitch());
            next.setVelocity(l.getVelocity());
            next.setFallDistance(l.getFallDistance());
            next.setRemainingAir(l.getRemainingAir());
            next.setNoDamageTicks(l.getNoDamageTicks());
            setStackCount(l, 1);
            l.setCollidable(false);
            l.setInvisible(true);
            l.setAI(false);
            setStackCount(next, s);
        }
    }

    @EventHandler
    public void on(EntityDamageByEntityEvent e) {
        onDamage(e);
    }

    @EventHandler
    public void on(EntityDamageByBlockEvent e) {
        onDamage(e);
    }

    @EventHandler
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        // Check for sneak and right click
        if (event.getPlayer().isSneaking() && event.getHand().equals(EquipmentSlot.HAND)) {
            Entity clickedEntity = event.getRightClicked();


            // Check if entity is stackable and has more than 1 in the stack
            if (stackableTypes.contains(clickedEntity.getType()) && getStackCount(clickedEntity) > 1) {
                // Calculate new stack counts for split entities
                int newStackCount = getStackCount(clickedEntity) / 2;
                int remainingStackCount = getStackCount(clickedEntity) - newStackCount;

                // Create new entity with half of the original stack count
                LivingEntity newEntity;
                if (clickedEntity instanceof Sheep) {
                    Sheep oldSheep = (Sheep) clickedEntity;
                    newEntity = (LivingEntity) clickedEntity.getWorld().spawnEntity(clickedEntity.getLocation().add(0, 0.5, 0), clickedEntity.getType());
                    ((Sheep) newEntity).setColor(oldSheep.getColor()); // setting the new sheep color
                } else if (clickedEntity instanceof Slime) {
                    Slime oldSlime = (Slime) clickedEntity;
                    newEntity = (LivingEntity) clickedEntity.getWorld().spawnEntity(clickedEntity.getLocation().add(0, 0.5, 0), clickedEntity.getType());
                    if (oldSlime.getSize() > 1) { // This is to ensure no infinite loop of slime spawning
                        ((Slime) newEntity).setSize(oldSlime.getSize() / 2); // setting the new size
                    } else {
                        ((Slime) newEntity).setSize(oldSlime.getSize());
                    }
                } else {
                    newEntity = (LivingEntity) clickedEntity.getWorld().spawnEntity(clickedEntity.getLocation().add(0, 0.5, 0), clickedEntity.getType());
                }
                setStackCount(newEntity, newStackCount);
                newEntity.setMetadata("DoNotStack", new FixedMetadataValue(React.instance, true));
                newEntity.setMetadata("UniqueMobStack", new FixedMetadataValue(React.instance, true));
                updateEntityCustomName(newEntity);

                // Update original entity with the remaining stack count
                setStackCount(clickedEntity, remainingStackCount);
                clickedEntity.setMetadata("DoNotStack", new FixedMetadataValue(React.instance, true));
                clickedEntity.setMetadata("UniqueMobStack", new FixedMetadataValue(React.instance, true));
                updateEntityCustomName(clickedEntity);
            }
        }
    }

    // Method to update entity custom name based on its stack count
    public void updateEntityCustomName(Entity e) {
        int count = getStackCount(e);
        if (count > 1) {
            e.setCustomName(ChatColor.BOLD + "" + count + "x " + ChatColor.RESET + ChatColor.GRAY + "" + Form.capitalizeWords(e.getType().name().toLowerCase().replaceAll("\\Q_\\E", " ")));
        } else {
            e.setCustomName(ChatColor.GOLD + "" + count + "x UNIQUE" + ChatColor.RESET + ChatColor.GRAY + "" + Form.capitalizeWords(e.getType().name().toLowerCase().replaceAll("\\Q_\\E", " ")));
        }
    }


    // prevent the spam in the console that happens when a mob is killed by non-living damage
    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (getStackCount(entity) > 1 && !(entity.getLastDamageCause() instanceof EntityDamageByEntityEvent)) {
            entity.setCustomName(null);
        } else if (getStackCount(entity) > 1) {
            EntityDamageByEntityEvent damageEvent = (EntityDamageByEntityEvent) entity.getLastDamageCause();
            if (!(damageEvent.getDamager() instanceof LivingEntity)) {
                entity.setCustomName(null);
            }
        }
        if (entity.hasMetadata("UniqueMobStack")) {
            entity.setCustomName(null);
        }
    }


    public boolean merge(Entity a, Entity into) {
        if (canMerge(a, into)) {
            setStackCount(into, getStackCount(into) + getStackCount(a));
            if (vacuumEffect) {
                try {
                    NMS.sendPacket(a, 64, NMS.collectPacket(a.getEntityId(), into.getEntityId(), 1));
                } catch (Throwable e) {
                    e.printStackTrace();
                }
            }
            a.remove();
            ((SamplerEntities) React.sampler(SamplerEntities.ID)).getEntities().decrementAndGet();
            return true;
        }

        return false;
    }

    public boolean canMerge(Entity a, Entity into) {
        // Check if entities are stackable via config
        if (skipCustomMobs && (CustomMobChecker.isCustom(a) || CustomMobChecker.isCustom(into))) {
            return false;
        }

        // Check if entities are marked as non-stackable
        if (a.hasMetadata("DoNotStack") || into.hasMetadata("DoNotStack")) {
            return false;
        }

        // Check if entities are stackable via spawn reason
        if (onlySpawnerMobs && (!a.hasMetadata("SpawnedBySpawner") || !into.hasMetadata("SpawnedBySpawner"))) {
            return false;
        }

        // Check if entities are dead
        if (a.isDead() || into.isDead()) {
            return false;
        }

        // Check if entities are the same literal entity
        if (a.getUniqueId().equals(into.getUniqueId())) {
            return false;
        }

        // Check if entities are a player
        if (a instanceof Player || into instanceof Player) {
            return false;
        }

        // Check if entities == living entities
        if (!(a instanceof LivingEntity la)) {
            return false;
        }

        // Check if entities are the same type
        if (!a.getType().equals(into.getType())) {
            return false;
        }

        // Check if entities are Slimes or Magma Cubes and if their sizes match
        if ((a instanceof Slime || a instanceof MagmaCube) && (into instanceof Slime || into instanceof MagmaCube)) {
            if (((Slime) a).getSize() != ((Slime) into).getSize()) {
                return false;
            }
        }

        // Check if entities are ageable and if both are adults or babies
        if ((a instanceof Ageable && into instanceof Ageable)) {
            if (((Ageable) a).isAdult() != ((Ageable) into).isAdult()) {
                return false;
            }
        }

        // Check if entities are Sheep and if their color matches
        if ((a instanceof Sheep && into instanceof Sheep)) {
            if (((Sheep) a).getColor() != ((Sheep) into).getColor()) {
                return false;
            }
        }

        // Check if entities are Villagers and if their professions match
        if ((a instanceof Villager && into instanceof Villager)) {
            if (((Villager) a).getProfession() != ((Villager) into).getProfession()) {
                return false;
            }
        }

        // types that can stack
        if (!stackableTypes.contains(a.getType())) {
            return false;
        }

        // Check stack count
        if (getStackCount(into) + getStackCount(a) > maxStackSize) {
            return false;
        }

        // Check health
        if (into instanceof LivingEntity li) {
            return !(la.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue() + li.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue() > maxHealth);
        }

        return true;
    }


    public int getTheoreticalMaxStackCount(Entity entityAsType) {
        if (entityAsType instanceof LivingEntity le) {
            return Math.min((int) Math.ceil(Math.floor(maxHealth / le.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue())), maxStackSize);
        }

        return maxStackSize;
    }

    public void setStackCount(Entity e, int i) {
        ReactEntity.setStackCount(e, i);

        if (customNames) {
            if (i > 1) {
                e.setCustomName(ChatColor.BOLD + "" + getStackCount(e) + "x " + ChatColor.RESET + ChatColor.GRAY + "" + Form.capitalizeWords(e.getType().name().toLowerCase().replaceAll("\\Q_\\E", " ")));
            } else {
                e.setCustomName(null);
            }
        }
    }

    public int getStackCount(Entity e) {
        return ReactEntity.getStackCount(e);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void on(EntitySpawnEvent e) {
        if (stackableTypes.contains(e.getEntityType())) {
            J.s(() -> onTick(e.getEntity()));
        }
    }

    @EventHandler
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.SPAWNER) {
            event.getEntity().setMetadata("SpawnedBySpawner", new FixedMetadataValue(React.instance, true));
        }
    }

    public void onTick(Entity entity) {
        J.a(() -> J.s(() -> {
            for (Entity i : entity.getNearbyEntities(searchRadius, searchRadius, searchRadius)) {
                if (merge(entity, i)) {
                    break;
                }
            }
        }));
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
