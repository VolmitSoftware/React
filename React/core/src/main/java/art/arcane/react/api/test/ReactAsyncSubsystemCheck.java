package art.arcane.react.api.test;

public interface ReactAsyncSubsystemCheck {
  String subsystem();

  void run(TestReport report, Runnable onDone);
}
