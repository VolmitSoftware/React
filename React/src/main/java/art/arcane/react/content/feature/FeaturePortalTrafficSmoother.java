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

import art.arcane.react.React;
import art.arcane.react.api.feature.ReactFeature;
import art.arcane.react.content.sampler.SamplerIncidentScore;
import art.arcane.react.content.sampler.SamplerTickTime;
import art.arcane.react.util.common.scheduling.J;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@art.arcane.react.util.project.config.ConfigDescription("Configuration for Portal Traffic Smoother feature. This feature continuously monitors server behavior and applies guardrails during runtime.")
public class FeaturePortalTrafficSmoother extends ReactFeature implements Listener {
  public static final String ID = "portal-traffic-smoother";
  @art.arcane.react.util.project.config.ConfigDoc(value = "Main evaluation interval for portal traffic smoother in milliseconds.", impact = "Lower values react faster but consume more CPU; higher values reduce overhead but react later.")
  private int tickIntervalMS = 2000;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Rolling enforcement window length used by portal traffic smoother (milliseconds).", impact = "Longer windows smooth bursts but react slower; shorter windows react faster but are more sensitive.")
  private int windowMS = 1000;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Maximum player portals allowed per chunk window in portal traffic smoother.", impact = "Higher values permit larger bursts before control engages; lower values clamp spikes sooner.")
  private int maxPlayerPortalsPerChunkWindow = 6;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Maximum entity portals allowed per chunk window in portal traffic smoother.", impact = "Higher values permit larger bursts before control engages; lower values clamp spikes sooner.")
  private int maxEntityPortalsPerChunkWindow = 16;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Cool-off period after throttling decisions in portal traffic smoother (milliseconds).", impact = "Higher values keep throttles active longer; lower values let activity recover sooner.")
  private int cooloffMS = 5000;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Player delay duration used by portal traffic smoother (ticks).", impact = "Higher values keep state active longer; lower values expire or apply changes sooner.")
  private int playerDelayTicks = 2;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Entity delay duration used by portal traffic smoother (ticks).", impact = "Higher values keep state active longer; lower values expire or apply changes sooner.")
  private int entityDelayTicks = 4;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Maximum queued delays allowed by portal traffic smoother.", impact = "Higher values allow more throughput before intervention; lower values make mitigation more aggressive.")
  private int maxQueuedDelays = 512;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Controls whether portal traffic smoother applies only during pressure.", impact = "Enable to apply this behavior; disable to keep this path inactive.")
  private boolean onlyDuringPressure = true;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Trigger threshold for pressure incident score in portal traffic smoother.", impact = "Higher values trigger mitigation later; lower values trigger earlier and more aggressively.")
  private double pressureIncidentScore = 40;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Tick-time threshold for pressure in portal traffic smoother (milliseconds).", impact = "Higher values delay activation or exit; lower values make this threshold easier to cross.")
  private double pressureTickMS = 52;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Bypasses portal traffic smoother handling for bypass near players.", impact = "Enable this to skip enforcement in matching situations; disable it for strict handling.")
  private boolean bypassNearPlayers = true;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Bypass player radius used by portal traffic smoother (blocks).", impact = "Higher values widen the search area and cost more work; lower values narrow scope and run cheaper.")
  private double bypassPlayerRadius = 10;
  private transient Map<ChunkKey, PortalWindow> windows = new ConcurrentHashMap<>();
  private transient Map<UUID, Long> delayed = new ConcurrentHashMap<>();

  public FeaturePortalTrafficSmoother() {
    super(ID);
  }

  @Override
  public void onActivate() {
    windows = new ConcurrentHashMap<>();
    delayed = new ConcurrentHashMap<>();
  }

  @Override
  public void onDeactivate() {
    windows.clear();
    delayed.clear();
  }

  @Override
  public int getTickInterval() {
    return tickIntervalMS;
  }

  @Override
  public void onTick() {
    long now = System.currentTimeMillis();
    long windowExpiry = Math.max(windowMS * 5L, cooloffMS * 2L);

    for (Map.Entry<ChunkKey, PortalWindow> entry : windows.entrySet()) {
      PortalWindow window = entry.getValue();
      if (now - window.lastHit > windowExpiry && now >= window.throttleUntil) {
        windows.remove(entry.getKey(), window);
      }
    }

    for (Map.Entry<UUID, Long> entry : delayed.entrySet()) {
      if (entry.getValue() <= now) {
        delayed.remove(entry.getKey(), entry.getValue());
      }
    }
  }

  @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
  public void on(PlayerPortalEvent event) {
    Location destination = event.getTo();
    if (destination == null) {
      return;
    }

    if (!shouldManage(destination)) {
      return;
    }

    if (!isThrottled(destination, true)) {
      return;
    }

    Player player = event.getPlayer();
    if (delayed.size() >= maxQueuedDelays) {
      event.setCancelled(true);
      return;
    }

    long now = System.currentTimeMillis();
    UUID id = player.getUniqueId();
    if (now < delayed.getOrDefault(id, 0L)) {
      event.setCancelled(true);
      return;
    }

    event.setCancelled(true);
    delayed.put(id, now + 10000L);
    Location to = destination.clone();
    int delay = Math.max(1, playerDelayTicks);
    if (!J.runEntity(player, () -> {
      if (player.isOnline() && !player.isDead()) {
        player.teleport(to, PlayerTeleportEvent.TeleportCause.PLUGIN);
      }
      delayed.remove(id);
    }, delay)) {
      delayed.remove(id);
    }
  }

