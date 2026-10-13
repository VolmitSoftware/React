package art.arcane.react.api.web.resource;

import art.arcane.react.api.web.dto.Envelope;
import art.arcane.react.core.controller.RuntimeTelemetryController;
import art.arcane.react.core.telemetry.runtime.JfrTelemetry;
import art.arcane.react.core.telemetry.runtime.RegionalHealth;
import art.arcane.react.core.telemetry.runtime.RuntimeMetric;
import io.javalin.http.Context;

import java.util.function.Supplier;

public final class RuntimeMetricsResource {
  private final Supplier<RuntimeTelemetryController> controller;

  public RuntimeMetricsResource(Supplier<RuntimeTelemetryController> controller) {
    this.controller = controller;
  }

  public Snapshot snapshotData() {
    RuntimeTelemetryController runtime = controller.get();
    if (runtime == null) {
      return new Snapshot(false, "source-unavailable", false, "source-unavailable", RegionalHealth.Snapshot.EMPTY, JfrTelemetry.Snapshot.EMPTY);
    }
    RuntimeMetric coverage = runtime.metric("region-player-coverage");
    RuntimeMetric jfr = runtime.metric("jfr-delivery-delay-ms");
    return new Snapshot(coverage.available(), coverage.unavailableReason(), jfr.available(), jfr.unavailableReason(),
        runtime.regionalSnapshot(), runtime.jfrSnapshot());
  }

  public void get(Context context) {
    context.json(new Envelope<>(snapshotData()));
  }

  public record Snapshot(boolean regionalAvailable, String regionalUnavailableReason, boolean jfrAvailable, String jfrUnavailableReason, RegionalHealth.Snapshot regional, JfrTelemetry.Snapshot jfr) {
  }
}
