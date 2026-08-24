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
import art.arcane.react.api.rendering.ReactRenderer;
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
import java.util.concurrent.atomic.AtomicLong;

@EqualsAndHashCode(callSuper = true)
@Data
public class FeatureController extends TickedObject implements IController {
  private static final long GATE_RECONCILE_INTERVAL_MS = 2000L;
  private transient final AtomicLong gateReconcileQueuedGeneration = new AtomicLong(-1L);
  private transient final AtomicLong lifecycleGeneration = new AtomicLong();
  private transient final AtomicBoolean stopFailed = new AtomicBoolean(false);
  private transient Registry<Feature> features;
  private transient Map<String, Feature> activeFeatures;
  private transient Map<String, ReactTickedFeature> tickedFeatures;
  private transient long lastGateReconcileMS;
  private transient volatile boolean reconcilePaused;
  private transient volatile boolean stopped;
  private transient volatile boolean stopping;

  private Feature unknown;

  public FeatureController() {
    super("react", "feature", 50);
  }

  @Override
  public void onTick() {
    if (stopped || stopping || !React.instance.isEnabled() || !React.instance.isReady()) {
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
    long generation = lifecycleGeneration.get();
    if (!gateReconcileQueuedGeneration.compareAndSet(-1L, generation)) {
      return;
    }

    try {
      J.s(() -> {
        try {
          reconcileFeatureGates(generation);
        } finally {
          gateReconcileQueuedGeneration.compareAndSet(generation, -1L);
        }
      });
    } catch (RuntimeException | Error failure) {
      gateReconcileQueuedGeneration.compareAndSet(generation, -1L);
      throw failure;
    }
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

  public synchronized void activateFeature(Feature feature) {
    if (feature == null
        || stopped
        || stopping
        || React.instance == null
        || activeFeatures == null
        || tickedFeatures == null) {
      return;
    }

    String id;
    try {
      if (!shouldActivateFeature(feature)) {
        return;
      }
      id = feature.getId();
    } catch (Throwable failure) {
      reportFeatureLifecycleFailure("activate", feature.getClass().getSimpleName(), failure);
      return;
    }

    if (id == null || id.isBlank() || activeFeatures.containsKey(id)) {
      return;
    }

    ReactTickedFeature scheduled = null;
    boolean listenerRegistrationAttempted = false;
    try {
      feature.onActivate();
      if (feature instanceof Listener listener && !(feature instanceof FeatureIntegrityListener)) {
        listenerRegistrationAttempted = true;
        React.instance.registerListener(listener);
      }

      if (feature.getTickInterval() > 0) {
        scheduled = new ReactTickedFeature(feature);
        tickedFeatures.put(id, scheduled);
      }

      activeFeatures.put(id, feature);
    } catch (Throwable failure) {
      rollbackFeatureActivation(feature, id, scheduled, listenerRegistrationAttempted, failure);
      return;
    }

    React.verbose("Activated Feature: " + id);
  }

  public synchronized void deactivateFeature(Feature feature) {
    if (feature == null || activeFeatures == null || tickedFeatures == null) {
      return;
    }

    String id;
    try {
      id = feature.getId();
    } catch (Throwable failure) {
      reportFeatureLifecycleFailure("deactivate", feature.getClass().getSimpleName(), failure);
      return;
    }

    Feature removed = activeFeatures.remove(id);
    ReactTickedFeature scheduled = tickedFeatures.remove(id);
    if (removed == null && scheduled == null) {
      return;
    }

    Feature component = removed == null ? feature : removed;
    Throwable failure = null;
    if (scheduled != null) {
      try {
        scheduled.unregister();
      } catch (Throwable cleanupFailure) {
        failure = appendFailure(failure, cleanupFailure);
      }
    }

    if (component instanceof Listener listener && !(component instanceof FeatureIntegrityListener)) {
      try {
        React.instance.unregisterListener(listener);
      } catch (Throwable cleanupFailure) {
        failure = appendFailure(failure, cleanupFailure);
      }
    }

    try {
      component.onDeactivate();
    } catch (Throwable cleanupFailure) {
      failure = appendFailure(failure, cleanupFailure);
    }

    if (failure != null) {
      reportFeatureLifecycleFailure("deactivate", id, failure);
      return;
    }

    React.verbose("Deactivated Feature: " + id);
  }

  @Override
  public synchronized void start() {
    // Concurrent: bStats chart callables read this from their own daemon thread on Folia
    activeFeatures = new ConcurrentHashMap<>();
    tickedFeatures = new HashMap<>();
    features = new Registry<>(Feature.class, "art.arcane.react.content.feature");
    lastGateReconcileMS = 0L;
    stopping = false;
    stopped = false;
    reconcilePaused = false;
    lifecycleGeneration.incrementAndGet();
    gateReconcileQueuedGeneration.set(-1L);
  }

  public synchronized void postStart() {
    React.verbose("Registered " + features.size() + " Features");

    for (String i : features.ids()) {
      try {
        Feature f = features.get(i);
        if (f == null) {
          continue;
        }

        if (f instanceof FeatureIntegrityListener listener) {
          React.instance.registerListener(listener);
        }

        if (shouldActivateFeature(f)) {
          activateFeature(f);
        } else if (f instanceof CapabilityGatedFeature gated) {
          React.verbose("Skipped gated feature " + f.getId() + " requires=" + gated.requirementLabel());
        }
      } catch (Throwable failure) {
        reportFeatureLifecycleFailure("start", i, failure);
      }
    }

    React.verbose("Activated " + activeFeatures.size() + " Features");
  }

  @Override
  public synchronized void stop() {
    stopping = true;
    stopped = true;
    lifecycleGeneration.incrementAndGet();
    gateReconcileQueuedGeneration.set(-1L);
    stopFailed.set(false);
    try {
      if (activeFeatures != null) {
        for (Feature feature : new ArrayList<>(activeFeatures.values())) {
          try {
            deactivateFeature(feature);
          } catch (Throwable failure) {
            reportFeatureLifecycleFailure("stop", feature == null ? "unknown" : feature.getClass().getSimpleName(), failure);
          }
        }
      }

      if (features != null) {
        for (Feature feature : features.all()) {
          if (!(feature instanceof FeatureIntegrityListener listener)) {
            continue;
          }

          try {
            React.instance.unregisterListener(listener);
          } catch (Throwable failure) {
            reportFeatureLifecycleFailure("stop integrity listener", feature.getClass().getSimpleName(), failure);
          }
        }
      }
    } finally {
      stopping = false;
    }
    if (stopFailed.get()) {
      throw new IllegalStateException("One or more features failed to stop cleanly");
    }
  }

  public synchronized void reconcileRuntimeMode() {
    reconcileFeatureGates(lifecycleGeneration.get());
  }

  private synchronized void reconcileFeatureGates(long expectedGeneration) {
    if (stopped
        || stopping
        || expectedGeneration != lifecycleGeneration.get()
        || !React.instance.isEnabled()
        || !React.instance.isReady()) {
      return;
    }

    if (features == null || activeFeatures == null) {
      return;
    }

    for (Feature feature : features.all()) {
      if (stopped || stopping || expectedGeneration != lifecycleGeneration.get()) {
        return;
      }

      if (feature == null) {
        continue;
      }

      try {
        boolean active = activeFeatures.containsKey(feature.getId());
        boolean shouldBeActive = shouldActivateFeature(feature);
        if (shouldBeActive && !active) {
          activateFeature(feature);
          continue;
        }

        if (!shouldBeActive && active) {
          deactivateFeature(feature);
        }
      } catch (Throwable e) {
        React.reportError("Failed to reconcile feature " + feature.getId() + ".", e);
      }
    }
  }

  public boolean shouldActivateFeature(Feature feature) {
    if (feature == null
        || React.instance == null
        || !React.instance.isEnabled()
        || !feature.isEnabled()
        || !isAllowedByRuntimeMode(feature)) {
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

  private boolean isAllowedByRuntimeMode(Feature feature) {
    return !React.instance.isMonitoringOnly() || feature instanceof ReactRenderer;
  }

  private void rollbackFeatureActivation(
      Feature feature,
      String id,
      ReactTickedFeature scheduled,
      boolean listenerRegistrationAttempted,
      Throwable failure
  ) {
    activeFeatures.remove(id, feature);
    ReactTickedFeature registered = tickedFeatures.remove(id);
    ReactTickedFeature scheduledForCleanup = registered == null ? scheduled : registered;
    if (scheduledForCleanup != null) {
      try {
        scheduledForCleanup.unregister();
      } catch (Throwable cleanupFailure) {
        failure = appendFailure(failure, cleanupFailure);
      }
    }

    if (listenerRegistrationAttempted && feature instanceof Listener listener) {
      try {
        React.instance.unregisterListener(listener);
      } catch (Throwable cleanupFailure) {
        failure = appendFailure(failure, cleanupFailure);
      }
    }

    try {
      feature.onDeactivate();
    } catch (Throwable cleanupFailure) {
      failure = appendFailure(failure, cleanupFailure);
    }

    reportFeatureLifecycleFailure("activate", id, failure);
  }

  private Throwable appendFailure(Throwable failure, Throwable additionalFailure) {
    if (failure == null) {
      return additionalFailure;
    }
    if (failure != additionalFailure) {
      failure.addSuppressed(additionalFailure);
    }
    return failure;
  }

  private void reportFeatureLifecycleFailure(String operation, String id, Throwable failure) {
    if (stopping) {
      stopFailed.set(true);
    }
    React.reportError("Failed to " + operation + " feature " + id + ".", failure);
  }
}
