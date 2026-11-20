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
import art.arcane.react.api.sampler.Sampler;
import art.arcane.react.api.sampler.SamplerHosts;
import art.arcane.react.content.sampler.SamplerIncidentScore;
import art.arcane.react.content.sampler.SamplerTickTime;

import art.arcane.react.core.incident.IncidentAction;
import art.arcane.react.core.incident.IncidentEvidence;
import art.arcane.react.core.incident.IncidentRecord;
import art.arcane.react.platform.IncidentHost;

import art.arcane.react.platform.GameLocation;
import art.arcane.react.platform.HopperHost;
import art.arcane.react.platform.ListenerPriority;
import art.arcane.react.platform.PortalHost;
import art.arcane.react.platform.ReactPlatform;
import art.arcane.react.platform.ReactPlatforms;
import art.arcane.react.platform.RedstoneHost;
import art.arcane.react.platform.SpawnHost;
import art.arcane.react.platform.Subscription;
import art.arcane.react.platform.WorldHost;
import art.arcane.react.util.common.scheduling.J;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicLongArray;

@art.arcane.react.util.project.config.ConfigDescription("Configuration for Incident Mode feature. This feature continuously monitors server behavior and applies guardrails during runtime.")
public class FeatureIncidentMode extends ReactFeature {
  public static final String ID = "incident-mode";
  private transient final AtomicLong evaluationQueuedGeneration = new AtomicLong(-1L);
  private transient final AtomicLong lifecycleGeneration = new AtomicLong();
  @art.arcane.react.util.project.config.ConfigDoc(value = "Main evaluation interval for incident mode in milliseconds.", impact = "Lower values react faster but consume more CPU; higher values reduce overhead but react later.")
  private int tickIntervalMS = 1000;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Trigger threshold for enter incident score in incident mode.", impact = "Higher values trigger mitigation later; lower values trigger earlier and more aggressively.")
  private double enterIncidentScore = 58;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Trigger threshold for exit incident score in incident mode.", impact = "Higher values trigger mitigation later; lower values trigger earlier and more aggressively.")
  private double exitIncidentScore = 35;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Tick-time threshold for enter in incident mode (milliseconds).", impact = "Higher values delay activation or exit; lower values make this threshold easier to cross.")
  private double enterTickMS = 60;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Tick-time threshold for exit in incident mode (milliseconds).", impact = "Higher values delay activation or exit; lower values make this threshold easier to cross.")
  private double exitTickMS = 46;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Minimum incident duration ms required by incident mode.", impact = "Higher values require stronger signals before action; lower values make this condition easier to satisfy.")
  private int minimumIncidentDurationMS = 8000;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Grace period after activation during which incident mode will not engage (milliseconds).", impact = "Higher values ignore startup and reload tick spikes longer; lower values let incident mode engage sooner after boot.")
  private int startupGraceMS = 60000;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Rolling window length for rate checks (milliseconds).", impact = "Longer windows smooth bursts but react slower; shorter windows react faster but are more sensitive.")
  private int rateWindowMS = 1000;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Maximum spawner spawns allowed per window in incident mode.", impact = "Higher values permit larger bursts before control engages; lower values clamp spikes sooner.")
  private int maxSpawnerSpawnsPerWindow = 28;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Maximum natural spawns allowed per window in incident mode.", impact = "Higher values permit larger bursts before control engages; lower values clamp spikes sooner.")
  private int maxNaturalSpawnsPerWindow = 70;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Maximum portal events allowed per window in incident mode.", impact = "Higher values permit larger bursts before control engages; lower values clamp spikes sooner.")
  private int maxPortalEventsPerWindow = 18;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Maximum hopper moves allowed per window in incident mode.", impact = "Higher values permit larger bursts before control engages; lower values clamp spikes sooner.")
  private int maxHopperMovesPerWindow = 120;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Maximum redstone transitions allowed per window in incident mode.", impact = "Higher values permit larger bursts before control engages; lower values clamp spikes sooner.")
  private int maxRedstoneTransitionsPerWindow = 220;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Bypasses incident mode handling for bypass near players.", impact = "Enable this to skip enforcement in matching situations; disable it for strict handling.")
  private boolean bypassNearPlayers = true;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Bypass player radius used by incident mode (blocks).", impact = "Higher values widen the search area and cost more work; lower values narrow scope and run cheaper.")
  private double bypassPlayerRadius = 14;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Enables extra logging for verbose transitions in incident mode.", impact = "Enable for diagnostics; disable to reduce chat or log noise.")
  private boolean verboseTransitions = true;
  private transient volatile boolean incident;
  private transient volatile boolean active;
  private transient volatile long incidentSince;
  private transient volatile long activatedAtMS;
  private transient volatile String activeIncidentId;
  private transient final RateWindow<RateCounter> rateWindow = new RateWindow<>(RateCounter.values().length);
  private transient final AtomicLongArray mitigations = new AtomicLongArray(RateCounter.values().length);
  private transient volatile WorldHost worlds;
  private transient List<Subscription> subscriptions = List.of();

