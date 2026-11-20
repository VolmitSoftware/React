package art.arcane.react.api.rendering;

import art.arcane.react.util.project.config.ConfigDescription;
import art.arcane.react.util.project.config.ConfigDoc;
import lombok.Data;

/** The map settings in {@code core/map.toml} on hosts without the Bukkit MapController, with the same keys and defaults. */
@Data
@ConfigDescription("Manages React map item metadata, renderer repair, and live map packet delivery for inventories and item frames.")
public class MapSettings implements MapRuntimeConfig {
  @ConfigDoc(value = "Main maintenance cadence for map repair logic in milliseconds.", impact = "Lower values repair inventories and item-frames sooner after reload; higher values reduce maintenance overhead.")
  private long maintenanceTickIntervalMs = 500L;
  @ConfigDoc(value = "Cadence for scanning player inventories for React map repair in milliseconds.", impact = "Lower values repair map metadata and renderers faster; higher values reduce per-tick work.")
  private long inventoryRepairCadenceMs = 250L;
  @ConfigDoc(value = "Number of online player inventories repaired per maintenance pass.", impact = "Higher values reduce recovery time after reload; lower values reduce work each pass.")
  private int inventoryRepairBatchSize = 3;
  @ConfigDoc(value = "Cadence for scanning loaded chunks for item-frame map repair in milliseconds.", impact = "Lower values fix item-frame maps faster after reload; higher values reduce maintenance overhead.")
  private long itemFrameRepairCadenceMs = 250L;
  @ConfigDoc(value = "Number of loaded chunks scanned for item-frame maps per maintenance pass.", impact = "Higher values speed up frame-map recovery after reload; lower values reduce per-pass cost.")
  private int itemFrameChunkBatchSize = 8;
  @ConfigDoc(value = "Warmup duration after startup/reload where map recovery uses aggressive repair batching (milliseconds).", impact = "Higher values prioritize fast post-reload recovery longer; lower values return to normal cadence sooner.")
  private long startupBoostDurationMs = 12_000L;
  @ConfigDoc(value = "Inventory repair batch size while startup warmup is active.", impact = "Higher values make map inventories recover faster right after reload at the cost of extra temporary work.")
  private int startupBoostInventoryBatchSize = 12;
  @ConfigDoc(value = "Item-frame chunk repair batch size while startup warmup is active.", impact = "Higher values make frame maps recover faster right after reload at the cost of extra temporary work.")
  private int startupBoostItemFrameChunkBatchSize = 32;
  @ConfigDoc(value = "Packet push interval for active item-frame maps in milliseconds.", impact = "Lower values refresh map visuals faster but increase packet and render pressure.")
  private long frameMapPushIntervalMs = 600L;
  @ConfigDoc(value = "Packet push interval used during startup warmup in milliseconds.", impact = "Lower values make frame maps appear faster after reload; higher values reduce temporary packet bursts during warmup.")
  private long startupFrameMapPushIntervalMs = 100L;
  @ConfigDoc(value = "Packet push interval for nearby viewers who are not actively looking at the frame map (milliseconds).", impact = "Higher values reduce idle packet volume when players are nearby but not watching the map.")
  private long frameMapIdlePushIntervalMs = 6_000L;
  @ConfigDoc(value = "Maximum block radius from an item-frame map where players receive push updates.", impact = "Higher values cover more viewers but increase packet fanout.")
  private double frameMapPushRadiusBlocks = 24D;
  @ConfigDoc(value = "Allows held-map updates outside frameMapPushRadiusBlocks.", impact = "Disable to hard-stop map packets outside radius; enable if holding should bypass distance checks.")
  private boolean frameMapPushOutsideRangeWhenHolding = false;
  @ConfigDoc(value = "Minimum view-direction dot product required to treat a player as actively looking at a frame map.", impact = "Higher values require tighter aim at the map before high-frequency updates are sent.")
  private double frameMapLookDotThreshold = 0.45D;
  @ConfigDoc(value = "If enabled, frame map pushes require line-of-sight for nearby viewers (holders bypass this).", impact = "Enabling eliminates packets to occluded viewers but adds lightweight visibility checks.")
  private boolean frameMapRequireLineOfSight = true;
  @ConfigDoc(value = "Retention duration for frame-map push bookkeeping in milliseconds.", impact = "Higher values keep state longer with less churn; lower values prune bookkeeping more aggressively.")
  private long frameMapPushStateRetentionMs = 600_000L;
  @ConfigDoc(value = "Minimum interval between canvas redraws for held React maps in milliseconds.", impact = "Lower values rotate and refresh held maps more smoothly; higher values cut render work when many players view the same map.")
  private long heldMapRedrawIntervalMs = 150L;
  @ConfigDoc(value = "Minimum interval between canvas redraws for item-frame React maps in milliseconds.", impact = "Lower values refresh frame dashboards faster; higher values collapse redundant per-viewer redraws on busy map walls.")
  private long frameMapRedrawIntervalMs = 300L;
  @ConfigDoc(value = "Interval between full item-stack revalidations of tracked frame maps in milliseconds.", impact = "Lower values detect swapped frame items sooner; higher values avoid repeated item metadata reads every maintenance tick.")
  private long frameMapValidateIntervalMs = 2_000L;
  @ConfigDoc(value = "Maximum tracked item-frame maps considered for packet pushes per maintenance pass.", impact = "Higher values refresh very large map walls sooner; lower values cap scheduler and entity-lookup pressure.")
  private int frameMapPushBatchSize = 128;
  @ConfigDoc(value = "Combines adjacent item-frame React maps with the same renderer into one large tiled megamap display.", impact = "Disable to render every frame map as an independent 128x128 dashboard.")
  private boolean megamapEnabled = true;
  @ConfigDoc(value = "Total tile budget for one combined megamap wall.", impact = "Walls larger than the budget fall back to zoomed single-dashboard rendering.")
  private int megamapMaxTiles = 32;
  @ConfigDoc(value = "Cloned React maps placed in item frames are automatically given fresh map ids so adjacent copies can combine into a megamap wall.", impact = "Disable to keep cloned maps sharing one picture and never combining.")
  private boolean megamapSplitDuplicates = true;
}
