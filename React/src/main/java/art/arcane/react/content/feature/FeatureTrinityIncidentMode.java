package art.arcane.react.content.feature;

import art.arcane.react.React;
import art.arcane.react.api.action.Action;
import art.arcane.react.api.action.ActionTicket;
import art.arcane.react.api.feature.Feature;
import art.arcane.react.api.feature.ReactCapabilityFeature;
import art.arcane.react.content.action.ActionIncidentPlaybook;
import art.arcane.react.content.sampler.SamplerIncidentScore;
import art.arcane.react.content.sampler.SamplerTickTime;
import art.arcane.react.core.controller.FeatureController;
import art.arcane.react.core.controller.IncidentController;
import art.arcane.react.core.controller.IntegrationController;
import art.arcane.react.core.incident.IncidentAction;
import art.arcane.react.core.incident.IncidentEvidence;
import art.arcane.react.core.incident.IncidentRecord;
import art.arcane.react.util.common.scheduling.J;
import art.arcane.volmlib.integration.IntegrationMetricSchema;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@art.arcane.react.util.project.config.ConfigDescription("Configuration for Trinity Incident Mode feature. This feature continuously monitors server behavior and applies guardrails during runtime.")
public class FeatureTrinityIncidentMode extends ReactCapabilityFeature {
  public static final String ID = "feature-trinity-incident-mode";

  @art.arcane.react.util.project.config.ConfigDoc(value = "Main evaluation interval for trinity incident mode in milliseconds.", impact = "Lower values react faster but consume more CPU; higher values reduce overhead but react later.")
  private int tickIntervalMS = 1000;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Trigger threshold for enter incident score in trinity incident mode.", impact = "Higher values trigger mitigation later; lower values trigger earlier and more aggressively.")
  private double enterIncidentScore = 62D;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Tick-time threshold for enter in trinity incident mode (milliseconds).", impact = "Higher values delay activation or exit; lower values make this threshold easier to cross.")
  private double enterTickMS = 62D;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Enter iris queue limit used by trinity incident mode.", impact = "Higher values increase buffered work or burst allowance; lower values tighten throttling.")
  private double enterIrisQueue = 340D;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Adapt session load threshold that can trigger incident handling in trinity incident mode (percent).", impact = "Higher values trigger later during heavier load; lower values trigger earlier.")
  private double enterAdaptSessionLoad = 72D;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Adapt ability timing-budget threshold that can trigger incident handling in trinity incident mode (percent).", impact = "Higher values require more measured Adapt guard-check cost before incident handling; lower values trigger earlier.")
  private double enterAdaptAbilityTimingBudgetPercent = 100D;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Minimum engage ms required by trinity incident mode.", impact = "Higher values require stronger signals before action; lower values make this condition easier to satisfy.")
  private int minimumEngageMS = 12000;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Cooldown for playbook cooldown in trinity incident mode (milliseconds).", impact = "Higher values reduce repeat frequency; lower values allow reactions more often.")
  private int playbookCooldownMS = 20000;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Enables extra logging for verbose transitions in trinity incident mode.", impact = "Enable for diagnostics; disable to reduce chat or log noise.")
  private boolean verboseTransitions = true;

  private transient volatile boolean engaged;
  private transient long engagedSinceMS;
  private transient long lastPlaybookAtMS;
  private transient String activeIncidentId;

  public FeatureTrinityIncidentMode() {
    super(ID);
  }

  @Override
  public Set<String> requiredCapabilities() {
    return Set.of("iris", "adapt");
  }

  @Override
  public boolean isSecretBundle() {
    return true;
  }

  @Override
  public void onActivate() {
    engaged = false;
    engagedSinceMS = 0L;
    lastPlaybookAtMS = 0L;
    activeIncidentId = null;
  }

  @Override
  public void onDeactivate() {
    if (engaged && activeIncidentId != null) {
      recordResolution(
          System.currentTimeMillis(),
          "DISABLED",
          "Trinity coordination stopped before recovery was observed."
      );
    }
    engaged = false;
    activeIncidentId = null;
  }

  @Override
  public int getTickInterval() {
    return tickIntervalMS;
  }

  @Override
  public void onTick() {
    long now = System.currentTimeMillis();
    PressureSnapshot pressure = capturePressure();
    boolean severe = pressure.severe();

    if (!engaged) {
      if (severe) {
        engaged = true;
        engagedSinceMS = now;
        activeIncidentId = UUID.randomUUID().toString();
        recordEngagement(now, pressure);
        if (verboseTransitions) {
          React.warn("Trinity incident mode engaged: cross-plugin coordinated mitigation active.");
        }
        coordinateMitigation(now);
      }
      return;
    }

    coordinateMitigation(now);
    if (severe) {
      return;
    }

    if (now - engagedSinceMS < minimumEngageMS) {
      return;
    }

    engaged = false;
    recordResolution(now, "RESOLVED", "Iris, Adapt, and server pressure recovered below the coordinated trigger thresholds.");
    activeIncidentId = null;
    if (verboseTransitions) {
      React.info("Trinity incident mode recovered.");
    }
  }

