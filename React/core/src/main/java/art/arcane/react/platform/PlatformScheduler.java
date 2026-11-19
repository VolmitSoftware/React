package art.arcane.react.platform;

/**
 * Task dispatch in the Folia shape. On platforms without regions, region and entity work runs on the server thread.
 * Methods that take {@code Object} accept the platform's own entity or location objects.
 * Every scheduling method returns false when the platform refused the task, and never runs it in that case.
 */
public interface PlatformScheduler {
  boolean isPrimaryThread();

  boolean isRegionized();

  default void forceRegionized() {
  }

  boolean isStopping();

  /**
   * True while the owning plugin or mod is enabled and may schedule work.
   */
  boolean isActive();

  /**
   * True once startup finished and queued main-thread jobs run every tick.
   */
  boolean isReady();

  /**
   * Hands a task to React's main-thread job queue, or to the startup queue while {@link #isReady()} is false.
   */
  void queue(Runnable task);

  boolean isOwnedByCurrentRegion(Object handle);

  default boolean isOwnedByCurrentRegion(ReactWorld world, int chunkX, int chunkZ) {
    return isPrimaryThread();
  }

  /**
   * True when the platform location names a world, so it can be mapped to a region.
   */
  boolean isPlaced(Object location);

  boolean global(Runnable task, int delayTicks);

  boolean region(ReactWorld world, int chunkX, int chunkZ, Runnable task, int delayTicks);

  boolean region(Object location, Runnable task, int delayTicks);

  /**
   * Runs the task on the thread that owns the entity. {@code retired} runs instead when the entity is removed
   * before the task could run; it may be null.
   */
  boolean entity(Object entity, Runnable task, int delayTicks, Runnable retired);

  boolean async(Runnable task, int delayTicks);

  /**
   * Schedules on the platform's legacy single-thread tick scheduler.
   *
   * @throws UnsupportedOperationException when the platform turned out to be regionized
   */
  boolean later(Runnable task, int delayTicks);

  void cancelAll();
}