  public FeatureIncidentMode() {
    super(ID);
  }

  @Override
  public void onActivate() {
    long now = System.currentTimeMillis();
    lifecycleGeneration.incrementAndGet();
    active = true;
    incident = false;
    incidentSince = 0L;
    activatedAtMS = now;
    activeIncidentId = null;
    rateWindow.reset(now);
    resetMitigations();
    evaluationQueuedGeneration.set(-1L);
    ReactPlatform platform = ReactPlatforms.get();
    worlds = platform.worldHost().orElse(null);
    List<Subscription> opened = new ArrayList<>();
    platform.spawnHost().ifPresent(host -> opened.add(host.onCreatureSpawn(ListenerPriority.LOWEST, true, this::onSpawn)));
    platform.portalHost().ifPresent(host -> opened.add(host.onPortal(ListenerPriority.LOWEST, true, this::onPortal)));
    platform.hopperHost().ifPresent(host -> opened.add(host.onMove(this::onMove)));
    platform.redstoneHost().ifPresent(host -> opened.add(host.onSignal(ListenerPriority.LOWEST, this::onSignal)));
    subscriptions = opened;
  }

  @Override
  public void onDeactivate() {
    for (Subscription subscription : subscriptions) {
      subscription.cancel();
    }
    subscriptions = List.of();
    if (incident && activeIncidentId != null) {
      recordResolution(
          System.currentTimeMillis(),
          "DISABLED",
          "Incident mode was disabled while its rate guardrails were active.",
          "The feature stopped by configuration or plugin lifecycle before recovery thresholds were observed."
      );
    }
    active = false;
    lifecycleGeneration.incrementAndGet();
    evaluationQueuedGeneration.set(-1L);
    incident = false;
    incidentSince = 0L;
    activeIncidentId = null;
  }

  public boolean isIncidentActive() {
    return incident;
  }

  public long getIncidentSince() {
    return incidentSince;
  }

  @Override
  public int getTickInterval() {
    return tickIntervalMS;
  }

  @Override
  public void onTick() {
    if (!active) {
      return;
    }

    long generation = lifecycleGeneration.get();
    if (!evaluationQueuedGeneration.compareAndSet(-1L, generation)) {
      return;
    }

    try {
      J.s(() -> {
        try {
          if (active && lifecycleGeneration.get() == generation) {
            evaluateIncident();
          }
        } finally {
          evaluationQueuedGeneration.compareAndSet(generation, -1L);
        }
      });
    } catch (RuntimeException | Error failure) {
      evaluationQueuedGeneration.compareAndSet(generation, -1L);
      throw failure;
    }
  }

  private void evaluateIncident() {
    double tickMS = sample(SamplerTickTime.ID);
    long now = System.currentTimeMillis();

    if (!incident) {
      if (now - activatedAtMS < Math.max(0, startupGraceMS)) {
        return;
      }

      SamplerIncidentScore.IncidentScoreSnapshot snapshot = incidentScoreSnapshot();
      double incidentScore = snapshot.score();
      if (incidentScore >= enterIncidentScore || tickMS >= enterTickMS) {
        incident = true;
        incidentSince = now;
        activeIncidentId = UUID.randomUUID().toString();
        resetMitigations();
        recordStart(now, snapshot, tickMS);
        if (verboseTransitions) {
          ReactPlatforms.get().warn("Incident mode enabled (score " + format(incidentScore) + ", tick " + format(tickMS) + "ms)");
        }
      }
      return;
    }

    if (now - incidentSince < minimumIncidentDurationMS || tickMS > exitTickMS) {
      return;
    }

    SamplerIncidentScore.IncidentScoreSnapshot snapshot = incidentScoreSnapshot();
    double incidentScore = snapshot.score();
    if (incidentScore <= exitIncidentScore) {
      incident = false;
      recordResolution(
          now,
          "RESOLVED",
          "Server pressure recovered below the configured exit thresholds.",
          "Incident score reached " + format(incidentScore) + " and tick time reached " + format(tickMS) + " ms."
      );
      incidentSince = 0L;
      activeIncidentId = null;
      if (verboseTransitions) {
        ReactPlatforms.get().info("Incident mode disabled (score " + format(incidentScore) + ", tick " + format(tickMS) + "ms)");
      }
    }
  }

