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

package art.arcane.react.util.project.world;

import art.arcane.react.React;
import art.arcane.react.content.sampler.SamplerEntities;
import art.arcane.react.core.controller.EntityController;
import art.arcane.react.util.common.scheduling.J;
import art.arcane.react.util.format.C;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import org.bukkit.Color;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.Objects;

public class EntityKiller implements Listener {
  private static final NamespacedKey nsKillerCountdown = Objects.requireNonNull(NamespacedKey.fromString("react:react-killer-countdown"));
  private Entity entity;
  private int seconds;
  private int tt;
  private boolean stamped;

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
    Entity target = entity;
    if (target == null) {
      cleanup();
      return;
    }

    if (J.runEntity(target, () -> {
      target.setCustomNameVisible(false);
      target.setCustomName(null);
      target.getPersistentDataContainer().remove(nsKillerCountdown);
      cleanup();
    }, 0, this::cleanup)) {
      return;
    }

    cleanup();
  }

  public static void reconcile(Entity entity) {
    PersistentDataContainer container = entity.getPersistentDataContainer();
    if (!container.has(nsKillerCountdown, PersistentDataType.BYTE)) {
      return;
    }

    container.remove(nsKillerCountdown);
    entity.setCustomNameVisible(false);
    entity.setCustomName(null);
  }

  private void cleanup() {
    if (React.instance != null) {
      React.instance.unregisterListener(this);
    }

    EntityController controller = null;
    try {
      controller = React.controller(EntityController.class);
    } catch (Throwable ex) {
      if (React.instance != null && React.instance.isEnabled()) {
        React.warn("EntityKiller cleanup failed to resolve EntityController: "
            + ex.getClass().getSimpleName()
            + (ex.getMessage() == null ? "" : " - " + ex.getMessage()));
        React.reportError(ex);
      }
    }

    if (controller != null && entity != null) {
      controller.getKilling().remove(entity);
    }

    if (controller != null) {
      controller.getKillers().remove(this);
    }

    if (tt != 0) {
      J.csr(tt);
      tt = 0;
    }
  }

  private void tick() {
    Entity target = entity;
    if (target == null) {
      stop();
      return;
    }

    if (!J.runEntity(target, this::tickOwned, 0, this::cleanup)) {
      stop();
    }
  }

  private void tickOwned() {
    if (entity.isDead()) {
      stop();
      return;
    }

    seconds--;
    if (seconds <= 0) {
      kill();
      return;
    }

    stampCountdown();
    entity.setCustomName(C.RED + "" + seconds + "s");
    entity.setCustomNameVisible(true);
  }

  private void stampCountdown() {
    if (stamped) {
      return;
    }

    stamped = true;
    byte hadCustomName = (byte) (entity.getCustomName() != null ? 1 : 0);
    entity.getPersistentDataContainer().set(nsKillerCountdown, PersistentDataType.BYTE, hadCustomName);
  }

  public void kill() {
    Entity target = entity;
    if (target == null) {
      stop();
      return;
    }

    if (!J.runEntity(target, this::killOwned, 0, this::cleanup)) {
      stop();
    }
  }

  private void killOwned() {
    if (entity.isDead()) {
      return;
    }

    stop();
    entity.getWorld().spawnParticle(Particle.FLASH, entity.getLocation(), 1, Color.WHITE);
    entity.getWorld().getPlayers().forEach(player ->
        player.playSound(Sound.sound(
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
    if (entity != null && e.getItem().equals(entity)) {
      stop();
    }
  }

  @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
  public void on(PlayerInteractEntityEvent event) {
    if (entity == null) {
      return;
    }

    Entity clickedEntity = event.getRightClicked();
    if (!entity.equals(clickedEntity)) {
      return;
    }
    React.verbose("EntityKiller: countdown cancelled by player " + event.getPlayer().getName());
    stop();
  }
}
