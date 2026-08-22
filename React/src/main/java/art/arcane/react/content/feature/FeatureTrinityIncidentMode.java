package art.arcane.react.content.feature;

import art.arcane.react.React;
import art.arcane.react.api.action.Action;
import art.arcane.react.api.feature.Feature;
import art.arcane.react.api.feature.ReactCapabilityFeature;
import art.arcane.react.content.action.ActionIncidentPlaybook;
import art.arcane.react.content.sampler.SamplerIncidentScore;
import art.arcane.react.content.sampler.SamplerTickTime;
import art.arcane.react.core.controller.FeatureController;
import art.arcane.react.core.controller.IntegrationController;
import art.arcane.react.util.common.scheduling.J;
import art.arcane.volmlib.integration.IntegrationMetricSchema;

import java.util.Set;

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
  }

  @Override
  public void onDeactivate() {
    engaged = false;
  }

  @Override
  public int getTickInterval() {
    return tickIntervalMS;
  }

  @Override
  public void onTick() {
    long now = System.currentTimeMillis();
    boolean severe = isSevereIncident();

    if (!engaged) {
      if (severe) {
        engaged = true;
        engagedSinceMS = now;
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
    if (verboseTransitions) {
      React.info("Trinity incident mode recovered.");
    }
  }

  private boolean isSevereIncident() {
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
    if (!externalPressure) {
      return false;
    }
    double incidentScore = sample(SamplerIncidentScore.ID);
    return hasServerPressure(tickMS, incidentScore, enterTickMS, enterIncidentScore);
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

    playbook.create().queue();
    lastPlaybookAtMS = now;
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
}
