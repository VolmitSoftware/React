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

import art.arcane.react.api.tweak.Tweak;
import art.arcane.react.platform.ComponentHost;
import art.arcane.react.platform.Subscription;
import art.arcane.react.util.plugin.IController;
import art.arcane.react.util.project.registry.Registry;
import lombok.Data;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

@Data
public class TweakController implements IController {
  private transient final AtomicBoolean stopFailed = new AtomicBoolean(false);
  private transient Registry<Tweak> tweaks;
  private transient Map<String, Tweak> activeTweaks;
  private transient Map<String, Subscription> tickedTweaks;

  private Tweak unknown;
  private transient volatile boolean stopping;

  @Override
  public String getId() {
    return "tweak";
  }

  @Override
  public String getName() {
    return "Tweaks";
  }

  public Tweak getTweak(String id) {
    return tweaks.get(id);
  }

  public String describeControllerLoadForSlowTick() {
    Map<String, Tweak> active = activeTweaks;
    Map<String, Subscription> scheduled = tickedTweaks;
    int activeCount = active == null ? 0 : active.size();
    int scheduledCount = scheduled == null ? 0 : scheduled.size();
    Collection<String> ids = scheduled == null ? List.of() : scheduled.keySet();
    String preview = previewIds(ids, 5);
    return "activeTweaks=" + activeCount + ", scheduledTicks=" + scheduledCount + ", scheduledIds=" + preview;
  }

  public void activateTweak(Tweak tweak) {
    ComponentHost host = ReactComponents.host().orElse(null);
    if (tweak == null
        || host == null
        || activeTweaks == null
        || tickedTweaks == null) {
      return;
    }

    String id;
    try {
      if (!shouldActivateTweak(tweak)) {
        return;
      }
      id = tweak.getId();
    } catch (Throwable failure) {
      reportTweakLifecycleFailure("activate", tweak.getClass().getSimpleName(), failure);
      return;
    }

    if (id == null || id.isBlank() || activeTweaks.containsKey(id)) {
      return;
    }

    Subscription scheduled = null;
    boolean listenerRegistrationAttempted = false;
    try {
      tweak.onActivate();
      listenerRegistrationAttempted = true;
      host.attach(tweak);

      if (tweak.getTickInterval() > 0) {
        scheduled = host.schedule(tweak, "tweak-" + id, tweak.getTickInterval(), tweak::onTick);
        tickedTweaks.put(id, scheduled);
      }

      activeTweaks.put(id, tweak);
    } catch (Throwable failure) {
      rollbackTweakActivation(host, tweak, id, scheduled, listenerRegistrationAttempted, failure);
      return;
    }

    ReactComponents.verbose("Activated Tweak: " + id);
  }

  public void deactivateTweak(Tweak tweak) {
    if (tweak == null || activeTweaks == null || tickedTweaks == null) {
      return;
    }

    String id;
    try {
      id = tweak.getId();
    } catch (Throwable failure) {
      reportTweakLifecycleFailure("deactivate", tweak.getClass().getSimpleName(), failure);
      return;
    }

    Tweak removed = activeTweaks.remove(id);
    Subscription scheduled = tickedTweaks.remove(id);
    if (removed == null && scheduled == null) {
      return;
    }

    Tweak component = removed == null ? tweak : removed;
    Throwable failure = null;
    if (scheduled != null) {
      try {
        scheduled.cancel();
      } catch (Throwable cleanupFailure) {
        failure = appendFailure(failure, cleanupFailure);
      }
    }

    ComponentHost host = ReactComponents.host().orElse(null);
    if (host != null) {
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
      reportTweakLifecycleFailure("deactivate", id, failure);
      return;
    }

    ReactComponents.verbose("Deactivated Tweak: " + id);
  }

  @Override
  public void start() {
    activeTweaks = new HashMap<>();
    tickedTweaks = new HashMap<>();
    tweaks = new Registry<>(Tweak.class, "art.arcane.react.content.tweak");
    ReactComponents.bind(this);
  }

