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

package art.arcane.react.content.feature;

import art.arcane.react.api.feature.ReactFeature;
import art.arcane.react.platform.GameLocation;
import art.arcane.react.platform.ListenerPriority;
import art.arcane.react.platform.SpawnHost;
import art.arcane.react.platform.ReactPlatforms;
import art.arcane.react.platform.Subscription;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@art.arcane.react.util.project.config.ConfigDescription("Configuration for Spawn Burst Limiter feature. This feature continuously monitors server behavior and applies guardrails during runtime.")
public class FeatureSpawnBurstLimiter extends ReactFeature {
  public static final String ID = "spawn-burst-limiter";
  @art.arcane.react.util.project.config.ConfigDoc(value = "Main evaluation interval for spawn burst limiter in milliseconds.", impact = "Lower values react faster but consume more CPU; higher values reduce overhead but react later.")
  private int tickIntervalMS = 5000;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Rolling enforcement window length used by spawn burst limiter (milliseconds).", impact = "Longer windows smooth bursts but react slower; shorter windows react faster but are more sensitive.")
  private int windowMS = 1200;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Maximum spawns allowed per chunk window in spawn burst limiter.", impact = "Higher values permit larger bursts before control engages; lower values clamp spikes sooner.")
  private int maxSpawnsPerChunkWindow = 22;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Maximum spawner spawns allowed per chunk window in spawn burst limiter.", impact = "Higher values permit larger bursts before control engages; lower values clamp spikes sooner.")
  private int maxSpawnerSpawnsPerChunkWindow = 10;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Maximum monster spawns allowed per chunk window in spawn burst limiter.", impact = "Higher values permit larger bursts before control engages; lower values clamp spikes sooner.")
  private int maxMonsterSpawnsPerChunkWindow = 15;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Controls whether spawn burst limiter applies enforce natural spawns.", impact = "Enable to apply this behavior; disable to keep this path inactive.")
  private boolean enforceNaturalSpawns = true;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Controls whether spawn burst limiter applies enforce spawner spawns.", impact = "Enable to apply this behavior; disable to keep this path inactive.")
  private boolean enforceSpawnerSpawns = true;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Controls whether spawn burst limiter applies enforce monster spawns.", impact = "Enable to apply this behavior; disable to keep this path inactive.")
  private boolean enforceMonsterSpawns = true;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Skips named entities when spawn burst limiter evaluates targets.", impact = "Enable this to exclude matching cases; disable it to include them in enforcement.")
  private boolean ignoreNamedEntities = true;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Pushes back the next spawn attempt of spawners whose spawns get burst-limited.", impact = "Enable to stop limited spawners from wastefully re-attempting every cycle; disable to only cancel the spawn itself.")
  private boolean spawnerBackoff = true;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Delay in ticks applied to a burst-limited spawner's next spawn attempt.", impact = "Higher values idle limited spawners longer, shedding more spawn-attempt work; lower values let grinders resume sooner.")
  private int spawnerBackoffTicks = 600;
  private transient Map<UUID, Long2ObjectOpenHashMap<SpawnWindow>> windows = new ConcurrentHashMap<>();
  private transient volatile SpawnHost spawns;
  private transient Subscription subscription = Subscription.NONE;

  public FeatureSpawnBurstLimiter() {
    super(ID);
  }

  @Override
  public void onActivate() {
    windows = new ConcurrentHashMap<>();
    spawns = ReactPlatforms.get().spawnHost().orElse(null);
    subscription = spawns == null ? Subscription.NONE : spawns.onCreatureSpawn(ListenerPriority.LOWEST, true, this::on);
  }

  @Override
  public void onDeactivate() {
    subscription.cancel();
    subscription = Subscription.NONE;
    windows.clear();
  }

  @Override
  public int getTickInterval() {
    return tickIntervalMS;
  }

  @Override
  public void onTick() {
    long now = System.currentTimeMillis();
    long expiryMS = Math.max(1000L, windowMS * 4L);
    Iterator<Map.Entry<UUID, Long2ObjectOpenHashMap<SpawnWindow>>> worldIterator = windows.entrySet().iterator();
    while (worldIterator.hasNext()) {
      Map.Entry<UUID, Long2ObjectOpenHashMap<SpawnWindow>> worldEntry = worldIterator.next();
      Long2ObjectOpenHashMap<SpawnWindow> chunkMap = worldEntry.getValue();
      synchronized (chunkMap) {
        ObjectIterator<Long2ObjectOpenHashMap.Entry<SpawnWindow>> chunkIterator = chunkMap.long2ObjectEntrySet().fastIterator();
        while (chunkIterator.hasNext()) {
          if (now - chunkIterator.next().getValue().lastHit > expiryMS) {
            chunkIterator.remove();
          }
        }
      }
    }
  }

