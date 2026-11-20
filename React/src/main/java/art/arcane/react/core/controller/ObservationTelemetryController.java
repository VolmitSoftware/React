package art.arcane.react.core.controller;

import art.arcane.react.React;
import art.arcane.react.core.telemetry.ObservationTelemetry;
import art.arcane.react.util.plugin.IController;

public final class ObservationTelemetryController implements IController {
  private transient final ObservationTelemetry telemetry = new ObservationTelemetry();

  @Override
  public String getId() {
    return "observation-telemetry";
  }

  @Override
  public String getName() {
    return "Observation Telemetry";
  }

  @Override
  public void start() {
    telemetry.start();
  }

  @Override
  public void postStart() {
    telemetry.register(React.controller(SampleController.class));
  }

  @Override
  public void stop() {
    telemetry.stop(React.controller(SampleController.class));
  }
}
