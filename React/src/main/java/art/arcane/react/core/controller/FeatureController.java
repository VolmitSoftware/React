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

package art.arcane.react.core.controller;

import art.arcane.react.React;
import art.arcane.react.api.feature.CapabilityGatedFeature;
import art.arcane.react.api.feature.Feature;
import art.arcane.react.api.feature.FeatureIntegrityListener;
import art.arcane.react.api.feature.ReactTickedFeature;
import art.arcane.react.content.feature.FeatureUnknown;
import art.arcane.react.core.integration.IntegrationCapabilitySupport;
import art.arcane.react.model.ReactConfiguration;
import art.arcane.react.util.common.scheduling.J;
import art.arcane.react.util.common.scheduling.TickedObject;
import art.arcane.react.util.plugin.IController;
import art.arcane.react.util.project.registry.Registry;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.bukkit.event.Listener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

@EqualsAndHashCode(callSuper = true)
@Data
public class FeatureController extends TickedObject implements IController {
  private static final long GATE_RECONCILE_INTERVAL_MS = 2000L;
  private transient final AtomicBoolean gateReconcileQueued = new AtomicBoolean(false);
  private transient Registry<Feature> features;
  private transient Map<String, Feature> activeFeatures;
  private transient Map<String, ReactTickedFeature> tickedFeatures;
  private transient long lastGateReconcileMS;
  private transient volatile boolean reconcilePaused;

  private Feature unknown;

  public FeatureController() {
    super("react", "feature", 50);
  }

  @Override
  public void onTick() {
    if (!React.instance.isEnabled() || !React.instance.isReady()) {
      return;
    }

    if (reconcilePaused) {
      return;
    }

    long now = System.currentTimeMillis();
    if (now - lastGateReconcileMS < GATE_RECONCILE_INTERVAL_MS) {
      return;
    }

    lastGateReconcileMS = now;
    if (!gateReconcileQueued.compareAndSet(false, true)) {
      return;
    }

    J.s(() -> {
      try {
        if (!React.instance.isEnabled() || !React.instance.isReady()) {
          return;
        }
        reconcileFeatureGates();
      } finally {
        gateReconcileQueued.set(false);
      }
    });
  }

  @Override
  public String getName() {
    return "Feature";
  }

  public Feature getFeature(String id) {
    Feature s = features.get(id);

    s = s == null ? unknown : s;

    if (s == null) {
      s = new FeatureUnknown();
    }

    return s;
  }

  public void activateFeature(Feature feature) {
    if (feature == null || !React.instance.isEnabled()) {
      return;
    }

    if (!activeFeatures.containsKey(feature.getId())) {
      activeFeatures.put(feature.getId(), feature);
      feature.onActivate();
      if (feature instanceof Listener l) {
        React.instance.registerListener(l);
      }

      if (feature.getTickInterval() > 0) {
        tickedFeatures.put(feature.getId(), new ReactTickedFeature(feature));
      }

      React.verbose("Activated Feature: " + feature.getName());
    }
  }

  public void deactivateFeature(Feature feature) {
    if (feature instanceof Listener l && !(feature instanceof FeatureIntegrityListener)) {
      React.instance.unregisterListener(l);
    }
    activeFeatures.remove(feature.getId());
    ReactTickedFeature t = tickedFeatures.remove(feature.getId());

    if (t != null) {
      t.unregister();
    }
    feature.onDeactivate()
    ;
    React.verbose("Deactivated Feature: " + feature.getName());
  }

  @Override
  public void start() {
    // Concurrent: bStats chart callables read this from their own daemon thread on Folia
    activeFeatures = new ConcurrentHashMap<>();
    tickedFeatures = new HashMap<>();
    features = new Registry<>(Feature.class, "art.arcane.react.content.feature");
    lastGateReconcileMS = 0L;
  }

  public void postStart() {
    React.info("Registered " + features.size() + " Features");

    for (String i : features.ids()) {
      Feature f = features.get(i);

      if (f instanceof FeatureIntegrityListener listener) {
        React.instance.registerListener(listener);
      }

      if (shouldActivateFeature(f)) {
        activateFeature(f);
      } else if (f instanceof CapabilityGatedFeature gated) {
        React.verbose("Skipped gated feature " + f.getId() + " requires=" + gated.requirementLabel());
      }
    }

    React.info("Activated " + activeFeatures.size() + " Features");
  }

  @Override
  public void stop() {
    new ArrayList<>(activeFeatures.values()).forEach(this::deactivateFeature);
    for (Feature feature : features.all()) {
      if (feature instanceof FeatureIntegrityListener listener) {
        React.instance.unregisterListener(listener);
      }
    }
  }

  private void reconcileFeatureGates() {
    if (!React.instance.isEnabled() || !React.instance.isReady()) {
      return;
    }

    if (features == null || activeFeatures == null) {
      return;
    }

    for (Feature feature : features.all()) {
      if (feature == null) {
        continue;
      }

      boolean active = activeFeatures.containsKey(feature.getId());
      boolean shouldBeActive = shouldActivateFeature(feature);
      if (shouldBeActive && !active) {
        activateFeature(feature);
        continue;
      }

      if (!shouldBeActive && active) {
        deactivateFeature(feature);
      }
    }
  }

  private boolean shouldActivateFeature(Feature feature) {
    if (feature == null || !feature.isEnabled()) {
      return false;
    }

    if (!(feature instanceof CapabilityGatedFeature gated)) {
      return true;
    }

    if (gated.isSecretBundle() && !ReactConfiguration.get().isIntegrationSecretsEnabled()) {
      return false;
    }

    IntegrationController integration = React.controller(IntegrationController.class);
    for (String capability : gated.requiredCapabilities()) {
      if (!IntegrationCapabilitySupport.hasCapability(integration, capability)) {
        return false;
      }
    }

    return true;
  }
}
