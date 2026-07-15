package art.arcane.react.content.feature;

import art.arcane.react.React;
import art.arcane.react.api.feature.ReactCapabilityFeature;
import art.arcane.react.content.sampler.SamplerTickTime;
import art.arcane.react.core.controller.IntegrationController;
import art.arcane.react.model.ReactConfiguration;
import art.arcane.volmlib.integration.IntegrationMetricSchema;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.projectiles.ProjectileSource;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@art.arcane.react.util.project.config.ConfigDescription("Configuration for Adapt Runtime Surge Guard feature. This feature continuously monitors server behavior and applies guardrails during runtime.")
public class FeatureAdaptRuntimeSurgeGuard extends ReactCapabilityFeature implements Listener {
  public static final String ID = "feature-adapt-runtime-surge-guard";

  @art.arcane.react.util.project.config.ConfigDoc(value = "Main evaluation interval for adapt runtime surge guard in milliseconds.", impact = "Lower values react faster but consume more CPU; higher values reduce overhead but react later.")
  private int tickIntervalMS = 1000;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Tick-time threshold for trigger in adapt runtime surge guard (milliseconds).", impact = "Higher values delay activation or exit; lower values make this threshold easier to cross.")
  private double triggerTickMS = 58D;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Trigger threshold for trigger session load percent in adapt runtime surge guard.", impact = "Higher values trigger mitigation later; lower values trigger earlier and more aggressively.")
  private double triggerSessionLoadPercent = 70D;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Trigger threshold for trigger ability ops minute in adapt runtime surge guard.", impact = "Higher values trigger mitigation later; lower values trigger earlier and more aggressively.")
  private double triggerAbilityOpsPerMinute = 260D;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Rolling enforcement window length used by adapt runtime surge guard (milliseconds).", impact = "Longer windows smooth bursts but react slower; shorter windows react faster but are more sensitive.")
  private int windowMS = 1800;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Maximum interactions allowed per window in adapt runtime surge guard.", impact = "Higher values permit larger bursts before control engages; lower values clamp spikes sooner.")
  private int maxInteractionsPerWindow = 8;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Maximum combat ops allowed per window in adapt runtime surge guard.", impact = "Higher values permit larger bursts before control engages; lower values clamp spikes sooner.")
  private int maxCombatOpsPerWindow = 10;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Maximum consume ops allowed per window in adapt runtime surge guard.", impact = "Higher values permit larger bursts before control engages; lower values clamp spikes sooner.")
  private int maxConsumeOpsPerWindow = 4;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Message rate-limit cooldown for adapt runtime surge guard notifications (milliseconds).", impact = "Higher values reduce repeated chat spam; lower values allow status messages more often.")
  private long messageCooldownMS = 2200L;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Permission node string checked before adapt runtime surge guard enforcement.", impact = "Change this to match your permission model for bypass behavior.")
  private String bypassPermission = "react.secret.adapt.bypass";

  private transient volatile boolean surge;
  private transient Map<UUID, WindowCounter> interactionOps = new ConcurrentHashMap<>();
  private transient Map<UUID, WindowCounter> combatOps = new ConcurrentHashMap<>();
  private transient Map<UUID, WindowCounter> consumeOps = new ConcurrentHashMap<>();
  private transient Map<UUID, Long> lastMessageByPlayer = new ConcurrentHashMap<>();

  public FeatureAdaptRuntimeSurgeGuard() {
    super(ID);
  }

  @Override
  public Set<String> requiredCapabilities() {
    return Set.of("adapt");
  }

  @Override
  public boolean isSecretBundle() {
    return true;
  }

  @Override
  public void onActivate() {
    surge = false;
    interactionOps = new ConcurrentHashMap<>();
    combatOps = new ConcurrentHashMap<>();
    consumeOps = new ConcurrentHashMap<>();
    lastMessageByPlayer = new ConcurrentHashMap<>();
  }

  @Override
  public void onDeactivate() {
    surge = false;
    interactionOps.clear();
    combatOps.clear();
    consumeOps.clear();
    lastMessageByPlayer.clear();
  }

  @Override
  public int getTickInterval() {
    return tickIntervalMS;
  }

