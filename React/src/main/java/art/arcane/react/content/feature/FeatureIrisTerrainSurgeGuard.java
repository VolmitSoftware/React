package art.arcane.react.content.feature;

import art.arcane.react.React;
import art.arcane.react.api.feature.ReactCapabilityFeature;
import art.arcane.react.content.sampler.SamplerTickTime;
import art.arcane.react.core.controller.IntegrationController;
import art.arcane.volmlib.integration.IntegrationMetricGroup;
import art.arcane.volmlib.integration.IntegrationMetricSample;
import art.arcane.volmlib.integration.IntegrationMetricSchema;
import art.arcane.volmlib.util.bukkit.WorldIdentity;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@art.arcane.react.util.project.config.ConfigDescription("Configuration for Iris Terrain Surge Guard feature. This feature continuously monitors server behavior and applies guardrails during runtime.")
public class FeatureIrisTerrainSurgeGuard extends ReactCapabilityFeature implements Listener {
  public static final String ID = "feature-iris-terrain-surge-guard";

  @art.arcane.react.util.project.config.ConfigDoc(value = "Main evaluation interval for iris terrain surge guard in milliseconds.", impact = "Lower values react faster but consume more CPU; higher values reduce overhead but react later.")
  private int tickIntervalMS = 1000;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Tick-time threshold for trigger in iris terrain surge guard (milliseconds).", impact = "Higher values delay activation or exit; lower values make this threshold easier to cross.")
  private double triggerTickMS = 56D;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Trigger threshold for trigger iris pregen queue in iris terrain surge guard.", impact = "Higher values trigger mitigation later; lower values trigger earlier and more aggressively.")
  private double triggerIrisPregenQueue = 280D;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Trigger threshold for Iris generation time in iris terrain surge guard.", impact = "Higher values trigger mitigation later; lower values trigger earlier and more aggressively.")
  private double triggerIrisGenerationMS = 24D;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Rolling enforcement window length used by iris terrain surge guard (milliseconds).", impact = "Longer windows smooth bursts but react slower; shorter windows react faster but are more sensitive.")
  private int windowMS = 2500;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Maximum ungenerated chunk moves allowed per window in iris terrain surge guard.", impact = "Higher values permit larger bursts before control engages; lower values clamp spikes sooner.")
  private int maxUngeneratedChunkMovesPerWindow = 10;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Maximum ungenerated chunk teleports allowed per window in iris terrain surge guard.", impact = "Higher values permit larger bursts before control engages; lower values clamp spikes sooner.")
  private int maxUngeneratedChunkTeleportsPerWindow = 4;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Cancels cancel chunk gen spawns while iris terrain surge guard is active.", impact = "Enable to hard-block this path during mitigation; disable to observe without blocking.")
  private boolean cancelChunkGenSpawns = true;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Message rate-limit cooldown for iris terrain surge guard notifications (milliseconds).", impact = "Higher values reduce repeated chat spam; lower values allow status messages more often.")
  private long messageCooldownMS = 2500L;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Permission node string checked before iris terrain surge guard enforcement.", impact = "Change this to match your permission model for bypass behavior.")
  private String bypassPermission = "react.secret.iris.bypass";

  private transient long windowStartMS;
  private transient int moveAttempts;
  private transient int teleportAttempts;
  private transient Map<UUID, Long> lastMessageByPlayer = new ConcurrentHashMap<>();

  public FeatureIrisTerrainSurgeGuard() {
    super(ID);
  }

  @Override
  public Set<String> requiredCapabilities() {
    return Set.of("iris");
  }

  @Override
  public boolean isSecretBundle() {
    return true;
  }

  @Override
  public void onActivate() {
    windowStartMS = System.currentTimeMillis();
    moveAttempts = 0;
    teleportAttempts = 0;
    lastMessageByPlayer = new ConcurrentHashMap<>();
  }

  @Override
  public void onDeactivate() {
    lastMessageByPlayer.clear();
  }

  @Override
  public int getTickInterval() {
    return tickIntervalMS;
  }

  @Override
  public void onTick() {
    rolloverWindow(System.currentTimeMillis());
  }

