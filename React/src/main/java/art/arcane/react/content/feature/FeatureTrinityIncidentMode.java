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
import art.arcane.react.util.scheduling.J;
import art.arcane.volmlib.integration.IntegrationMetricSchema;

import java.util.Set;

public class FeatureTrinityIncidentMode extends ReactCapabilityFeature {
    public static final String ID = "feature-trinity-incident-mode";

    private int tickIntervalMS = 1000;
    private double enterIncidentScore = 62D;
    private double enterTickMS = 62D;
    private double enterIrisQueue = 340D;
    private double enterAdaptSessionLoad = 72D;
    private double enterAdaptAbilityOps = 280D;
    private int minimumEngageMS = 12000;
    private int playbookCooldownMS = 20000;
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
        double incidentScore = sample(SamplerIncidentScore.ID);
        double tickMS = sample(SamplerTickTime.ID);
        double irisQueue = metricOr("iris", IntegrationMetricSchema.IRIS_PREGEN_QUEUE, -1D);
        double adaptSessionLoad = metricOr("adapt", IntegrationMetricSchema.ADAPT_SESSION_LOAD, -1D);
        double adaptAbilityOps = metricOr("adapt", IntegrationMetricSchema.ADAPT_ABILITY_OPS, -1D);

        boolean coreIncident = incidentScore >= enterIncidentScore || tickMS >= enterTickMS;
        boolean irisPressure = irisQueue >= 0D && irisQueue >= enterIrisQueue;
        boolean adaptPressure = (adaptSessionLoad >= 0D && adaptSessionLoad >= enterAdaptSessionLoad)
                || (adaptAbilityOps >= 0D && adaptAbilityOps >= enterAdaptAbilityOps);
        return coreIncident && (irisPressure || adaptPressure);
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

    private double sample(String samplerId) {
        var sampler = React.sampler(samplerId);
        return sampler == null ? 0D : sampler.sample();
    }
}
