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
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntitySpawnEvent;

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
            LivingEntity next = (LivingEntity) l.getWorld().spawnEntity(l.getLocation(), l.getType());
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
        if (skipCustomMobs && (CustomMobChecker.isCustom(a) || CustomMobChecker.isCustom(into))) {
            return false;
        }

        if (a.isDead() || into.isDead()) {
            return false;
        }

        if (a.getUniqueId().equals(into.getUniqueId())) {
            return false;
        }

        if (a instanceof Player) {
            return false;
        }

        if (into instanceof Player) {
            return false;
        }

        if (!(a instanceof LivingEntity la)) {
            return false;
        }

        if (!a.getType().equals(into.getType())) {
            return false;
        }

        if (!stackableTypes.contains(a.getType())) {
            return false;
        }

        if (getStackCount(into) + getStackCount(a) > maxStackSize) {
            return false;
        }

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

    public void onTick(Entity entity) {
        for (Entity i : entity.getNearbyEntities(searchRadius, searchRadius, searchRadius)) {
            if (merge(entity, i)) {
                break;
            }
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
