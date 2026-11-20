package art.arcane.react.platform;

import java.util.Set;

/**
 * One world's live entity tracking and activation ranges in blocks. Read and change them on the server thread.
 */
public interface EntityRanges {
  Set<EntityRange> supportedRanges();

  int range(EntityRange type);

  void range(EntityRange type, int blocks);

  /**
   * True when villagers outside their activation range still run their reduced inactive tick.
   */
  boolean tickInactiveVillagers();

  void tickInactiveVillagers(boolean enabled);
}