  @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
  public void on(PlayerMoveEvent event) {
    if (event.getTo() == null || !isSurging(event.getTo().getWorld())) {
      return;
    }

    if (sameChunk(event.getFrom(), event.getTo())) {
      return;
    }

    Player player = event.getPlayer();
    if (bypass(player) || isChunkGenerated(event.getTo())) {
      return;
    }

    long now = System.currentTimeMillis();
    rolloverWindow(now);
    moveAttempts++;
    if (moveAttempts <= maxUngeneratedChunkMovesPerWindow) {
      return;
    }

    event.setTo(event.getFrom());
    notifyPlayer(player, "Iris terrain surge guard throttled new chunk movement.", now);
  }

  @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
  public void on(PlayerTeleportEvent event) {
    if (event.getTo() == null || !isSurging(event.getTo().getWorld())) {
      return;
    }

    Player player = event.getPlayer();
    if (bypass(player) || isChunkGenerated(event.getTo())) {
      return;
    }

    long now = System.currentTimeMillis();
    rolloverWindow(now);
    teleportAttempts++;
    if (teleportAttempts <= maxUngeneratedChunkTeleportsPerWindow) {
      return;
    }

    event.setCancelled(true);
    notifyPlayer(player, "Iris terrain surge guard throttled teleport into ungenerated terrain.", now);
  }

  @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
  public void on(CreatureSpawnEvent event) {
    if (!cancelChunkGenSpawns || !isSurging(event.getLocation().getWorld())) {
      return;
    }

    if (event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.CHUNK_GEN) {
      event.setCancelled(true);
    }
  }

  @EventHandler
  public void on(PlayerQuitEvent event) {
    lastMessageByPlayer.remove(event.getPlayer().getUniqueId());
  }

  private boolean sameChunk(Location from, Location to) {
    if (from == null || to == null || from.getWorld() == null || to.getWorld() == null) {
      return true;
    }

    if (!from.getWorld().equals(to.getWorld())) {
      return false;
    }

    return from.getBlockX() >> 4 == to.getBlockX() >> 4 && from.getBlockZ() >> 4 == to.getBlockZ() >> 4;
  }

  private boolean isChunkGenerated(Location location) {
    if (location == null || location.getWorld() == null) {
      return true;
    }

    return location.getWorld().isChunkGenerated(location.getBlockX() >> 4, location.getBlockZ() >> 4);
  }

  private boolean isSurging(World world) {
    IntegrationMetricGroup group = worldGroup(world);
    if (group == null) {
      return false;
    }
    double tickMS = sample(SamplerTickTime.ID);
    double pregenQueue = metricOr(group, IntegrationMetricSchema.IRIS_PREGEN_QUEUE, -1D);
    double generationMS = metricOr(group, IntegrationMetricSchema.IRIS_GENERATION_TOTAL_MS, -1D);

    return tickMS >= triggerTickMS
        || (pregenQueue >= 0D && pregenQueue >= triggerIrisPregenQueue)
        || (generationMS >= 0D && generationMS >= triggerIrisGenerationMS);
  }

  private IntegrationMetricGroup worldGroup(World world) {
    if (world == null) {
      return null;
    }
    IntegrationController integration = React.controller(IntegrationController.class);
    if (integration == null || integration.getRemoteSamplerBridge() == null) {
      return null;
    }
    return integration.getRemoteSamplerBridge().getGroup(
        "iris",
        "world",
        WorldIdentity.serialize(world)
    );
  }

  private double metricOr(IntegrationMetricGroup group, String key, double fallback) {
    IntegrationMetricSample sample = group.samples().get(key);
    return sample == null ? fallback : sample.valueOr(fallback);
  }

  private void rolloverWindow(long now) {
    if (now - windowStartMS <= windowMS) {
      return;
    }

    windowStartMS = now;
    moveAttempts = 0;
    teleportAttempts = 0;
  }

  private boolean bypass(Player player) {
    return player == null || (bypassPermission != null && !bypassPermission.isBlank() && player.hasPermission(bypassPermission));
  }

  private void notifyPlayer(Player player, String message, long now) {
    if (player == null || message == null || message.isBlank()) {
      return;
    }

    Long last = lastMessageByPlayer.get(player.getUniqueId());
    if (last != null && now - last < messageCooldownMS) {
      return;
    }

    lastMessageByPlayer.put(player.getUniqueId(), now);
    player.sendMessage(message);
  }
}
