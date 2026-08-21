package art.arcane.react.api.test.checks;

import art.arcane.react.React;
import art.arcane.react.api.feature.Feature;
import art.arcane.react.api.rendering.ReactRenderer;
import art.arcane.react.api.test.ReactAsyncSubsystemCheck;
import art.arcane.react.api.test.TestReport;
import art.arcane.react.core.controller.FeatureController;
import art.arcane.react.localization.ReactLanguage;
import art.arcane.react.localization.catalog.TestMessages;
import art.arcane.react.util.common.scheduling.J;
import art.arcane.react.util.project.registry.Registry;
import art.arcane.volmlib.util.localization.MessageArgument;
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
      report.skip("features", "lifecycle", ReactLanguage.raw(TestMessages.FEATURE_CONTROLLER_UNAVAILABLE));
      onDone.run();
      return;
    }

    Registry<Feature> registry = controller.getFeatures();
    if (registry == null) {
      report.skip("features", "lifecycle", ReactLanguage.raw(TestMessages.FEATURE_REGISTRY_UNAVAILABLE));
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
      if (React.instance.isMonitoringOnly() && !(feature instanceof ReactRenderer)) {
        continue;
      }
      enabled.add(feature);
      originalActive.put(feature.getId(), isActive(controller, feature.getId()));
    }

    if (enabled.isEmpty()) {
      report.info("features", "lifecycle", ReactLanguage.raw(TestMessages.FEATURE_NONE));
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
            report.pass("features", id, ReactLanguage.raw(TestMessages.FEATURE_CYCLE_PASS));
          } else {
            report.fail(
                "features",
                id,
                ReactLanguage.raw(
                    TestMessages.FEATURE_CYCLE_FAIL,
                    MessageArgument.untrusted("removed_first", removedAfterDeactivate),
                    MessageArgument.untrusted("added", addedAfterActivate),
                    MessageArgument.untrusted("removed_second", removedAfterRedeactivate)
                )
            );
          }
          restoreFeature(controller, feature, originalActive);
          advance.run();
        }, SETTLE_TICKS);
      }, SETTLE_TICKS);
    }, SETTLE_TICKS);
  }

  private void assertWorldAutoSave(TestReport report, Map<UUID, Boolean> baseline) {
    if (baseline.isEmpty()) {
      report.skip("features", "autosave-restore-guard", ReactLanguage.raw(TestMessages.FEATURE_AUTOSAVE_NO_WORLDS));
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
      report.pass(
          "features",
          "autosave-restore-guard",
          ReactLanguage.raw(
              TestMessages.FEATURE_AUTOSAVE_PASS,
              MessageArgument.untrusted("total", total)
          )
      );
      return;
    }

    report.fail(
        "features",
        "autosave-restore-guard",
        ReactLanguage.raw(
            TestMessages.FEATURE_AUTOSAVE_FAIL,
            MessageArgument.untrusted("matched", matched),
            MessageArgument.untrusted("total", total)
        )
    );
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
    String reason = e.getClass().getSimpleName() + (e.getMessage() == null ? "" : ": " + e.getMessage());
    report.fail(
        "features",
        feature.getId(),
        ReactLanguage.raw(
            TestMessages.FEATURE_PHASE_FAILED,
            MessageArgument.untrusted("phase", phase),
            MessageArgument.untrusted("reason", reason)
        )
    );
  }

  private boolean isActive(FeatureController controller, String id) {
    Map<String, Feature> active = controller.getActiveFeatures();
    return active != null && active.containsKey(id);
  }
}
