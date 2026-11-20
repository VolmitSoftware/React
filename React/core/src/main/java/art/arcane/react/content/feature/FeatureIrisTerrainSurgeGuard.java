package art.arcane.react.content.feature;

import art.arcane.react.api.feature.ReactCapabilityFeature;
import art.arcane.react.content.sampler.SamplerTickTime;
import art.arcane.react.core.integration.IrisPregenPressure;
import art.arcane.react.localization.ReactLanguage;
import art.arcane.react.localization.catalog.RuntimeMessages;
import art.arcane.react.platform.GameLocation;
import art.arcane.react.platform.IntegrationMetricsHost;
import art.arcane.react.platform.PlayerHost;
import art.arcane.react.platform.ReactPlatform;
import art.arcane.react.platform.ReactPlatforms;
import art.arcane.react.platform.ReactPlayer;
import art.arcane.react.platform.ReactWorld;
import art.arcane.react.platform.Subscription;
import art.arcane.react.platform.WorldHost;
import art.arcane.volmlib.integration.IntegrationMetricGroup;
import art.arcane.volmlib.integration.IntegrationMetricSample;
import art.arcane.volmlib.integration.IntegrationMetricSchema;
import art.arcane.volmlib.util.localization.TextKey;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@art.arcane.react.util.project.config.ConfigDescription("Configuration for Iris Terrain Surge Guard feature. This feature continuously monitors server behavior and applies guardrails during runtime.")
public class FeatureIrisTerrainSurgeGuard extends ReactCapabilityFeature {
  public static final String ID = "feature-iris-terrain-surge-guard";
  private static final long SURGE_VERDICT_TTL_MS = 1_000L;

  @art.arcane.react.util.project.config.ConfigDoc(value = "Main evaluation interval for iris terrain surge guard in milliseconds.", impact = "Lower values react faster but consume more CPU; higher values reduce overhead but react later.")
  private int tickIntervalMS = 1000;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Tick-time threshold for trigger in iris terrain surge guard (milliseconds).", impact = "Higher values delay activation or exit; lower values make this threshold easier to cross.")
  private double triggerTickMS = 56D;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Trigger threshold for Iris generation time in iris terrain surge guard.", impact = "Higher values trigger mitigation later; lower values trigger earlier and more aggressively.")
  private double triggerIrisGenerationMS = 24D;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Iris pregenerator in-flight chunk requests for this world that trigger iris terrain surge guard while tick time also reaches triggerIrisPregenTickMS; values clamp to 1..256.", impact = "Higher values trigger only while the world's pregeneration runs near its full concurrency; lower values also trigger on lighter pregeneration load.")
  private int triggerIrisPregenInFlight = IrisPregenPressure.DEFAULT_IN_FLIGHT_THRESHOLD;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Tick-time threshold that must also be reached before Iris pregenerator in-flight requests trigger iris terrain surge guard (milliseconds); values clamp to 1..1000.", impact = "Higher values let pregeneration run without limiting players until the server falls further behind; lower values limit player chunk generation sooner while a pregeneration runs.")
  private double triggerIrisPregenTickMS = IrisPregenPressure.DEFAULT_TICK_GATE_MS;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Rolling enforcement window length used by iris terrain surge guard (milliseconds).", impact = "Longer windows smooth bursts but react slower; shorter windows react faster but are more sensitive.")
  private int windowMS = 2500;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Maximum ungenerated chunk moves allowed per window in iris terrain surge guard.", impact = "Higher values permit larger bursts before control engages; lower values clamp spikes sooner.")
  private int maxUngeneratedChunkMovesPerWindow = 10;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Maximum ungenerated chunk teleports allowed per window in iris terrain surge guard.", impact = "Higher values permit larger bursts before control engages; lower values clamp spikes sooner.")
  private int maxUngeneratedChunkTeleportsPerWindow = 4;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Message rate-limit cooldown for iris terrain surge guard notifications (milliseconds).", impact = "Higher values reduce repeated chat spam; lower values allow status messages more often.")
  private long messageCooldownMS = 2500L;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Permission node string checked before iris terrain surge guard enforcement.", impact = "Change this to match your permission model for bypass behavior.")
  private String bypassPermission = "react.secret.iris.bypass";