  private PressureSnapshot capturePressure() {
    double tickMS = sample(SamplerTickTime.ID);
    double irisQueue = metricOr("iris", IntegrationMetricSchema.IRIS_PREGEN_QUEUE, -1D);
    double adaptSessionLoad = metricOr("adapt", IntegrationMetricSchema.ADAPT_SESSION_LOAD, -1D);
    double adaptAbilityTimingBudget = metricOr(
        "adapt",
        IntegrationMetricSchema.ADAPT_ABILITY_TIMING_BUDGET,
        -1D
    );

    boolean irisPressure = irisQueue >= 0D && irisQueue >= enterIrisQueue;
    boolean adaptPressure = hasAdaptPressure(
        adaptSessionLoad,
        adaptAbilityTimingBudget,
        enterAdaptSessionLoad,
        enterAdaptAbilityTimingBudgetPercent
    );
    boolean externalPressure = irisPressure || adaptPressure;
    SamplerIncidentScore.IncidentScoreSnapshot incident = incidentScoreSnapshot();
    boolean serverPressure = hasServerPressure(tickMS, incident.score(), enterTickMS, enterIncidentScore);
    return new PressureSnapshot(
        tickMS,
        incident,
        irisQueue,
        adaptSessionLoad,
        adaptAbilityTimingBudget,
        externalPressure && serverPressure
    );
  }

  static boolean hasAdaptPressure(double sessionLoad,
                                  double abilityTimingBudget,
                                  double sessionLoadThreshold,
                                  double timingBudgetThreshold) {
    return (sessionLoad >= 0D && sessionLoad >= sessionLoadThreshold)
        || (abilityTimingBudget >= 0D && abilityTimingBudget >= timingBudgetThreshold);
  }

  static boolean hasServerPressure(double tickMS,
                                   double incidentScore,
                                   double tickThreshold,
                                   double incidentThreshold) {
    return tickMS >= tickThreshold || incidentScore >= incidentThreshold;
  }

  private void coordinateMitigation(long now) {
    J.s(this::ensureMitigationFeaturesActive);

    if (now - lastPlaybookAtMS < playbookCooldownMS) {
      return;
    }

    Action<?> playbook = React.action(ActionIncidentPlaybook.ID);
    if (playbook == null) {
      return;
    }

    ActionTicket<?> ticket = playbook.create();
    String incidentId = activeIncidentId;
    recordPlaybook(incidentId, now, "QUEUED", "The coordinated mitigation playbook was queued.");
    ticket.onTerminal(completed -> recordPlaybook(
        incidentId,
        System.currentTimeMillis(),
        completed.isFailed() ? "FAILED" : "COMPLETED",
        completed.isFailed()
            ? "The coordinated mitigation playbook failed: " + completed.getFailure().getMessage()
            : "The coordinated mitigation playbook queued " + completed.getCount() + " child actions."
    ));
    ticket.queue();
    lastPlaybookAtMS = now;
  }

  private void recordEngagement(long now, PressureSnapshot pressure) {
    IncidentController controller = React.controller(IncidentController.class);
    if (controller == null || activeIncidentId == null) {
      return;
    }
    List<IncidentEvidence> evidence = new ArrayList<>(pressure.incident().evidence());
    evidence.add(evidence(
        SamplerTickTime.ID,
        "Tick Time",
        pressure.tickMS(),
        pressure.tickMS() >= 0D,
        "ms",
        enterTickMS
    ));
    evidence.add(evidence(
        IntegrationMetricSchema.IRIS_PREGEN_QUEUE,
        "Iris Pregenerator Queue",
        pressure.irisQueue(),
        pressure.irisQueue() >= 0D,
        "chunks",
        enterIrisQueue
    ));
    evidence.add(evidence(
        IntegrationMetricSchema.ADAPT_SESSION_LOAD,
        "Adapt Session Load",
        pressure.adaptSessionLoad(),
        pressure.adaptSessionLoad() >= 0D,
        "%",
        enterAdaptSessionLoad
    ));
    evidence.add(evidence(
        IntegrationMetricSchema.ADAPT_ABILITY_TIMING_BUDGET,
        "Adapt Ability Timing Budget",
        pressure.adaptAbilityTimingBudget(),
        pressure.adaptAbilityTimingBudget() >= 0D,
        "%",
        enterAdaptAbilityTimingBudgetPercent
    ));
    String externalCause = pressure.irisQueue() >= enterIrisQueue
        ? "Iris pregeneration queue pressure"
        : pressure.adaptSessionLoad() >= enterAdaptSessionLoad
        ? "Adapt session load"
        : "Adapt ability timing budget pressure";
    controller.record(new IncidentRecord(
        UUID.randomUUID().toString(),
        activeIncidentId,
        "TRINITY_PRESSURE",
        "STARTED",
        "CRITICAL",
        now,
        now,
        ID,
        "Trinity incident coordination engaged",
        "React activated coordinated React, Iris, and Adapt mitigation paths.",
        externalCause + " coincided with elevated server pressure.",
        null,
        evidence,
        List.of(new IncidentAction(
            "trinity-guards",
            "Cross-plugin guardrails",
            "ACTIVE",
            "Incident, quarantine, Iris terrain, and Adapt runtime guards were requested.",
            now
        )),
        Map.of()
    ));
  }

