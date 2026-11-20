package art.arcane.react.platform.cost;

/**
 * A sampler that reads one plugin or mod, so the map hub can list every owner's samplers together, family by family,
 * from the busiest owner down.
 */
public interface OwnerSampler {
  int FAMILY_COST = 0;
  int FAMILY_WORLDGEN = 1;
  int FAMILY_GAUGES = 2;

  /**
   * Families list in this order: cost first, then world generation, then the gauges.
   */
  int ownerFamily();

  /**
   * The value owners of one family are ranked by, or NaN when this sampler does not read a single owner.
   */
  double ownerRankValue();
}