  void onSpawn(SpawnHost.Spawn event) {
    if (!incident) {
      return;
    }

    String reason = event.reason();
    boolean spawner = SpawnHost.isSpawnerReason(reason);
    boolean natural = SpawnHost.isNaturalReason(reason);

    if (spawner) {
      if (!rateWindow.tryAcquire(RateCounter.SPAWNER, maxSpawnerSpawnsPerWindow, System.currentTimeMillis(), rateWindowMS)) {
        event.cancel();
        mitigations.incrementAndGet(RateCounter.SPAWNER.ordinal());
      }
      return;
    }

    if (natural && !rateWindow.tryAcquire(RateCounter.NATURAL, maxNaturalSpawnsPerWindow, System.currentTimeMillis(), rateWindowMS)) {
      event.cancel();
      mitigations.incrementAndGet(RateCounter.NATURAL.ordinal());
    }
  }

  void onPortal(PortalHost.Portal event) {
    if (!incident) {
      return;
    }

    GameLocation location = event.to() == null ? event.entity().location() : event.to();
    if (shouldBypass(location)) {
      return;
    }

    if (!rateWindow.tryAcquire(RateCounter.PORTAL, maxPortalEventsPerWindow, System.currentTimeMillis(), rateWindowMS)) {
      event.cancel();
      mitigations.incrementAndGet(RateCounter.PORTAL.ordinal());
    }
  }

  void onMove(HopperHost.Move event) {
    if (!incident) {
      return;
    }

    GameLocation location = event.hopper();
    if (location == null || shouldBypass(location)) {
      return;
    }

    if (!rateWindow.tryAcquire(RateCounter.HOPPER, maxHopperMovesPerWindow, System.currentTimeMillis(), rateWindowMS)) {
      event.cancel();
      mitigations.incrementAndGet(RateCounter.HOPPER.ordinal());
    }
  }

  void onSignal(RedstoneHost.Signal event) {
    if (!incident || event.oldCurrent() == event.newCurrent()) {
      return;
    }

    GameLocation location = event.location();
    if (shouldBypass(location)) {
      return;
    }

    if (!rateWindow.tryAcquire(RateCounter.REDSTONE, maxRedstoneTransitionsPerWindow, System.currentTimeMillis(), rateWindowMS)) {
      event.setNewCurrent(event.oldCurrent());
      mitigations.incrementAndGet(RateCounter.REDSTONE.ordinal());
    }
  }

  private void recordStart(long now, SamplerIncidentScore.IncidentScoreSnapshot snapshot, double tickMS) {
    IncidentHost controller = incidentHost();
    if (controller == null || activeIncidentId == null) {
      return;
    }
    boolean scoreTrigger = snapshot.available() && snapshot.score() >= enterIncidentScore;
    boolean tickTrigger = tickMS >= enterTickMS;
    IncidentEvidence primary = snapshot.evidence().stream()
        .filter(IncidentEvidence::available)
        .max(Comparator.comparingDouble(IncidentEvidence::scorePoints))
        .orElse(null);
    String trigger = scoreTrigger && tickTrigger
        ? "Both the composite incident score and tick time crossed their entry thresholds."
        : scoreTrigger
        ? "The composite incident score crossed its entry threshold."
        : "Tick time crossed its entry threshold.";
    String primaryCause = primary == null || primary.scorePoints() <= 0D
        ? trigger
        : trigger + " The strongest measured contributor was " + primary.label() + " at " + primary.display() + ".";
    List<IncidentEvidence> evidence = new ArrayList<>(snapshot.evidence());
    evidence.add(tickEvidence(tickMS, enterTickMS));
    controller.record(new IncidentRecord(
        UUID.randomUUID().toString(),
        activeIncidentId,
        "SERVER_PRESSURE",
        "STARTED",
        snapshot.score() >= 75D || tickMS >= 75D ? "CRITICAL" : "WARNING",
        now,
        now,
        ID,
        "Incident mode engaged",
        "React enabled spawn, portal, hopper, and redstone rate guardrails.",
        primaryCause,
        null,
        evidence,
        List.of(new IncidentAction(
            "incident-rate-guards",
            "Runtime rate guardrails",
            "ACTIVE",
            "Excess events are limited while server pressure remains elevated.",
            now
        )),
        Map.of(
            "incidentScoreThreshold", format(enterIncidentScore),
            "tickThresholdMs", format(enterTickMS)
        )
    ));
  }

