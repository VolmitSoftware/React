package art.arcane.react.platform.cost;

/**
 * A per-owner cost sampler, so lists of owner graphs can be ordered by what each owner costs.
 */
public interface OwnerCostSampler extends OwnerSampler {
  /**
   * The owner's smoothed cost in ms/s over the windows measured so far; it holds its last value while nothing measures.
   */
  double recentCostMsPerSecond();

  @Override
  default int ownerFamily() {
    return FAMILY_COST;
  }

  @Override
  default double ownerRankValue() {
    return recentCostMsPerSecond();
  }
}
