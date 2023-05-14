package com.volmit.react.util;

import com.volmit.react.React;
import com.volmit.react.content.sampler.SamplerEntities;
import com.volmit.react.util.format.C;
import com.volmit.react.util.scheduling.J;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;

public class EntityKiller {
    private Entity entity;
    private int seconds;
    private int tt;

    public EntityKiller(Entity e, int seconds) {
        this.entity = e;
        this.seconds = seconds;
        this.tt = seconds;
        J.sr(this::tick, 20, seconds);
    }

    private void tick() {
        if(entity.isDead())
        {
            return;
        }

        seconds--;
        if(seconds == 0) {
            kill();
        }

        entity.setCustomName(C.RED + "" + seconds + "s");
        entity.setCustomNameVisible(true);
    }

    public void kill() {
        if(entity.isDead())
        {
            return;
        }

        entity.getWorld().spawnParticle(Particle.FLASH, entity.getLocation(), 1);
        entity.getWorld().playSound(entity.getLocation(), Sound.PARTICLE_SOUL_ESCAPE, 0.5f, 0.9f);
        entity.remove();
        ((SamplerEntities)React.instance.getSampleController().getSampler(SamplerEntities.ID)).getEntities().decrementAndGet();
    }
}