  void on(SpawnHost.Spawn event) {
    if (!shouldEvaluate(event)) {
      return;
    }

    GameLocation location = event.location();
    if (location == null) {
      return;
    }

    long now = System.currentTimeMillis();
    UUID worldId = location.worldId();
    long chunkKey = packChunk(location.chunkX(), location.chunkZ());
    Long2ObjectOpenHashMap<SpawnWindow> chunkMap = windows.computeIfAbsent(worldId, ignored -> new Long2ObjectOpenHashMap<>());
    SpawnWindow window;
    synchronized (chunkMap) {
      window = chunkMap.get(chunkKey);
      if (window == null) {
        window = new SpawnWindow(now);
        chunkMap.put(chunkKey, window);
      }
    }
    window.rollover(windowMS, now);
    window.total++;

    boolean spawnerReason = SpawnHost.isSpawnerReason(event.reason());
    if (spawnerReason) {
      window.spawner++;
    }

    boolean monster = event.isMonster();
    if (monster) {
      window.monster++;
    }

    if (window.total > maxSpawnsPerChunkWindow) {
      event.cancel();
      if (spawnerReason) {
        backoffNearbySpawners(location, window, now);
      }
      return;
    }

    if (enforceSpawnerSpawns && window.spawner > maxSpawnerSpawnsPerChunkWindow) {
      event.cancel();
      backoffNearbySpawners(location, window, now);
      return;
    }

    if (enforceMonsterSpawns && monster && window.monster > maxMonsterSpawnsPerChunkWindow) {
      event.cancel();
    }
  }

  private void backoffNearbySpawners(GameLocation location, SpawnWindow window, long now) {
    if (!spawnerBackoff || spawnerBackoffTicks <= 0) {
      return;
    }

    if (now - window.lastBackoffMs < Math.max(250, windowMS)) {
      return;
    }
    window.lastBackoffMs = now;

    SpawnHost host = spawns;
    if (host != null) {
      host.backoffSpawners(location, spawnerBackoffTicks);
    }
  }

  private boolean shouldEvaluate(SpawnHost.Spawn event) {
    if (ignoreNamedEntities && event.hasCustomName()) {
      return false;
    }

    String reason = event.reason();
    boolean spawner = SpawnHost.isSpawnerReason(reason);
    if (spawner && enforceSpawnerSpawns) {
      return true;
    }

    if (event.isMonster() && enforceMonsterSpawns) {
      return true;
    }

    return SpawnHost.isNaturalReason(reason) && enforceNaturalSpawns;
  }

  private static final class SpawnWindow {
    @art.arcane.react.util.project.config.ConfigDoc(value = "Internal timestamp used by spawn burst limiter to track timing windows and decay.", impact = "Primarily runtime state; changing this manually can distort cooldown or throttling behavior.")
    private long start;
    @art.arcane.react.util.project.config.ConfigDoc(value = "Internal timestamp used by spawn burst limiter to track timing windows and decay.", impact = "Primarily runtime state; changing this manually can distort cooldown or throttling behavior.")
    private long lastHit;
    @art.arcane.react.util.project.config.ConfigDoc(value = "Internal counter used by spawn burst limiter while tracking burst activity.", impact = "Primarily runtime state; React updates this automatically during live evaluation.")
    private int total;
    @art.arcane.react.util.project.config.ConfigDoc(value = "Internal counter used by spawn burst limiter while tracking burst activity.", impact = "Primarily runtime state; React updates this automatically during live evaluation.")
    private int spawner;
    @art.arcane.react.util.project.config.ConfigDoc(value = "Internal counter used by spawn burst limiter while tracking burst activity.", impact = "Primarily runtime state; React updates this automatically during live evaluation.")
    private int monster;
    @art.arcane.react.util.project.config.ConfigDoc(value = "Internal timestamp used by spawn burst limiter to track timing windows and decay.", impact = "Primarily runtime state; changing this manually can distort cooldown or throttling behavior.")
    private long lastBackoffMs;

    private SpawnWindow(long now) {
      start = now;
      lastHit = now;
    }

    private void rollover(int windowMS, long now) {
      if (now - start > windowMS) {
        start = now;
        total = 0;
        spawner = 0;
        monster = 0;
      }

      lastHit = now;
    }
  }

  private static long packChunk(int cx, int cz) {
    return (((long) cx) << 32) ^ (cz & 0xFFFFFFFFL);
  }
}
