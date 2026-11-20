package art.arcane.react.platform;

/**
 * Server and per-world view and simulation distances, in chunks. Setters run on the server thread.
 */
public interface ViewDistanceHost {
  /**
   * False where worlds cannot have their own distances, such as plain Spigot.
   */
  boolean supportsWorldDistance();

  int serverViewDistance();

  int serverSimulationDistance();

  int viewDistance(ReactWorld world);

  int simulationDistance(ReactWorld world);

  void setViewDistance(ReactWorld world, int distance);

  void setSimulationDistance(ReactWorld world, int distance);
}
