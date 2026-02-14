package art.arcane.react.content.feature;

import art.arcane.react.React;
import art.arcane.react.api.feature.ReactCapabilityFeature;
import art.arcane.react.content.sampler.SamplerTickTime;
import art.arcane.react.core.controller.IntegrationController;
import art.arcane.volmlib.integration.IntegrationMetricSchema;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class FeatureIrisTerrainSurgeGuard extends ReactCapabilityFeature implements Listener {
    public static final String ID = "feature-iris-terrain-surge-guard";

    private int tickIntervalMS = 1000;
    private double triggerTickMS = 56D;
    private double triggerIrisPregenQueue = 280D;
    private double triggerIrisChunkStreamMS = 24D;
    private int windowMS = 2500;
    private int maxUngeneratedChunkMovesPerWindow = 10;
    private int maxUngeneratedChunkTeleportsPerWindow = 4;
    private boolean cancelChunkGenSpawns = true;
    private long messageCooldownMS = 2500L;
    private String bypassPermission = "react.secret.iris.bypass";

    private transient volatile boolean surge;
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
        surge = false;
        windowStartMS = System.currentTimeMillis();
        moveAttempts = 0;
        teleportAttempts = 0;
        lastMessageByPlayer = new ConcurrentHashMap<>();
    }

    @Override
    public void onDeactivate() {
        surge = false;
        lastMessageByPlayer.clear();
    }

    @Override
    public int getTickInterval() {
        return tickIntervalMS;
    }

    @Override
    public void onTick() {
        surge = isSurging();
        rolloverWindow(System.currentTimeMillis());
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void on(PlayerMoveEvent event) {
        if (!surge || event.getTo() == null) {
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
        if (!surge || event.getTo() == null) {
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
        if (!surge || !cancelChunkGenSpawns) {
            return;
        }

        if (event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.CHUNK_GEN) {
            event.setCancelled(true);
        }
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

    private boolean isSurging() {
        double tickMS = sample(SamplerTickTime.ID);
        double pregenQueue = metricOr(IntegrationMetricSchema.IRIS_PREGEN_QUEUE, -1D);
        double chunkStreamMS = metricOr(IntegrationMetricSchema.IRIS_CHUNK_STREAM_MS, -1D);

        return tickMS >= triggerTickMS
                || (pregenQueue >= 0D && pregenQueue >= triggerIrisPregenQueue)
                || (chunkStreamMS >= 0D && chunkStreamMS >= triggerIrisChunkStreamMS);
    }

    private double metricOr(String key, double fallback) {
        IntegrationController integration = React.controller(IntegrationController.class);
        if (integration == null || integration.getRemoteSamplerBridge() == null) {
            return fallback;
        }

        return integration.getRemoteSamplerBridge().valueOr("iris", key, fallback);
    }

    private double sample(String samplerId) {
        var sampler = React.sampler(samplerId);
        return sampler == null ? 0D : sampler.sample();
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
