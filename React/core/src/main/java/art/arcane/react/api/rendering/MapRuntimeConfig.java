package art.arcane.react.api.rendering;

public interface MapRuntimeConfig {
  long getMaintenanceTickIntervalMs();

  long getInventoryRepairCadenceMs();

  int getInventoryRepairBatchSize();

  long getItemFrameRepairCadenceMs();

  int getItemFrameChunkBatchSize();

  long getStartupBoostDurationMs();

  int getStartupBoostInventoryBatchSize();

  int getStartupBoostItemFrameChunkBatchSize();

  long getFrameMapPushIntervalMs();

  long getStartupFrameMapPushIntervalMs();

  long getFrameMapIdlePushIntervalMs();

  double getFrameMapPushRadiusBlocks();

  boolean isFrameMapPushOutsideRangeWhenHolding();

  double getFrameMapLookDotThreshold();

  boolean isFrameMapRequireLineOfSight();

  long getFrameMapPushStateRetentionMs();

  long getHeldMapRedrawIntervalMs();

  long getFrameMapRedrawIntervalMs();

  long getFrameMapValidateIntervalMs();

  int getFrameMapPushBatchSize();

  boolean isMegamapEnabled();

  int getMegamapMaxTiles();

  boolean isMegamapSplitDuplicates();
}
