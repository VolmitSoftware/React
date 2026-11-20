package art.arcane.react.core.controller;

import art.arcane.react.React;
import art.arcane.react.core.telemetry.RuntimeDiagnostics;
import art.arcane.react.util.common.scheduling.TickedObject;
import art.arcane.react.util.common.scheduling.Ticker;
import art.arcane.react.util.plugin.IController;

public final class RuntimeDiagnosticsController extends TickedObject implements IController {
  private transient final RuntimeDiagnostics diagnostics = new RuntimeDiagnostics(() -> {
    Ticker ticker = React.instance.getTicker();
    return ticker == null ? null : ticker.diagnostics();
  }, () -> React.controller(HistoryController.class));

  public RuntimeDiagnosticsController() {
    super("react", "runtime-diagnostics", 1_000L);
  }

  @Override
  public String getName() {
    return "Runtime Diagnostics";
  }

  @Override
  public void start() {
    diagnostics.start();
  }

  @Override
  public void postStart() {
    SampleController controller = React.controller(SampleController.class);
    if (controller != null) {
      diagnostics.register(controller);
    }
  }

  @Override
  public void stop() {
    diagnostics.stop(React.controller(SampleController.class));
  }

  @Override
  public void onTick() {
    diagnostics.tick(React.controller(SampleController.class));
  }
}