  @Override
  public void onTick() {
    surge = isSurging();
  }

  @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
  public void on(PlayerInteractEvent event) {
    if (!surge) {
      return;
    }

    Player player = event.getPlayer();
    if (bypass(player)) {
      return;
    }

    long now = System.currentTimeMillis();
    if (allow(interactionOps, player.getUniqueId(), maxInteractionsPerWindow, now)) {
      return;
    }

    event.setCancelled(true);
    notifyPlayer(player, "Adapt runtime surge guard smoothed rapid interaction burst.", now);
  }

  @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
  public void on(EntityDamageByEntityEvent event) {
    if (!surge) {
      return;
    }

    Player player = resolvePlayerDamager(event.getDamager());
    if (player == null || bypass(player)) {
      return;
    }

    long now = System.currentTimeMillis();
    if (allow(combatOps, player.getUniqueId(), maxCombatOpsPerWindow, now)) {
      return;
    }

    event.setCancelled(true);
    notifyPlayer(player, "Adapt runtime surge guard smoothed combat ability burst.", now);
  }

  @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
  public void on(PlayerItemConsumeEvent event) {
    if (!surge) {
      return;
    }

    Player player = event.getPlayer();
    if (bypass(player)) {
      return;
    }

    long now = System.currentTimeMillis();
    if (allow(consumeOps, player.getUniqueId(), maxConsumeOpsPerWindow, now)) {
      return;
    }

    event.setCancelled(true);
    notifyPlayer(player, "Adapt runtime surge guard smoothed item-consume burst.", now);
  }

  @EventHandler
  public void on(PlayerQuitEvent event) {
    UUID playerId = event.getPlayer().getUniqueId();
    interactionOps.remove(playerId);
    combatOps.remove(playerId);
    consumeOps.remove(playerId);
    lastMessageByPlayer.remove(playerId);
  }

  private Player resolvePlayerDamager(Entity damager) {
    if (damager instanceof Player player) {
      return player;
    }

    if (damager instanceof Projectile projectile) {
      ProjectileSource shooter = projectile.getShooter();
      if (shooter instanceof Player player) {
        return player;
      }
    }

    return null;
  }

  private boolean isSurging() {
    double tickMS = sample(SamplerTickTime.ID);
    double sessionLoad = metricOr(IntegrationMetricSchema.ADAPT_SESSION_LOAD, -1D);
    double abilityOps = metricOr(ReactConfiguration.adaptAbilityOpsMetricKey(), -1D);
    return tickMS >= triggerTickMS
        || (sessionLoad >= 0D && sessionLoad >= triggerSessionLoadPercent)
        || (abilityOps >= 0D && abilityOps >= triggerAbilityOpsPerMinute);
  }

  private double metricOr(String key, double fallback) {
    IntegrationController integration = React.controller(IntegrationController.class);
    if (integration == null || integration.getRemoteSamplerBridge() == null) {
      return fallback;
    }

    return integration.getRemoteSamplerBridge().valueOr("adapt", key, fallback);
  }

  private boolean allow(Map<UUID, WindowCounter> map, UUID playerId, int maxOps, long now) {
    if (playerId == null) {
      return true;
    }

    WindowCounter counter = map.computeIfAbsent(playerId, ignored -> new WindowCounter(now));
    synchronized (counter) {
      if (now - counter.windowStart > windowMS) {
        counter.windowStart = now;
        counter.ops = 0;
      }

      counter.ops++;
      return counter.ops <= Math.max(1, maxOps);
    }
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

  private static final class WindowCounter {
    @art.arcane.react.util.project.config.ConfigDoc(value = "Internal timestamp used by adapt runtime surge guard to track timing windows and decay.", impact = "Primarily runtime state; changing this manually can distort cooldown or throttling behavior.")
    private long windowStart;
    @art.arcane.react.util.project.config.ConfigDoc(value = "Internal counter used by adapt runtime surge guard while tracking burst activity.", impact = "Primarily runtime state; React updates this automatically during live evaluation.")
    private int ops;

    private WindowCounter(long now) {
      windowStart = now;
      ops = 0;
    }
  }
}
