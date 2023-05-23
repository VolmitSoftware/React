package com.volmit.react.content.tweak;

import com.volmit.react.api.tweak.ReactTweak;
import com.volmit.react.util.scheduling.J;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;

public class TweakCombat extends ReactTweak implements Listener {
    public static final String ID = "tweak-combat";
    private double globalDamageMultiplier = 1.0;
    private Map<EntityType, Double> perEntityDamageMultiplier = defaultMultipliers();
    private double globalKnockbackMultiplier = 1.0;
    private Map<EntityType, Double> perEntityKnockbackMultiplier = defaultMultipliers();

    public TweakCombat() {
        super(ID);
    }

    @Override
    public void onActivate() {

    }

    /**
     * This is the main method that handles damage and knockback.
     * Sets the damage to the damage, and the Knockback to the knockback.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void on(EntityDamageEvent e) {
        e.setDamage(e.getDamage() * globalDamageMultiplier * perEntityDamageMultiplier.getOrDefault(e.getEntityType(), 1.0));
        double mod = globalKnockbackMultiplier * perEntityKnockbackMultiplier.getOrDefault(e.getEntityType(), 1.0);
        if (mod != 1.0) {
            if (mod > 1.0) {
                J.ss(() -> e.getEntity().setVelocity(multiplyXZ(e.getEntity().getVelocity(), mod)), 0);
            } else {
                J.ss(() -> e.getEntity().setVelocity(e.getEntity().getVelocity().multiply(mod)), 0);
            }
        }
    }

    private Vector multiplyXZ(Vector v, double m) {
        v.setX(v.getX() * m);
        v.setZ(v.getZ() * m);
        return v;
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

    public static Map<EntityType, Double> defaultMultipliers() {
        Map<EntityType, Double> map = new HashMap<>();

        for (EntityType i : EntityType.values()) {
            if (i.isAlive()) {
                map.put(i, 1.0);
            }
        }

        return map;
    }
}
