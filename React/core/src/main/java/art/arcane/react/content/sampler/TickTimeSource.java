package art.arcane.react.content.sampler;

@FunctionalInterface
interface TickTimeSource {
  long[] read();

  default int freshTicks(int observedTicks) {
    return observedTicks;
  }
}