  private void recordResolution(long now, String phase, String summary) {
    IncidentController controller = React.controller(IncidentController.class);
    if (controller == null || activeIncidentId == null) {
      return;
    }
    controller.record(new IncidentRecord(
        UUID.randomUUID().toString(),
        activeIncidentId,
        "TRINITY_PRESSURE",
        phase,
        "INFO",
        now,
        engagedSinceMS,
        ID,
        "Trinity incident coordination released",
        summary,
        "The coordinated external and server pressure trigger is no longer present.",
        null,
        incidentScoreSnapshot().evidence(),
        List.of(new IncidentAction(
            "trinity-guards",
            "Cross-plugin guardrails",
            "COMPLETED",
            "The coordinated incident window ended.",
            now
        )),
        Map.of()
    ));
  }

  private void recordPlaybook(String incidentId, long now, String status, String detail) {
    IncidentController controller = React.controller(IncidentController.class);
    if (controller == null || incidentId == null) {
      return;
    }
    controller.record(new IncidentRecord(
        UUID.randomUUID().toString(),
        incidentId,
        "TRINITY_PRESSURE",
        "ACTION",
        "INFO",
        now,
        engagedSinceMS,
        ID,
        "Incident playbook " + status.toLowerCase(Locale.ROOT),
        detail,
        "The playbook was requested by the active Trinity incident.",
        null,
        List.of(),
        List.of(new IncidentAction(
            ActionIncidentPlaybook.ID,
            "Incident mitigation playbook",
            status,
            detail,
            now
        )),
        Map.of()
    ));
  }

  private SamplerIncidentScore.IncidentScoreSnapshot incidentScoreSnapshot() {
    SamplerIncidentScore sampler = React.sampler(SamplerIncidentScore.class);
    if (sampler == null) {
      return SamplerIncidentScore.IncidentScoreSnapshot.empty();
    }
    sampler.sample();
    return sampler.snapshot();
  }

  private IncidentEvidence evidence(
      String id,
      String label,
      double value,
      boolean available,
      String suffix,
      double threshold
  ) {
    double maximum = Math.max(threshold + 1D, threshold * 2D);
    double pressure = available
        ? Math.max(0D, Math.min(1D, (value - threshold) / (maximum - threshold)))
        : 0D;
    return new IncidentEvidence(
        id,
        label,
        available,
        available ? value : 0D,
        available ? String.format(Locale.ROOT, "%.1f %s", value, suffix) : "Unavailable",
        pressure,
        0D,
        0D,
        threshold,
        maximum
    );
  }

  private void ensureMitigationFeaturesActive() {
    FeatureController controller = React.controller(FeatureController.class);
    if (controller == null || controller.getFeatures() == null || controller.getActiveFeatures() == null) {
      return;
    }

    activateIfAllowed(controller, FeatureIncidentMode.ID);
    activateIfAllowed(controller, FeatureChunkQuarantine.ID);
    activateIfAllowed(controller, FeatureIrisTerrainSurgeGuard.ID);
    activateIfAllowed(controller, FeatureAdaptRuntimeSurgeGuard.ID);
  }

  private void activateIfAllowed(FeatureController controller, String featureId) {
    Feature feature = controller.getFeatures().get(featureId);
    if (feature == null || !feature.isEnabled()) {
      return;
    }

    if (controller.getActiveFeatures().containsKey(featureId)) {
      return;
    }

    controller.activateFeature(feature);
  }

  private double metricOr(String pluginId, String key, double fallback) {
    IntegrationController integration = React.controller(IntegrationController.class);
    if (integration == null || integration.getRemoteSamplerBridge() == null) {
      return fallback;
    }

    return integration.getRemoteSamplerBridge().valueOr(pluginId, key, fallback);
  }

  private record PressureSnapshot(
      double tickMS,
      SamplerIncidentScore.IncidentScoreSnapshot incident,
      double irisQueue,
      double adaptSessionLoad,
      double adaptAbilityTimingBudget,
      boolean severe
  ) {
  }
}
