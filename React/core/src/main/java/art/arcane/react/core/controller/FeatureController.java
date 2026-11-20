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

import art.arcane.react.api.feature.CapabilityGatedFeature;
import art.arcane.react.api.feature.Feature;
import art.arcane.react.api.feature.FeatureIntegrity;
import art.arcane.react.api.rendering.ReactRenderer;
import art.arcane.react.content.feature.FeatureUnknown;
import art.arcane.react.platform.ComponentHost;
import art.arcane.react.platform.Subscription;
import art.arcane.react.util.common.scheduling.J;
import art.arcane.react.util.plugin.IController;
import art.arcane.react.util.project.registry.Registry;
import lombok.Data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

@Data
public class FeatureController implements IController {
  private static final long CONTROLLER_TICK_MS = 50L;
  private static final long GATE_RECONCILE_INTERVAL_MS = 2000L;
  private transient final AtomicLong gateReconcileQueuedGeneration = new AtomicLong(-1L);
  private transient final AtomicLong lifecycleGeneration = new AtomicLong();
  private transient final AtomicBoolean stopFailed = new AtomicBoolean(false);
  private transient Registry<Feature> features;
  private transient Map<String, Feature> activeFeatures;
  private transient Map<String, Subscription> tickedFeatures;
  private transient Subscription controllerTicket = Subscription.NONE;
  private transient long lastGateReconcileMS;
  private transient volatile boolean reconcilePaused;
  private transient volatile boolean stopped;
  private transient volatile boolean stopping;

  private Feature unknown;

  @Override
  public String getId() {
    return "feature";
  }

  public void onTick() {
    if (stopped || stopping || !ReactComponents.isRunning()) {
      return;
    }

    if (reconcilePaused) {
      return;
    }

    if (features != null) {
      for (Feature feature : features.all()) {
        if (feature instanceof FeatureIntegrity listener) {
          try {
            listener.onIntegrityTick();
          } catch (Throwable failure) {
            reportFeatureLifecycleFailure("reconcile integrity", feature.getId(), failure);
          }
        }
      }
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

    ComponentHost host = ReactComponents.host().orElse(null);
    if (host == null) {
      return;
    }

    Subscription scheduled = null;
    boolean listenerRegistrationAttempted = false;
    try {
      feature.onActivate();
      if (!(feature instanceof FeatureIntegrity)) {
        listenerRegistrationAttempted = true;
        host.attach(feature);
      }

      if (feature.getTickInterval() > 0) {
        scheduled = host.schedule(feature, "feature-" + id, feature.getTickInterval(), feature::onTick);
        tickedFeatures.put(id, scheduled);
      }

      activeFeatures.put(id, feature);
    } catch (Throwable failure) {
      rollbackFeatureActivation(host, feature, id, scheduled, listenerRegistrationAttempted, failure);
      return;
    }

    ReactComponents.verbose("Activated Feature: " + id);
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
    Subscription scheduled = tickedFeatures.remove(id);
    if (removed == null && scheduled == null) {
      return;
    }

    Feature component = removed == null ? feature : removed;
    Throwable failure = null;
    if (scheduled != null) {
      try {
        scheduled.cancel();
      } catch (Throwable cleanupFailure) {
        failure = appendFailure(failure, cleanupFailure);
      }
    }

    ComponentHost host = ReactComponents.host().orElse(null);
    if (host != null && !(component instanceof FeatureIntegrity)) {
      try {
        host.detach(component);
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

    ReactComponents.verbose("Deactivated Feature: " + id);
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
    ReactComponents.bind(this);
    controllerTicket = ReactComponents.host()
        .map(host -> host.schedule(this, "feature", CONTROLLER_TICK_MS, this::onTick))
        .orElse(Subscription.NONE);
  }

  public synchronized void postStart() {
    ReactComponents.verbose("Registered " + features.size() + " Features");
    ComponentHost host = ReactComponents.host().orElse(null);

    for (String i : features.ids()) {
      try {
        Feature f = features.get(i);
        if (f == null) {
          continue;
        }

        if (f instanceof FeatureIntegrity integrity && host != null) {
          host.attach(f);
          integrity.onAttach();
        }

        if (shouldActivateFeature(f)) {
          activateFeature(f);
        } else if (f instanceof CapabilityGatedFeature gated) {
          ReactComponents.verbose("Skipped gated feature " + f.getId() + " requires=" + gated.requirementLabel());
        }
      } catch (Throwable failure) {
        reportFeatureLifecycleFailure("start", i, failure);
      }
    }

    ReactComponents.verbose("Activated " + activeFeatures.size() + " Features");
  }

  @Override
  public synchronized void stop() {
    stopping = true;
    stopped = true;
    lifecycleGeneration.incrementAndGet();
    gateReconcileQueuedGeneration.set(-1L);
    stopFailed.set(false);
    controllerTicket.cancel();
    controllerTicket = Subscription.NONE;
    ComponentHost host = ReactComponents.host().orElse(null);
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

      if (features != null && host != null) {
        for (Feature feature : features.all()) {
          if (!(feature instanceof FeatureIntegrity integrity)) {
            continue;
          }

          try {
            integrity.onDetach();
            host.detach(feature);
          } catch (Throwable failure) {
            reportFeatureLifecycleFailure("stop integrity listener", feature.getClass().getSimpleName(), failure);
          }
        }
      }
    } finally {
      stopping = false;
      ReactComponents.unbind(this);
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
        || reconcilePaused
        || expectedGeneration != lifecycleGeneration.get()
        || !ReactComponents.isRunning()) {
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
        ReactComponents.reportError("Failed to reconcile feature " + feature.getId() + ".", e);
      }
    }
  }

  public boolean shouldActivateFeature(Feature feature) {
    ComponentHost host = ReactComponents.host().orElse(null);
    if (feature == null
        || host == null
        || !host.isEnabled()
        || !feature.isEnabled()
        || !isAllowedByRuntimeMode(host, feature)) {
      return false;
    }

    if (!(feature instanceof CapabilityGatedFeature gated)) {
      return true;
    }

    if (gated.isSecretBundle() && !host.integrationSecretsEnabled()) {
      return false;
    }

    for (String capability : gated.requiredCapabilities()) {
      if (!host.hasCapability(capability)) {
        return false;
      }
    }

    return true;
  }

  private boolean isAllowedByRuntimeMode(ComponentHost host, Feature feature) {
    return !host.isMonitoringOnly() || feature instanceof ReactRenderer;
  }

  private void rollbackFeatureActivation(
      ComponentHost host,
      Feature feature,
      String id,
      Subscription scheduled,
      boolean listenerRegistrationAttempted,
      Throwable failure
  ) {
    activeFeatures.remove(id, feature);
    Subscription registered = tickedFeatures.remove(id);
    Subscription scheduledForCleanup = registered == null ? scheduled : registered;
    if (scheduledForCleanup != null) {
      try {
        scheduledForCleanup.cancel();
      } catch (Throwable cleanupFailure) {
        failure = appendFailure(failure, cleanupFailure);
      }
    }

    if (listenerRegistrationAttempted) {
      try {
        host.detach(feature);
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
    ReactComponents.reportError("Failed to " + operation + " feature " + id + ".", failure);
  }
}
