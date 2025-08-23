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

package com.volmit.react.util.world;

import com.volmit.react.React;
import com.volmit.react.content.sampler.SamplerEntities;
import com.volmit.react.core.controller.EntityController;
import com.volmit.react.util.format.C;
import com.volmit.react.util.scheduling.J;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import org.bukkit.Particle;
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
        if (React.controller(EntityController.class).getKilling().contains(e)) {
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
        if (entity.isDead()) {
            stop();
            return;
        }

        seconds--;
        if (seconds == 0) {
            kill();
            React.instance.unregisterListener(this);
        }

        entity.setCustomName(C.RED + "" + seconds + "s");
        entity.setCustomNameVisible(true);
    }

    public void kill() {
        if (entity.isDead()) {
            return;
        }

        stop();
        entity.getWorld().spawnParticle(Particle.FLASH, entity.getLocation(), 1);
        entity.getWorld().getPlayers().forEach(player -> 
                React.audiences.player(player).playSound(Sound.sound(
                        Key.key("minecraft:particle.soul_escape"),
                        Sound.Source.NEUTRAL,
                        0.5f,
                        0.9f
                ), entity.getLocation().getX(), entity.getLocation().getY(), entity.getLocation().getZ())
        );
        entity.remove();
        ((SamplerEntities) React.sampler(SamplerEntities.ID)).getEntities().decrementAndGet();
    }


    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void on(EntityPickupItemEvent e) {
        if (e.getItem().equals(entity)) {
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
