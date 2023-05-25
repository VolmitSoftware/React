package com.volmit.react.util.world;

import com.volmit.react.React;
import com.volmit.react.content.sampler.SamplerEntities;
import com.volmit.react.core.controller.EntityController;
import com.volmit.react.util.format.C;
import com.volmit.react.util.scheduling.J;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;

public class EntityKiller implements Listener {
    private Entity entity;
    private int seconds;
    private int tt;

    public EntityKiller(Entity e, int seconds) {
        if(React.controller(EntityController.class).getKilling().contains(e)) {
            return;
        }

        React.controller(EntityController.class).getKilling().add(e);
        React.controller(EntityController.class).getKillers().add(this);
        React.instance.registerListener(this);
        this.entity = e;
        this.seconds = seconds;
        this.tt = seconds;
       tt = J.sr(this::tick, 20);
    }

    public void stop() {
        entity.setCustomNameVisible(false);
        entity.setCustomName(null);
        React.instance.unregisterListener(this);
        React.controller(EntityController.class).getKilling().remove(entity);
        React.controller(EntityController.class).getKillers().remove(this);
        J.csr(tt);
    }

    private void tick() {
        if(entity.isDead()) {
            stop();
            return;
        }

        seconds--;
        if(seconds == 0) {
            kill();
            React.instance.unregisterListener(this);
        }

        entity.setCustomName(C.RED + "" + seconds + "s");
        entity.setCustomNameVisible(true);
    }

    public void kill() {
        if(entity.isDead()) {
            return;
        }

        stop();
        entity.getWorld().spawnParticle(Particle.FLASH, entity.getLocation(), 1);
        entity.getWorld().playSound(entity.getLocation(), Sound.PARTICLE_SOUL_ESCAPE, 0.5f, 0.9f);
        entity.remove();
        ((SamplerEntities)React.sampler(SamplerEntities.ID)).getEntities().decrementAndGet();
    }


    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void on(EntityPickupItemEvent e) {
        if(e.getItem().equals(entity)) {
            stop();
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void on(PlayerInteractEntityEvent event) {
        Entity clickedEntity = event.getRightClicked();
        if (!entity.equals(clickedEntity)) {
            return;
        }
        React.verbose("EntityKiller: " + entity.getCustomName() + " was cancelled by player " + event.getPlayer().getName());
        stop();
    }
}