  @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
  public void on(EntityPortalEvent event) {
    if (event.getEntity() instanceof Player) {
      return;
    }

    Location destination = event.getTo();
    if (destination == null) {
      return;
    }

    if (!shouldManage(destination)) {
      return;
    }

    if (!isThrottled(destination, false)) {
      return;
    }

    if (delayed.size() >= maxQueuedDelays) {
      event.setCancelled(true);
      return;
    }

    Entity entity = event.getEntity();
    UUID id = entity.getUniqueId();
    long now = System.currentTimeMillis();
    if (now < delayed.getOrDefault(id, 0L)) {
      event.setCancelled(true);
      return;
    }

    event.setCancelled(true);
    delayed.put(id, now + 10000L);
    Location to = destination.clone();
    int delay = Math.max(1, entityDelayTicks);
    if (!J.runEntity(entity, () -> {
      if (entity.isValid() && !entity.isDead()) {
        entity.teleport(to);
      }
      delayed.remove(id);
    }, delay)) {
      delayed.remove(id);
    }
  }

  private boolean shouldManage(Location location) {
    if (bypassNearPlayers && React.hasNearbyPlayer(location, bypassPlayerRadius)) {
      return false;
    }

    if (!onlyDuringPressure) {
      return true;
    }

    return sample(SamplerTickTime.ID) >= pressureTickMS
        || sample(SamplerIncidentScore.ID) >= pressureIncidentScore;
  }

  private boolean isThrottled(Location location, boolean player) {
    if (location.getWorld() == null) {
      return false;
    }

    long now = System.currentTimeMillis();
    ChunkKey key = new ChunkKey(location.getWorld().getUID(), location.getBlockX() >> 4, location.getBlockZ() >> 4);
    PortalWindow window = windows.computeIfAbsent(key, k -> new PortalWindow(now));
    window.rollover(windowMS, now);

    if (player) {
      window.players++;
    } else {
      window.entities++;
    }

    int limit = player ? maxPlayerPortalsPerChunkWindow : maxEntityPortalsPerChunkWindow;
    int count = player ? window.players : window.entities;

    if (count > limit) {
      window.throttleUntil = Math.max(window.throttleUntil, now + cooloffMS);
    }

    return now < window.throttleUntil;
  }

  private double sample(String id) {
    var sampler = React.sampler(id);
    return sampler == null ? 0D : sampler.sample();
  }

  private static final class PortalWindow {
    @art.arcane.react.util.project.config.ConfigDoc(value = "Internal timestamp used by portal traffic smoother to track timing windows and decay.", impact = "Primarily runtime state; changing this manually can distort cooldown or throttling behavior.")
    private long start;
    @art.arcane.react.util.project.config.ConfigDoc(value = "Internal timestamp used by portal traffic smoother to track timing windows and decay.", impact = "Primarily runtime state; changing this manually can distort cooldown or throttling behavior.")
    private long lastHit;
    @art.arcane.react.util.project.config.ConfigDoc(value = "Internal timestamp used by portal traffic smoother to track timing windows and decay.", impact = "Primarily runtime state; changing this manually can distort cooldown or throttling behavior.")
    private long throttleUntil;
    @art.arcane.react.util.project.config.ConfigDoc(value = "Internal counter used by portal traffic smoother while tracking burst activity.", impact = "Primarily runtime state; React updates this automatically during live evaluation.")
    private int players;
    @art.arcane.react.util.project.config.ConfigDoc(value = "Internal counter used by portal traffic smoother while tracking burst activity.", impact = "Primarily runtime state; React updates this automatically during live evaluation.")
    private int entities;

    private PortalWindow(long now) {
      start = now;
      lastHit = now;
      throttleUntil = 0;
    }

    private void rollover(int windowMS, long now) {
      if (now - start > windowMS) {
        start = now;
        players = 0;
        entities = 0;
      }

      lastHit = now;
    }
  }

  private static final class ChunkKey {
    @art.arcane.react.util.project.config.ConfigDoc(value = "World identifier used by portal traffic smoother internal tracking.", impact = "This is runtime identity data and should normally be left to automatic updates.")
    private final UUID world;
    @art.arcane.react.util.project.config.ConfigDoc(value = "X-axis coordinate used by portal traffic smoother internal tracking.", impact = "This is internal state data and should not normally be changed manually.")
    private final int x;
    @art.arcane.react.util.project.config.ConfigDoc(value = "Z-axis coordinate used by portal traffic smoother internal tracking.", impact = "This is internal state data and should not normally be changed manually.")
    private final int z;

    private ChunkKey(UUID world, int x, int z) {
      this.world = world;
      this.x = x;
      this.z = z;
    }

    @Override
    public boolean equals(Object object) {
      if (this == object) {
        return true;
      }

      if (!(object instanceof ChunkKey key)) {
        return false;
      }

      return x == key.x && z == key.z && world.equals(key.world);
    }

    @Override
    public int hashCode() {
      int result = world.hashCode();
      result = 31 * result + x;
      result = 31 * result + z;
      return result;
    }
  }
}
