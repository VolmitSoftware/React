package art.arcane.react.api.test.checks;

import art.arcane.react.React;
import art.arcane.react.api.sampler.Sampler;
import art.arcane.react.api.test.ReactSubsystemCheck;
import art.arcane.react.api.test.TestReport;
import art.arcane.react.api.test.TestStatus;
import art.arcane.react.core.controller.SampleController;
import art.arcane.react.util.project.registry.Registry;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class MonitoringCheck implements ReactSubsystemCheck {
  private static final String SUBSYSTEM = "monitoring";

  @Override
  public String subsystem() {
    return SUBSYSTEM;
  }

  @Override
  public void run(TestReport report) {
    SampleController controller = React.controller(SampleController.class);
    if (controller == null) {
      report.skip(SUBSYSTEM, "Sampler finiteness", "SampleController is not available");
      return;
    }

    Registry<Sampler> registry = controller.getSamplers();
    if (registry == null) {
      report.skip(SUBSYSTEM, "Sampler finiteness", "Sampler registry is not initialized");
      return;
    }

    Collection<Sampler> samplers = registry.all();
    int total = registry.size();
    if (samplers == null || samplers.isEmpty()) {
      report.warn(SUBSYSTEM, "Sampler finiteness", "No samplers are registered");
      return;
    }

    Map<String, Object> offenders = new LinkedHashMap<String, Object>();
    List<String> offenderIds = new ArrayList<String>();
    List<String> integrationIds = new ArrayList<String>();
    int finiteCount = 0;
    int unreadable = 0;

    for (Sampler sampler : samplers) {
      String id = sampler.getId();
      if (isIntegrationId(id)) {
        integrationIds.add(id);
      }

      double value;
      try {
        value = sampler.sample();
      } catch (Throwable e) {
        unreadable++;
        report.warn(SUBSYSTEM, "Sampler read [" + id + "]", "sample() threw " + summarize(e));
        React.reportError(e);
        continue;
      }

      if (Double.isFinite(value)) {
        finiteCount++;
      } else {
        offenderIds.add(id);
        offenders.put(id, value);
      }
    }

    report.record(
        SUBSYSTEM,
        "Integration samplers",
        TestStatus.INFO,
        integrationIds.isEmpty()
            ? "No iris/adapt/wormholes integration samplers present"
            : integrationIds.size() + " integration sampler(s): " + String.join(", ", integrationIds));

    if (!offenders.isEmpty()) {
      report.record(
          SUBSYSTEM,
          "Sampler finiteness",
          TestStatus.FAIL,
          offenders.size() + " of " + total + " sampler(s) returned a non-finite sample(): " + String.join(", ", offenderIds),
          offenders);
      return;
    }

    report.pass(
        SUBSYSTEM,
        "Sampler finiteness",
        finiteCount + " samplers, all finite" + (unreadable > 0 ? " (" + unreadable + " unreadable)" : ""));
  }

  private boolean isIntegrationId(String id) {
    if (id == null) {
      return false;
    }
    String lower = id.toLowerCase();
    return lower.contains("iris") || lower.contains("adapt") || lower.contains("wormhole");
  }

  private String summarize(Throwable throwable) {
    Throwable root = throwable;
    while (root.getCause() != null && root.getCause() != root) {
      root = root.getCause();
    }
    String message = root.getMessage();
    if (message == null || message.isBlank()) {
      return root.getClass().getSimpleName();
    }
    return root.getClass().getSimpleName() + ": " + message;
  }
}
