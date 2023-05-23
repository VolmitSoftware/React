package com.volmit.react.util.world;

import com.volmit.react.React;
import com.volmit.react.content.sampler.SamplerEntities;
import com.volmit.react.util.format.C;
import com.volmit.react.util.scheduling.J;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class EntityKiller implements Listener {
    private static Set<Entity> killing = new HashSet<>();
    private static Map<Entity, String> taggedEntities = new HashMap<>();

    private Entity entity;
    private int seconds;
    private int tt;

    public EntityKiller(Entity e, int seconds) {
        if(killing.contains(e)) {
            return;
        }
        React.instance.registerListener(this);

        this.entity = e;
        this.seconds = seconds;
        this.tt = seconds;
        J.sr(this::tick, 20 , seconds);
        killing.add(entity);
    }

    private void tick() {
        if(entity.isDead()) {
            killing.remove(entity);
            return;
        }

        seconds--;
        if(seconds == 0) {
            tag();
            kill();
            React.instance.unregisterListener(this);
        }

        entity.setCustomName(C.RED + "" + seconds + "s");
        entity.setCustomNameVisible(true);
    }

    public void tag() {
        taggedEntities.put(entity, entity.getCustomName());
    }

    public void untag(Entity e) {
        if(taggedEntities.containsKey(e)) {
            e.setCustomName(taggedEntities.get(e));
            taggedEntities.remove(e);
        }
    }

    public void kill() {
        killing.remove(entity);
        if(entity.isDead()) {
            return;
        }

        entity.getWorld().spawnParticle(Particle.FLASH, entity.getLocation(), 1);
        entity.getWorld().playSound(entity.getLocation(), Sound.PARTICLE_SOUL_ESCAPE, 0.5f, 0.9f);
        entity.remove();
        ((SamplerEntities)React.sampler(SamplerEntities.ID)).getEntities().decrementAndGet();
    }

    @EventHandler
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        Entity clickedEntity = event.getRightClicked();
        if (entity != clickedEntity) {
            return;
        }
        // untag and remove from kill list
        React.info("EntityKiller: " + entity.getCustomName() + " was cancelled by player " + event.getPlayer().getName());
        untag(entity);
        killing.remove(entity);
        taggedEntities.remove(entity);
    }
}
