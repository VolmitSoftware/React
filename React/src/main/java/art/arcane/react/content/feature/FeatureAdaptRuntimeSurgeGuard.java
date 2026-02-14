package art.arcane.react.content.feature;

import art.arcane.react.React;
import art.arcane.react.api.feature.ReactCapabilityFeature;
import art.arcane.react.content.sampler.SamplerTickTime;
import art.arcane.react.core.controller.IntegrationController;
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
import org.bukkit.projectiles.ProjectileSource;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class FeatureAdaptRuntimeSurgeGuard extends ReactCapabilityFeature implements Listener {
    public static final String ID = "feature-adapt-runtime-surge-guard";

    private int tickIntervalMS = 1000;
    private double triggerTickMS = 58D;
    private double triggerSessionLoadPercent = 70D;
    private double triggerAbilityOpsPerMinute = 260D;
    private int windowMS = 1800;
    private int maxInteractionsPerWindow = 8;
    private int maxCombatOpsPerWindow = 10;
    private int maxConsumeOpsPerWindow = 4;
    private long messageCooldownMS = 2200L;
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
        double abilityOps = metricOr(IntegrationMetricSchema.ADAPT_ABILITY_OPS, -1D);
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

    private double sample(String samplerId) {
        var sampler = React.sampler(samplerId);
        return sampler == null ? 0D : sampler.sample();
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
        private long windowStart;
        private int ops;

        private WindowCounter(long now) {
            windowStart = now;
            ops = 0;
        }
    }
}
