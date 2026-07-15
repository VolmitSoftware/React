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

package art.arcane.react.core.controller;

import art.arcane.chrono.ChronoLatch;
import art.arcane.react.React;
import art.arcane.react.model.ReactConfiguration;
import art.arcane.react.model.ReactEntity;
import art.arcane.react.util.common.scheduling.J;
import art.arcane.react.util.plugin.IController;
import art.arcane.react.util.project.value.MaterialValue;
import art.arcane.react.util.project.world.EntityKiller;
import art.arcane.react.util.project.world.WorldEntitySnapshots;
import art.arcane.volmlib.util.scheduling.Looper;
import lombok.Data;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.world.EntitiesLoadEvent;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

@Data
public class EntityController implements IController, Listener {
  private int perWorldUpdatesPerTick = 15;
  private transient Looper looper;
  private transient ChronoLatch valueSaver = new ChronoLatch(60000);
  private transient Map<EntityType, List<Consumer<Entity>>> entityTickListeners;
  private transient List<Consumer<Entity>> allEntityTickListeners;
  private transient Set<EntityKiller> killers = new HashSet<>();
  private transient Set<Entity> killing = new HashSet<>();

  public void registerEntityTickListener(EntityType type, Consumer<Entity> listener) {
    if (listener == null) {
      return;
    }

    entityTickListeners.computeIfAbsent(type, (t) -> new CopyOnWriteArrayList<>()).add(listener);
  }

  public void registerEntityTickListener(Consumer<Entity> listener) {
    if (listener == null) {
      return;
    }

    allEntityTickListeners.add(listener);
  }

  @Override
  public String getName() {
    return "Entity";
  }

  @Override
  public String getId() {
    return "entity";
  }

  @Override
  public void start() {
    allEntityTickListeners = new CopyOnWriteArrayList<>();
    entityTickListeners = new ConcurrentHashMap<>();
    ReactConfiguration.get().getPriority().rebuildPriority();
    looper = new Looper() {
      @Override
      protected long loop() {
        if (!React.instance.isReady()) {
          return 100;
        }

        onTick();
        return 50;
      }
    };
  }

  public void tickEntity(Entity e) {
    if (e == null) {
      return;
    }

    if (J.isFoliaThreading()) {
      if (!J.isOwnedByCurrentRegion(e)) {
        J.runEntity(e, () -> tickEntity(e));
        return;
      }
    } else if (!J.isPrimaryThread()) {
      J.s(() -> tickEntity(e));
      return;
    }

    if (!hasRegisteredTickListeners()) {
      return;
    }

    if (!ReactEntity.tick(e, ReactConfiguration.get().getPriority())) {
      return;
    }

    for (Consumer<Entity> listener : allEntityTickListeners) {
      try {
        listener.accept(e);
      } catch (Throwable ex) {
        React.reportError(ex);
      }
    }

    List<Consumer<Entity>> typeListeners = entityTickListeners.get(e.getType());
    if (typeListeners == null || typeListeners.isEmpty()) {
      return;
    }

    for (Consumer<Entity> listener : typeListeners) {
      try {
        listener.accept(e);
      } catch (Throwable ex) {
        React.reportError(ex);
      }
    }
  }

  @EventHandler
  public void on(EntitySpawnEvent e) {
    tickEntity(e.getEntity());
  }

  @EventHandler
  public void on(EntityDamageEvent e) {
    tickEntity(e.getEntity());
  }

  @EventHandler
  public void on(EntityTargetEvent e) {
    tickEntity(e.getEntity());

    if (e.getTarget() != null) {
      tickEntity(e.getTarget());
    }
  }

  @EventHandler
  public void on(EntityInteractEvent e) {
    tickEntity(e.getEntity());
  }

  @EventHandler
  public void on(PlayerInteractAtEntityEvent e) {
    tickEntity(e.getRightClicked());
  }

  @EventHandler
  public void on(EntityPoseChangeEvent e) {
    tickEntity(e.getEntity());
  }

  @EventHandler
  public void on(EntityRegainHealthEvent e) {
    tickEntity(e.getEntity());
  }

  @EventHandler
  public void on(EntityTameEvent e) {
    tickEntity(e.getEntity());
  }

  @EventHandler
  public void on(EntityPlaceEvent e) {
    tickEntity(e.getEntity());
  }

  @EventHandler
  public void on(EntityDropItemEvent e) {
    tickEntity(e.getEntity());
  }

  @EventHandler
  public void on(PlayerDropItemEvent e) {
    tickEntity(e.getItemDrop());
  }

  @EventHandler
  public void on(ItemMergeEvent e) {
    tickEntity(e.getTarget());
  }

  @EventHandler
  public void on(EntityBreedEvent e) {
    tickEntity(e.getMother());
    tickEntity(e.getFather());
  }

  @EventHandler
  public void on(EntitiesLoadEvent e) {
    for (Entity entity : e.getEntities()) {
      EntityKiller.reconcile(entity);
    }
  }

  @Override
  public void stop() {
    looper.interrupt();

    for (EntityKiller i : new HashSet<>(killers)) {
      i.stop();
    }

    killers.clear();
    killing.clear();
  }

  @Override
  public void postStart() {
    looper.start();
  }

  public void onTick() {
    if (valueSaver.flip() && ReactConfiguration.get().getPriority().isUseItemStackValueSystem()) {
      MaterialValue.save();
    }

    if (!hasRegisteredTickListeners()) {
      return;
    }

    if (J.isFoliaThreading()) {
      onFoliaTick();
      return;
    }

    J.s(() -> {
      ThreadLocalRandom random = ThreadLocalRandom.current();

      for (World world : Bukkit.getWorlds()) {
        List<Entity> entities = WorldEntitySnapshots.get(world);
        int entityCount = entities.size();

        if (entityCount < 3) {
          continue;
        }

        int sampleCount = Math.min(perWorldUpdatesPerTick, entityCount);

        for (int j = 0; j < sampleCount; j++) {
          tickEntity(entities.get(random.nextInt(entityCount)));
        }
      }
    });
  }

  private void onFoliaTick() {
    List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
    if (players.isEmpty()) {
      return;
    }

    ThreadLocalRandom random = ThreadLocalRandom.current();
    int samples = Math.min(players.size(), Math.max(1, perWorldUpdatesPerTick));

    for (int i = 0; i < samples; i++) {
      Player player = players.get(random.nextInt(players.size()));
      J.runEntity(player, () -> sampleAroundPlayer(player));
    }
  }

  private void sampleAroundPlayer(Player player) {
    if (player == null || !player.isOnline() || !J.isOwnedByCurrentRegion(player)) {
      return;
    }

    List<Entity> nearby = player.getNearbyEntities(48, 32, 48);
    if (nearby == null || nearby.isEmpty()) {
      return;
    }

    ThreadLocalRandom random = ThreadLocalRandom.current();
    int samples = Math.min(Math.max(1, perWorldUpdatesPerTick / 2), nearby.size());
    for (int i = 0; i < samples; i++) {
      Entity sampled = nearby.get(random.nextInt(nearby.size()));
      if (sampled != null) {
        tickEntity(sampled);
      }
    }
  }

  private boolean hasRegisteredTickListeners() {
    if (!allEntityTickListeners.isEmpty()) {
      return true;
    }

    for (List<Consumer<Entity>> listeners : entityTickListeners.values()) {
      if (listeners != null && !listeners.isEmpty()) {
        return true;
      }
    }

    return false;
  }
}