  private void recordResolution(long now, String phase, String summary, String cause) {
    IncidentHost controller = incidentHost();
    if (controller == null || activeIncidentId == null) {
      return;
    }
    controller.record(new IncidentRecord(
        UUID.randomUUID().toString(),
        activeIncidentId,
        "SERVER_PRESSURE",
        phase,
        "INFO",
        now,
        incidentSince,
        ID,
        "Incident mode released",
        summary,
        cause,
        null,
        incidentScoreSnapshot().evidence(),
        List.of(new IncidentAction(
            "incident-rate-guards",
            "Runtime rate guardrails",
            "COMPLETED",
            mitigationSummary(),
            now
        )),
        mitigationContext()
    ));
  }

  private SamplerIncidentScore.IncidentScoreSnapshot incidentScoreSnapshot() {
    Sampler found = SamplerHosts.sampler(SamplerIncidentScore.ID);
    SamplerIncidentScore sampler = found instanceof SamplerIncidentScore incidentScore ? incidentScore : null;
    if (sampler == null) {
      return SamplerIncidentScore.IncidentScoreSnapshot.empty();
    }
    sampler.sample();
    return sampler.snapshot();
  }

  private IncidentEvidence tickEvidence(double tickMS, double threshold) {
    double maximum = Math.max(threshold + 1D, 150D);
    double pressure = Math.max(0D, Math.min(1D, (tickMS - threshold) / (maximum - threshold)));
    return new IncidentEvidence(
        SamplerTickTime.ID,
        "Tick Time",
        true,
        tickMS,
        format(tickMS) + " ms",
        pressure,
        0D,
        0D,
        threshold,
        maximum
    );
  }

  private Map<String, String> mitigationContext() {
    return Map.of(
        "spawnerSpawnsBlocked", Long.toString(mitigations.get(RateCounter.SPAWNER.ordinal())),
        "naturalSpawnsBlocked", Long.toString(mitigations.get(RateCounter.NATURAL.ordinal())),
        "portalEventsBlocked", Long.toString(mitigations.get(RateCounter.PORTAL.ordinal())),
        "hopperMovesBlocked", Long.toString(mitigations.get(RateCounter.HOPPER.ordinal())),
        "redstoneTransitionsBlocked", Long.toString(mitigations.get(RateCounter.REDSTONE.ordinal()))
    );
  }

  private String mitigationSummary() {
    long total = 0L;
    for (RateCounter counter : RateCounter.values()) {
      total += mitigations.get(counter.ordinal());
    }
    return total + " excess runtime events were limited during this incident.";
  }

  private void resetMitigations() {
    for (RateCounter counter : RateCounter.values()) {
      mitigations.set(counter.ordinal(), 0L);
    }
  }

  private String format(double value) {
    return String.format(Locale.ROOT, "%.1f", value);
  }

  private boolean shouldBypass(GameLocation location) {
    WorldHost presence = worlds;
    return bypassNearPlayers && location != null && presence != null && presence.anyPlayerNear(location, bypassPlayerRadius);
  }

  private enum RateCounter {
    SPAWNER,
    NATURAL,
    PORTAL,
    HOPPER,
    REDSTONE
  }

  private static IncidentHost incidentHost() {
    ReactPlatform platform = ReactPlatforms.getOrNull();
    return platform == null ? null : platform.incidentHost().orElse(null);
  }
}