  public void postStart() {
    ReactComponents.verbose("Registered " + tweaks.size() + " Tweaks");

    for (String i : tweaks.ids()) {
      try {
        Tweak tweak = tweaks.get(i);
        if (tweak != null) {
          activateTweak(tweak);
        }
      } catch (Throwable failure) {
        reportTweakLifecycleFailure("start", i, failure);
      }
    }

    ReactComponents.verbose("Activated " + activeTweaks.size() + " Tweaks");
  }

  @Override
  public void stop() {
    stopping = true;
    stopFailed.set(false);
    try {
      if (activeTweaks != null) {
        for (Tweak tweak : new ArrayList<>(activeTweaks.values())) {
          try {
            deactivateTweak(tweak);
          } catch (Throwable failure) {
            reportTweakLifecycleFailure("stop", tweak == null ? "unknown" : tweak.getClass().getSimpleName(), failure);
          }
        }
      }
    } finally {
      stopping = false;
      ReactComponents.unbind(this);
    }
    if (stopFailed.get()) {
      throw new IllegalStateException("One or more tweaks failed to stop cleanly");
    }
  }

  public void reconcileRuntimeMode() {
    if (tweaks == null || activeTweaks == null) {
      return;
    }

    for (Tweak tweak : new ArrayList<>(activeTweaks.values())) {
      try {
        if (!shouldActivateTweak(tweak)) {
          deactivateTweak(tweak);
        }
      } catch (Throwable failure) {
        reportTweakLifecycleFailure("reconcile", tweak.getClass().getSimpleName(), failure);
      }
    }

    for (Tweak tweak : tweaks.all()) {
      try {
        if (shouldActivateTweak(tweak) && !activeTweaks.containsKey(tweak.getId())) {
          activateTweak(tweak);
        }
      } catch (Throwable failure) {
        reportTweakLifecycleFailure("reconcile", tweak == null ? "unknown" : tweak.getClass().getSimpleName(), failure);
      }
    }
  }

  private boolean shouldActivateTweak(Tweak tweak) {
    ComponentHost host = ReactComponents.host().orElse(null);
    return tweak != null
        && host != null
        && host.isEnabled()
        && !host.isMonitoringOnly()
        && tweak.isEnabled();
  }

  private String previewIds(Collection<String> ids, int limit) {
    if (ids == null || ids.isEmpty()) {
      return "none";
    }

    List<String> ordered = new ArrayList<>(ids);
    Collections.sort(ordered);
    int safeLimit = Math.max(1, limit);
    int shown = Math.min(safeLimit, ordered.size());
    List<String> shownIds = new ArrayList<>(shown);
    for (int i = 0; i < shown; i++) {
      shownIds.add(ordered.get(i));
    }

    int remaining = ordered.size() - shown;
    if (remaining <= 0) {
      return String.join(", ", shownIds);
    }

    return String.join(", ", shownIds) + ", +" + remaining + " more";
  }

  private void rollbackTweakActivation(
      ComponentHost host,
      Tweak tweak,
      String id,
      Subscription scheduled,
      boolean listenerRegistrationAttempted,
      Throwable failure
  ) {
    activeTweaks.remove(id, tweak);
    Subscription registered = tickedTweaks.remove(id);
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
        host.detach(tweak);
      } catch (Throwable cleanupFailure) {
        failure = appendFailure(failure, cleanupFailure);
      }
    }

    try {
      tweak.onDeactivate();
    } catch (Throwable cleanupFailure) {
      failure = appendFailure(failure, cleanupFailure);
    }

    reportTweakLifecycleFailure("activate", id, failure);
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

  private void reportTweakLifecycleFailure(String operation, String id, Throwable failure) {
    if (stopping) {
      stopFailed.set(true);
    }
    ReactComponents.reportError("Failed to " + operation + " tweak " + id + ".", failure);
  }
}