  private transient final RateWindow<RateCounter> rateWindow = new RateWindow<>(RateCounter.values().length);
  private transient Map<UUID, Long> lastMessageByPlayer = new ConcurrentHashMap<>();
  private transient Map<UUID, SurgeVerdict> surgeVerdictByWorld = new ConcurrentHashMap<>();
  private transient final List<Subscription> subscriptions = new ArrayList<>();

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
    rateWindow.reset(System.currentTimeMillis());
    lastMessageByPlayer = new ConcurrentHashMap<>();
    surgeVerdictByWorld = new ConcurrentHashMap<>();
    PlayerHost host = players();
    if (host != null) {
      synchronized (subscriptions) {
        subscriptions.add(host.onMove(this::move));
        subscriptions.add(host.onTeleport(this::teleport));
        subscriptions.add(host.onQuit(player -> lastMessageByPlayer.remove(player.id())));
      }
    }
  }

  @Override
  public void onDeactivate() {
    synchronized (subscriptions) {
      for (Subscription subscription : subscriptions) {
        subscription.cancel();
      }
      subscriptions.clear();
    }
    lastMessageByPlayer.clear();
    surgeVerdictByWorld.clear();
  }

  @Override
  public int getTickInterval() {
    return tickIntervalMS;
  }

  @Override
  public void onTick() {
  }

  void move(PlayerHost.Move event) {
    if (event.isCancelled() || event.to() == null || sameChunk(event.from(), event.to())) {
      return;
    }

    if (!isSurging(event.to().worldId())) {
      return;
    }

    ReactPlayer player = event.player();
    if (bypass(player) || isChunkGenerated(event.to())) {
      return;
    }

    long now = System.currentTimeMillis();
    if (rateWindow.tryAcquire(RateCounter.MOVE, maxUngeneratedChunkMovesPerWindow, now, windowMS)) {
      return;
    }

    event.cancel();
    notifyPlayer(player, RuntimeMessages.IRIS_MOVEMENT_THROTTLED, now);
  }

  void teleport(PlayerHost.Teleport event) {
    if (event.isCancelled() || event.to() == null || !isSurging(event.to().worldId())) {
      return;
    }

    ReactPlayer player = event.player();
    if (bypass(player) || isChunkGenerated(event.to())) {
      return;
    }

    long now = System.currentTimeMillis();
    if (rateWindow.tryAcquire(RateCounter.TELEPORT, maxUngeneratedChunkTeleportsPerWindow, now, windowMS)) {
      return;
    }

    event.cancel();
    notifyPlayer(player, RuntimeMessages.IRIS_TELEPORT_THROTTLED, now);
  }

  private boolean sameChunk(GameLocation from, GameLocation to) {
    if (from == null || to == null || from.worldId() == null || to.worldId() == null) {
      return true;
    }

    if (!from.worldId().equals(to.worldId())) {
      return false;
    }

    return from.chunkX() == to.chunkX() && from.chunkZ() == to.chunkZ();
  }

  private boolean isChunkGenerated(GameLocation location) {
    PlayerHost host = players();
    if (location == null || location.worldId() == null || host == null) {
      return true;
    }

    return host.isChunkGenerated(location);
  }

  private boolean isSurging(UUID worldId) {
    if (worldId == null) {
      return false;
    }

    long now = System.currentTimeMillis();
    SurgeVerdict cached = surgeVerdictByWorld.get(worldId);
    if (cached != null && now - cached.evaluatedAtMs() < SURGE_VERDICT_TTL_MS) {
      return cached.surging();
    }

    boolean surging = evaluateSurge(worldId);
    surgeVerdictByWorld.put(worldId, new SurgeVerdict(surging, System.currentTimeMillis()));
    return surging;
  }

  private boolean evaluateSurge(UUID worldId) {
    IntegrationMetricGroup group = worldGroup(worldId);
    if (group == null) {
      return false;
    }
    return shouldSurge(
        sample(SamplerTickTime.ID),
        metricOr(group, IntegrationMetricSchema.IRIS_GENERATION_TOTAL_MS, -1D),
        metricOr(group, IntegrationMetricSchema.IRIS_PREGEN_QUEUE, -1D),
        new SurgeTriggers(triggerTickMS, triggerIrisGenerationMS, triggerIrisPregenInFlight, triggerIrisPregenTickMS)
    );
  }

  static boolean shouldSurge(double tickMS, double generationMS, double pregenInFlight, SurgeTriggers triggers) {
    return tickMS >= triggers.tickMS()
        || (generationMS >= 0D && generationMS >= triggers.generationMS())
        || IrisPregenPressure.hasInFlightPressureUnderLoad(
            pregenInFlight,
            triggers.pregenInFlight(),
            tickMS,
            triggers.pregenTickMS()
        );
  }

  private IntegrationMetricGroup worldGroup(UUID worldId) {
    ReactPlatform platform = ReactPlatforms.getOrNull();
    WorldHost worlds = platform == null ? null : platform.worldHost().orElse(null);
    IntegrationMetricsHost integrations = platform == null ? null : platform.integrationMetricsHost().orElse(null);
    if (worlds == null || integrations == null) {
      return null;
    }
    ReactWorld world = worlds.world(worldId).orElse(null);
    return world == null ? null : integrations.group("iris", "world", worlds.worldKey(world));
  }

  private static PlayerHost players() {
    ReactPlatform platform = ReactPlatforms.getOrNull();
    return platform == null ? null : platform.playerHost().orElse(null);
  }

  private double metricOr(IntegrationMetricGroup group, String key, double fallback) {
    IntegrationMetricSample sample = group.samples().get(key);
    return sample == null ? fallback : sample.valueOr(fallback);
  }

  private boolean bypass(ReactPlayer player) {
    return player == null || (bypassPermission != null && !bypassPermission.isBlank() && player.hasPermission(bypassPermission));
  }

  private void notifyPlayer(ReactPlayer player, TextKey message, long now) {
    if (player == null || message == null) {
      return;
    }

    Long last = lastMessageByPlayer.get(player.id());
    if (last != null && now - last < messageCooldownMS) {
      return;
    }

    lastMessageByPlayer.put(player.id(), now);
    ReactLanguage.send(player.handle(), message);
  }

  private enum RateCounter {
    MOVE,
    TELEPORT
  }

  private record SurgeVerdict(boolean surging, long evaluatedAtMs) {
  }

  record SurgeTriggers(double tickMS, double generationMS, int pregenInFlight, double pregenTickMS) {
  }
}
