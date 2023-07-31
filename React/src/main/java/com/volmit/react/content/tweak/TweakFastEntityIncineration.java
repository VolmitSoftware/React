package com.volmit.react.content.tweak;

import com.volmit.react.React;
import com.volmit.react.api.tweak.ReactTweak;
import com.volmit.react.util.scheduling.J;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Monster;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;

public class TweakFastEntityIncineration extends ReactTweak implements Listener {
    public static final String ID = "fast-entity-incineration";
    private double incinerationBeyondNearestPlayer = 32;

    public TweakFastEntityIncineration() {
        super(ID);
    }

    @Override
    public void onActivate() {

    }

    @EventHandler
    public void on(EntityDamageEvent e) {
        if (e.getCause().equals(EntityDamageEvent.DamageCause.FIRE_TICK) && e.getEntity() instanceof Monster m && React.hasNearbyPlayer(m.getLocation(), incinerationBeyondNearestPlayer)) {
            kill(m);
        }
    }

    private void kill(Entity entity) {
        J.s(() -> React.kill(entity, 3), (int) (20 * Math.random()));
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
