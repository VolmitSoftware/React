package art.arcane.react.api.test.checks;

import art.arcane.react.React;
import art.arcane.react.api.feature.Feature;
import art.arcane.react.api.test.ReactAsyncSubsystemCheck;
import art.arcane.react.api.test.TestReport;
import art.arcane.react.content.feature.FeatureWorldSaveStaggering;
import art.arcane.react.core.controller.FeatureController;
import art.arcane.react.util.common.scheduling.J;
import art.arcane.react.util.project.registry.Registry;
import org.bukkit.Bukkit;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class FeatureLifecycleCheck implements ReactAsyncSubsystemCheck {
  private static final int SETTLE_TICKS = 1;

  @Override
  public String subsystem() {
    return "features";
  }

  @Override
  public void run(TestReport report, Runnable onDone) {
    FeatureController controller = React.controller(FeatureController.class);
    if (controller == null) {
      report.skip("features", "lifecycle", "FeatureController unavailable");
      onDone.run();
      return;
    }

    Registry<Feature> registry = controller.getFeatures();
    if (registry == null) {
      report.skip("features", "lifecycle", "feature registry not initialized");
      onDone.run();
      return;
    }

    controller.setReconcilePaused(true);
    Runnable done = () -> {
      controller.setReconcilePaused(false);
      onDone.run();
    };

    Map<UUID, Boolean> baseline = new LinkedHashMap<UUID, Boolean>();
    for (World world : Bukkit.getWorlds()) {
      baseline.put(world.getUID(), world.isAutoSave());
    }

    List<Feature> enabled = new ArrayList<Feature>();
    Map<String, Boolean> originalActive = new HashMap<String, Boolean>();
    for (Feature feature : registry.all()) {
      if (feature == null || !feature.isEnabled()) {
        continue;
      }
      enabled.add(feature);
      originalActive.put(feature.getId(), isActive(controller, feature.getId()));
    }

    if (enabled.isEmpty()) {
      report.info("features", "lifecycle", "no enabled features to cycle");
      assertWorldAutoSave(report, baseline);
      done.run();
      return;
    }

    cycleFeature(report, controller, enabled, originalActive, baseline, 0, done);
  }

  private void cycleFeature(TestReport report, FeatureController controller, List<Feature> features, Map<String, Boolean> originalActive, Map<UUID, Boolean> baseline, int index, Runnable onDone) {
    if (index >= features.size()) {
      assertWorldAutoSave(report, baseline);
      onDone.run();
      return;
    }

    Feature feature = features.get(index);
    String id = feature.getId();
    Runnable advance = () -> cycleFeature(report, controller, features, originalActive, baseline, index + 1, onDone);

    try {
      controller.deactivateFeature(feature);
    } catch (Throwable e) {
      recordFeatureFailure(report, feature, "deactivate", e);
      restoreFeature(controller, feature, originalActive);
      advance.run();
      return;
    }

    J.s(() -> {
      boolean removedAfterDeactivate = !isActive(controller, id);
      try {
        controller.activateFeature(feature);
      } catch (Throwable e) {
        recordFeatureFailure(report, feature, "activate", e);
        restoreFeature(controller, feature, originalActive);
        advance.run();
        return;
      }

      J.s(() -> {
        boolean addedAfterActivate = isActive(controller, id);
        try {
          controller.deactivateFeature(feature);
        } catch (Throwable e) {
          recordFeatureFailure(report, feature, "redeactivate", e);
          restoreFeature(controller, feature, originalActive);
          advance.run();
          return;
        }

        J.s(() -> {
          boolean removedAfterRedeactivate = !isActive(controller, id);
          boolean mapConsistent = removedAfterDeactivate && addedAfterActivate && removedAfterRedeactivate;
          if (mapConsistent) {
            report.pass("features", id, "deactivate/activate/deactivate cycle clean; activeFeatures tracked every transition");
          } else {
            report.fail("features", id, "activeFeatures map inconsistent: removed1=" + removedAfterDeactivate + " added=" + addedAfterActivate + " removed2=" + removedAfterRedeactivate);
          }
          restoreFeature(controller, feature, originalActive);
          advance.run();
        }, SETTLE_TICKS);
      }, SETTLE_TICKS);
    }, SETTLE_TICKS);
  }

  private void assertWorldAutoSave(TestReport report, Map<UUID, Boolean> baseline) {
    if (baseline.isEmpty()) {
      report.skip("features", "autosave-restore-guard", "no worlds loaded to verify autosave restoration");
      return;
    }

    int total = 0;
    int matched = 0;
    for (World world : Bukkit.getWorlds()) {
      Boolean was = baseline.get(world.getUID());
      if (was == null) {
        continue;
      }
      total++;
      if (world.isAutoSave() == was.booleanValue()) {
        matched++;
      }
    }

    if (total > 0 && matched == total) {
      report.pass("features", "autosave-restore-guard", "all " + total + " worlds returned to baseline isAutoSave after full feature lifecycle cycling");
    } else if (worldSaveFeatureActive()) {
      report.warn("features", "autosave-restore-guard", "autosave differs from baseline (" + matched + "/" + total + ") while world-save-staggering is active, which legitimately toggles autosave");
    } else {
      report.fail("features", "autosave-restore-guard", "autosave drift after cycling: " + matched + "/" + total + " worlds match baseline (data-loss guard)");
    }
  }

  private boolean worldSaveFeatureActive() {
    FeatureController controller = React.controller(FeatureController.class);
    if (controller == null || controller.getActiveFeatures() == null) {
      return false;
    }
    return controller.getActiveFeatures().containsKey(FeatureWorldSaveStaggering.ID);
  }

  private void restoreFeature(FeatureController controller, Feature feature, Map<String, Boolean> originalActive) {
    try {
      boolean shouldBeActive = Boolean.TRUE.equals(originalActive.get(feature.getId()));
      boolean currentlyActive = isActive(controller, feature.getId());
      if (shouldBeActive && !currentlyActive) {
        controller.activateFeature(feature);
      } else if (!shouldBeActive && currentlyActive) {
        controller.deactivateFeature(feature);
      }
    } catch (Throwable e) {
      React.reportError(e);
    }
  }

  private void recordFeatureFailure(TestReport report, Feature feature, String phase, Throwable e) {
    React.reportError(e);
    report.fail("features", feature.getId(), phase + " threw " + e.getClass().getSimpleName() + (e.getMessage() == null ? "" : ": " + e.getMessage()));
  }

  private boolean isActive(FeatureController controller, String id) {
    Map<String, Feature> active = controller.getActiveFeatures();
    return active != null && active.containsKey(id);
  }
}
