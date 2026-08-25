/*
 *  Copyright (c) 2016-2025 Arcane Arts (Volmit Software)
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 *
 */

package art.arcane.react.content.feature;

import art.arcane.react.React;
import art.arcane.react.api.feature.ReactFeature;
import art.arcane.react.api.rendering.MapTheme;
import art.arcane.react.api.rendering.MegamapGrid;
import art.arcane.react.api.rendering.ReactRenderer;
import art.arcane.react.api.rendering.Region;
import art.arcane.react.api.rendering.RendererLayout;
import art.arcane.react.api.sampler.Sampler;
import art.arcane.react.api.web.heatmap.ChunkGridExporter;
import art.arcane.react.api.web.heatmap.HeatmapWorldRef;
import art.arcane.react.core.controller.MapController;
import art.arcane.react.core.controller.ObserverController;
import art.arcane.react.core.controller.ObserverController.LoadedChunkCoordinate;
import art.arcane.react.localization.ReactLanguage;
import art.arcane.react.localization.catalog.RendererMessages;
import art.arcane.react.model.SampledChunk;
import art.arcane.react.util.data.TinyColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@art.arcane.react.util.project.config.ConfigDescription("Configuration for Chunk Heatmap Base feature. This feature continuously monitors server behavior and applies guardrails during runtime.")
abstract class FeatureChunkHeatmapBase extends ReactFeature implements ReactRenderer, ChunkGridExporter {
  private static final long SCAN_CACHE_TTL_MS = 300L;
  private static final int COLOR_RAMP_STEPS = 64;
  private static final int LEGEND_RAMP_STEPS = 16;
  private static final ThreadLocal<Projection> PROJECTION = ThreadLocal.withInitial(Projection::new);
  private static final TinyColor FRAME_CENTER = MapTheme.INFO;
  private static final TinyColor HELD_CENTER = MapTheme.OK;
  private static final TinyColor FRAME_VALUE = MapTheme.INFO;
  private static final TinyColor UNLOADED_CELL = MapTheme.SURFACE_0;
  private static final TinyColor LOADED_QUIET_CELL = MapTheme.SURFACE_3;
  private static final TinyColor CELL_BORDER = MapTheme.LINE;
  private static final TinyColor MCA_BORDER = MapTheme.LINE_STRONG;
  private static final TinyColor COOL_LOW = new TinyColor(16, 78, 180);
  private static final TinyColor COOL_HIGH = new TinyColor(60, 175, 235);
  private static final TinyColor HOT_HIGH = new TinyColor(255, 98, 42);
  @art.arcane.react.util.project.config.ConfigDoc(value = "Pixel scale used when chunk heatmap base draws each chunk on the map.", impact = "Higher values make chunks larger and reduce visible radius; lower values show more area with finer detail.")
  private int chunkPixelSize = 5;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Map chunks radius used by chunk heatmap base (chunks).", impact = "Higher values widen the search area and cost more work; lower values narrow scope and run cheaper.")
  private int mapRadiusChunks = 0;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Controls whether chunk heatmap base renders draw center marker on map output.", impact = "Enable to show this visual layer; disable for a cleaner map and slightly lower render cost.")
  private boolean drawCenterMarker = true;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Controls whether chunk heatmap base renders draw label on map output.", impact = "Enable to show this visual layer; disable for a cleaner map and slightly lower render cost.")
  private boolean drawLabel = true;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Minimum peak chunk score required before the heatmap renders activity.", impact = "Below this the map shows an explicit quiet state instead of amplifying measurement noise into full-scale colors.")
  private double minSignificantScore = 0.001D;
  private transient volatile ScanSnapshot cachedScan = ScanSnapshot.empty();

  protected FeatureChunkHeatmapBase(String id) {
    super(id);
  }

  @Override
  public String heatmapId() {
    return getId();
  }

  @Override
  public String heatmapLabel() {
    return mapLabel();
  }

  @Override
  public double scoreChunk(HeatmapWorldRef world, int chunkX, int chunkZ) {
    double score = chunkScore(world, chunkX, chunkZ);
    return Double.isFinite(score) ? Math.max(0D, score) : 0D;
  }

  @Override
  public MegamapGrid.MegamapCapability megamapCapability() {
    return MegamapGrid.MegamapCapability.adaptiveWall();
  }

  @Override
  public void render() {
    Player viewer = player();
    MapController mapController = React.controller(MapController.class);
    Location anchor = mapController == null
        ? viewer == null ? null : viewer.getLocation().clone()
        : mapController.resolveMapAnchor(view(), viewer);
    World mapWorld = view() != null && view().getWorld() != null
        ? view().getWorld()
        : anchor == null ? viewer == null ? null : viewer.getWorld() : anchor.getWorld();

    if (mapWorld == null) {
      fill(rootRegion(), backgroundColor());
      RendererLayout.emptyState(
          this,
          bodyRegion(),
          ReactLanguage.raw(RendererMessages.HEATMAP_NO_ACTIVITY),
          ReactLanguage.raw(RendererMessages.PLUGIN_WAIT_FOR_DATA)
      );
      dashHeader(drawLabel ? mapLabel() : null, null, headerColor(false), FRAME_VALUE);
      return;
    }

    if (anchor == null || anchor.getWorld() == null) {
      anchor = mapWorld.getSpawnLocation().clone();
    } else if (!mapWorld.equals(anchor.getWorld())) {
      anchor = new Location(
          mapWorld,
          anchor.getX(),
          Math.max(mapWorld.getMinHeight(), Math.min(mapWorld.getMaxHeight() - 1, anchor.getY())),
          anchor.getZ(),
          anchor.getYaw(),
          anchor.getPitch()
      );
    }

    boolean frameAnchored = mapController != null && mapController.hasFrameAnchor(view());
    fill(rootRegion(), backgroundColor());
    Region body = bodyRegion();
    int centerChunkX = Math.floorDiv(anchor.getBlockX(), 16);
    int centerChunkZ = Math.floorDiv(anchor.getBlockZ(), 16);
    int axisLeft = textWidth("-000") + MapTheme.gutter(uiScale());
    int axisTop = textHeight() + Math.max(1, uiScale());
    ChunkHeatmapLayout layout = ChunkHeatmapLayout.create(
        body,
        centerChunkX,
        centerChunkZ,
        effectiveZoom(),
        mapRadiusChunks,
        axisLeft,
        axisTop
    );

    Projection projection = PROJECTION.get();
    projection.gridX = layout.grid().x();
    projection.gridY = layout.grid().y();
    projection.minChunkX = layout.minChunkX();
    projection.minChunkZ = layout.minChunkZ();
    projection.cellSize = layout.cellSize();
    projection.radiusChunks = layout.radiusChunks();
    projection.anchorWorldId = mapWorld.getUID();
    projection.anchorX = anchor.getX();
    projection.anchorZ = anchor.getZ();
    projection.frameAnchored = frameAnchored;

    ScanSnapshot scan = scan(mapWorld.getUID(), layout);
    pushClip(body);
    try {
      Region drawBounds = clipRegion().intersect(tileRegion());
      drawGrid(layout, scan, scan.colorRamp, drawBounds);
      renderOverlay(scan.scale.quiet() ? Map.of() : scan.positiveScores, 0D, scan.scale.maximum());
      if (drawCenterMarker) {
        drawCenterRing(layout, frameAnchored ? FRAME_CENTER : HELD_CENTER);
      }
      drawAxes(layout);
    } finally {
      popClip();
    }

    dashHeader(
        drawLabel ? mapLabel() : null,
        coordinateLabel(centerChunkX, centerChunkZ, frameAnchored),
        headerColor(frameAnchored),
        FRAME_VALUE
    );
    drawLegend(scan.scale, layout.cellSize(), frameAnchored, scan.colorRamp);
  }

  protected int effectiveZoom() {
    int zoom = Math.max(3, chunkPixelSize);
    return canvasSpan() >= 2 ? Math.max(3, zoom - 2) : zoom;
  }

  protected Pixel projectChunk(LoadedChunkCoordinate chunk) {
    return PROJECTION.get().projectChunk(chunk.chunkX(), chunk.chunkZ());
  }

  protected TinyColor backgroundColor() {
    return new TinyColor(6, 8, 12);
  }

  protected TinyColor headerColor(boolean frameAnchored) {
    TinyColor base = headerColor();
    return frameAnchored ? tint(base, 1.12D) : base;
  }

  protected TinyColor headerColor() {
    return new TinyColor(68, 122, 176);
  }

  protected String mapLabel() {
    return getName();
  }

  protected abstract double chunkScore(HeatmapWorldRef world, int chunkX, int chunkZ);

  protected TinyColor colorFor(double normalized, double rawScore) {
    if (normalized < 0.5D) {
      return gradient(normalized * 2D, COOL_LOW, COOL_HIGH);
    }
    return gradient((normalized - 0.5D) * 2D, COOL_HIGH, HOT_HIGH);
  }

  protected void renderOverlay(Map<LoadedChunkCoordinate, Double> score, double min, double max) {

  }

  protected double chunkSample(HeatmapWorldRef world, int chunkX, int chunkZ, String samplerId) {
    Sampler sampler = React.sampler(samplerId);
    ObserverController observer = React.controller(ObserverController.class);
    if (sampler == null || observer == null) {
      return 0D;
    }
    return Math.max(0D, observer.sample(world.worldKey(), chunkX, chunkZ, sampler).orElse(0D));
  }

  protected double chunkTotalScore(HeatmapWorldRef world, int chunkX, int chunkZ) {
    ObserverController observer = React.controller(ObserverController.class);
    if (observer == null || observer.getSampled() == null) {
      return 0D;
    }
    return observer.sampledChunk(world.worldKey(), chunkX, chunkZ).map(SampledChunk::totalScore).orElse(0D);
  }

  protected TinyColor gradient(double normalized, TinyColor low, TinyColor high) {
    return new TinyColor(gradientRgb(normalized, low, high));
  }

  protected Pixel projectBlockDelta(ProjectionAnchor anchor, double targetX, double targetZ) {
    return PROJECTION.get().projectBlock(targetX, targetZ);
  }

  protected double visibleRadiusBlocks() {
    return (Math.max(0, PROJECTION.get().radiusChunks) + 1D) * 16D;
  }

  protected ProjectionAnchor projectionAnchor() {
    Projection projection = PROJECTION.get();
    return projection.anchorWorldId == null
        ? null
        : new ProjectionAnchor(projection.anchorWorldId, projection.anchorX, projection.anchorZ);
  }

  protected boolean projectionFrameAnchored() {
    return PROJECTION.get().frameAnchored;
  }

  private int canvasSpan() {
    return Math.max(1, Math.min(width(), height()) / ReactRenderer.CANVAS_SIZE);
  }

  private ScanSnapshot scan(UUID worldId, ChunkHeatmapLayout layout) {
    long now = System.currentTimeMillis();
    ScanSnapshot current = cachedScan;
    if (current.matches(worldId, layout, minSignificantScore, now)) {
      return current;
    }

    double[] cellScores = new double[layout.columns() * layout.rows()];
    Arrays.fill(cellScores, Double.NaN);
    HashMap<LoadedChunkCoordinate, Double> positiveScores = new HashMap<>();
    ObserverController observer = React.controller(ObserverController.class);
    HeatmapWorldRef heatmapWorld = observer == null ? null : observer.heatmapWorld(worldId).orElse(null);
    if (observer != null && heatmapWorld != null) {
      for (LoadedChunkCoordinate chunk : observer.loadedChunkCoordinatesInBounds(
          worldId,
          layout.minChunkX(),
          layout.maxChunkX(),
          layout.minChunkZ(),
          layout.maxChunkZ()
      )) {
        int index = layout.indexOf(chunk.chunkX(), chunk.chunkZ());
        if (index < 0) {
          continue;
        }
        double value = scoreChunk(heatmapWorld, chunk.chunkX(), chunk.chunkZ());
        cellScores[index] = value;
        if (value > 0D) {
          positiveScores.put(chunk, value);
        }
      }
    }

    ChunkHeatmapScale scale = ChunkHeatmapScale.fromValues(cellScores, minSignificantScore);
    ScanSnapshot refreshed = new ScanSnapshot(
        worldId,
        layout.minChunkX(),
        layout.maxChunkX(),
        layout.minChunkZ(),
        layout.maxChunkZ(),
        minSignificantScore,
        now,
        cellScores,
        positiveScores.isEmpty() ? Map.of() : Map.copyOf(positiveScores),
        scale,
        buildColorRamp(scale)
    );
    cachedScan = refreshed;
    return refreshed;
  }

  private void drawGrid(
      ChunkHeatmapLayout layout,
      ScanSnapshot scan,
      TinyColor[] colorRamp,
      Region drawBounds
  ) {
    int cellSize = layout.cellSize();
    for (int chunkZ = layout.minChunkZ(); chunkZ <= layout.maxChunkZ(); chunkZ++) {
      int y = layout.cellY(chunkZ);
      if (y >= drawBounds.bottom() || y + cellSize <= drawBounds.y()) {
        continue;
      }
      for (int chunkX = layout.minChunkX(); chunkX <= layout.maxChunkX(); chunkX++) {
        int x = layout.cellX(chunkX);
        if (x >= drawBounds.right() || x + cellSize <= drawBounds.x()) {
          continue;
        }
        double value = scan.cellScores[layout.indexOf(chunkX, chunkZ)];
        drawCell(x, y, cellSize, cellColor(value, scan.scale, colorRamp), CELL_BORDER);
        drawMcaEdges(x, y, cellSize, chunkX, chunkZ);
      }
    }
  }

  private TinyColor cellColor(double value, ChunkHeatmapScale scale, TinyColor[] colorRamp) {
    if (Double.isNaN(value)) {
      return UNLOADED_CELL;
    }
    if (value <= 0D || colorRamp.length == 0) {
      return LOADED_QUIET_CELL;
    }
    return colorRamp[scale.rampIndex(value, colorRamp.length)];
  }

  private void drawCell(int x, int y, int size, TinyColor fillColor, TinyColor borderColor) {
    set(x, y, size, size, fillColor);
    drawRing(x, y, size, borderColor);
  }

  private void drawRing(int x, int y, int size, TinyColor color) {
    set(x, y, size, 1, color);
    set(x, y + size - 1, size, 1, color);
    set(x, y + 1, 1, Math.max(0, size - 2), color);
    set(x + size - 1, y + 1, 1, Math.max(0, size - 2), color);
  }

  private void drawMcaEdges(int x, int y, int size, int chunkX, int chunkZ) {
    if (ChunkHeatmapLayout.startsMcaRegion(chunkX)) {
      set(x, y, 1, size, MCA_BORDER);
    }
    if (ChunkHeatmapLayout.endsMcaRegion(chunkX)) {
      set(x + size - 1, y, 1, size, MCA_BORDER);
    }
    if (ChunkHeatmapLayout.startsMcaRegion(chunkZ)) {
      set(x, y, size, 1, MCA_BORDER);
    }
    if (ChunkHeatmapLayout.endsMcaRegion(chunkZ)) {
      set(x, y + size - 1, size, 1, MCA_BORDER);
    }
  }

  private void drawCenterRing(ChunkHeatmapLayout layout, TinyColor color) {
    int x = layout.cellX(layout.centerChunkX());
    int y = layout.cellY(layout.centerChunkZ());
    int size = layout.cellSize();
    int thickness = Math.max(1, Math.min(uiScale(), Math.max(1, size / 3)));
    for (int inset = 0; inset < thickness; inset++) {
      drawRing(x + inset, y + inset, size - (2 * inset), color);
    }
  }

  private void drawAxes(ChunkHeatmapLayout layout) {
    Region body = bodyRegion();
    int labelY = Math.max(body.y(), layout.grid().y() - textHeight() - 1);
    text(body.x(), labelY, "N", MapTheme.TEXT_STRONG);
    text(body.x() + textWidth("N") + MapTheme.gutter(uiScale()), labelY, "X", MapTheme.TEXT_MUTED);
    drawCenteredAxisLabel(
        relativeLabel(layout.minChunkX() - layout.centerChunkX()),
        layout.cellX(layout.minChunkX()) + (layout.cellSize() / 2),
        labelY
    );
    if (layout.columns() > 1) {
      drawCenteredAxisLabel("0", layout.cellX(layout.centerChunkX()) + (layout.cellSize() / 2), labelY);
      drawCenteredAxisLabel(
          relativeLabel(layout.maxChunkX() - layout.centerChunkX()),
          layout.cellX(layout.maxChunkX()) + (layout.cellSize() / 2),
          labelY
      );
    }

    int labelRight = layout.grid().x() - MapTheme.gutter(uiScale());
    text(body.x(), layout.grid().y(), "Z", MapTheme.TEXT_MUTED);
    drawRightAxisLabel(
        relativeLabel(layout.minChunkZ() - layout.centerChunkZ()),
        labelRight,
        layout.cellY(layout.minChunkZ()) + (layout.cellSize() / 2)
    );
    if (layout.rows() > 1) {
      drawRightAxisLabel("0", labelRight, layout.cellY(layout.centerChunkZ()) + (layout.cellSize() / 2));
      drawRightAxisLabel(
          relativeLabel(layout.maxChunkZ() - layout.centerChunkZ()),
          labelRight,
          layout.cellY(layout.maxChunkZ()) + (layout.cellSize() / 2)
      );
    }
  }

  private void drawCenteredAxisLabel(String label, int centerX, int y) {
    text(centerX - (textWidth(label) / 2), y, label, MapTheme.TEXT_MUTED);
  }

  private void drawRightAxisLabel(String label, int right, int centerY) {
    text(right - textWidth(label), centerY - (textHeight() / 2), label, MapTheme.TEXT_MUTED);
  }

  private String relativeLabel(int offset) {
    return offset > 0 ? "+" + offset : Integer.toString(offset);
  }

  private String coordinateLabel(int chunkX, int chunkZ, boolean frameAnchored) {
    String label = (frameAnchored ? "F " : "") + "X" + chunkX + " Z" + chunkZ;
    return textWidth(label) <= Math.max(1, width() / 2) ? label : null;
  }

  private TinyColor[] buildColorRamp(ChunkHeatmapScale scale) {
    if (scale.maximum() <= 0D) {
      return new TinyColor[0];
    }
    TinyColor[] ramp = new TinyColor[COLOR_RAMP_STEPS];
    for (int step = 0; step < ramp.length; step++) {
      double normalized = step / (double) (ramp.length - 1);
      ramp[step] = colorFor(normalized, scale.maximum() * normalized);
    }
    return ramp;
  }

  private TinyColor tint(TinyColor color, double factor) {
    int red = (int) Math.round(color.getRed() * factor);
    int green = (int) Math.round(color.getGreen() * factor);
    int blue = (int) Math.round(color.getBlue() * factor);
    return new TinyColor(
        Math.max(0, Math.min(255, red)),
        Math.max(0, Math.min(255, green)),
        Math.max(0, Math.min(255, blue))
    );
  }

  private void drawLegend(
      ChunkHeatmapScale scale,
      int cellSize,
      boolean frameAnchored,
      TinyColor[] colorRamp
  ) {
    Region footer = footerRegion();
    if (footer.isEmpty()) {
      return;
    }
    fill(footer, MapTheme.SURFACE_2);
    hSeparator(footer, 0, headerColor(frameAnchored));
    Region content = RendererLayout.footerContent(this);
    if (content.isEmpty()) {
      return;
    }

    int baseline = RendererLayout.baseline(this, content);
    String cellScale = "1C=" + cellSize + "px";
    if (scale.quiet()) {
      textIn(content, 0, baseline, ReactLanguage.raw(RendererMessages.HEATMAP_QUIET), MapTheme.TEXT_MUTED);
      textRightIn(content, 0, baseline, cellScale, MapTheme.TEXT_MUTED);
      return;
    }

    String lowLabel = "0";
    String highLabel = compact(scale.maximum());
    int gutter = MapTheme.gutter(uiScale());
    int lowWidth = textWidth(lowLabel);
    int highWidth = textWidth(highLabel);
    int scaleWidth = textWidth(cellScale);
    textIn(content, 0, baseline, lowLabel, MapTheme.TEXT_MUTED);
    textRightIn(content, 0, baseline, cellScale, MapTheme.TEXT_MUTED);
    Region highRegion = new Region(
        content.right() - scaleWidth - gutter - highWidth,
        content.y(),
        highWidth,
        content.height()
    );
    textIn(highRegion, 0, baseline, highLabel, MapTheme.TEXT_MUTED);

    Region ramp = content
        .withoutLeft(lowWidth + gutter)
        .withoutRight(scaleWidth + highWidth + (3 * gutter))
        .inset(0, 2 * uiScale(), 0, 2 * uiScale());
    if (ramp.width() < LEGEND_RAMP_STEPS || colorRamp.length == 0) {
      return;
    }
    int stepWidth = Math.max(1, ramp.width() / LEGEND_RAMP_STEPS);
    for (int step = 0; step < LEGEND_RAMP_STEPS; step++) {
      int x = ramp.x() + (step * stepWidth);
      int width = step == LEGEND_RAMP_STEPS - 1 ? ramp.right() - x : stepWidth;
      int rampIndex = (int) Math.round(
          (step / (double) (LEGEND_RAMP_STEPS - 1)) * (colorRamp.length - 1)
      );
      fill(new Region(x, ramp.y(), width, ramp.height()), colorRamp[rampIndex]);
    }
  }

  private String compact(double value) {
    if (!Double.isFinite(value)) {
      return "0";
    }
    if (Math.abs(value) >= 1000D) {
      return String.format(Locale.ROOT, "%.1fk", value / 1000D);
    }
    if (Math.abs(value) >= 100D) {
      return String.format(Locale.ROOT, "%.0f", value);
    }
    if (Math.abs(value) >= 10D) {
      return String.format(Locale.ROOT, "%.1f", value);
    }
    return String.format(Locale.ROOT, "%.2f", value);
  }

  protected static final class Pixel {
    @art.arcane.react.util.project.config.ConfigDoc(value = "X-axis coordinate used by chunk heatmap base internal tracking.", impact = "This is internal state data and should not normally be changed manually.")
    private final int x;
    @art.arcane.react.util.project.config.ConfigDoc(value = "Y-axis coordinate used by chunk heatmap base internal tracking.", impact = "This is internal state data and should not normally be changed manually.")
    private final int y;

    private Pixel(int x, int y) {
      this.x = x;
      this.y = y;
    }

    public int x() {
      return x;
    }

    public int y() {
      return y;
    }
  }

  protected record ProjectionAnchor(UUID worldId, double x, double z) {
  }

  private static final class Projection {
    private int gridX;
    private int gridY;
    private int minChunkX;
    private int minChunkZ;
    private int cellSize = 1;
    private int radiusChunks;
    private UUID anchorWorldId;
    private double anchorX;
    private double anchorZ;
    private boolean frameAnchored;

    private Pixel projectChunk(int chunkX, int chunkZ) {
      int x = gridX + ((chunkX - minChunkX) * cellSize) + (cellSize / 2);
      int y = gridY + ((chunkZ - minChunkZ) * cellSize) + (cellSize / 2);
      return new Pixel(x, y);
    }

    private Pixel projectBlock(double blockX, double blockZ) {
      double originX = minChunkX * 16D;
      double originZ = minChunkZ * 16D;
      int x = gridX + (int) Math.floor(((blockX - originX) * cellSize) / 16D);
      int y = gridY + (int) Math.floor(((blockZ - originZ) * cellSize) / 16D);
      return new Pixel(x, y);
    }
  }

  private static final class ScanSnapshot {
    private final UUID worldId;
    private final int minChunkX;
    private final int maxChunkX;
    private final int minChunkZ;
    private final int maxChunkZ;
    private final double significantThreshold;
    private final long stampMs;
    private final double[] cellScores;
    private final Map<LoadedChunkCoordinate, Double> positiveScores;
    private final ChunkHeatmapScale scale;
    private final TinyColor[] colorRamp;

    private ScanSnapshot(
        UUID worldId,
        int minChunkX,
        int maxChunkX,
        int minChunkZ,
        int maxChunkZ,
        double significantThreshold,
        long stampMs,
        double[] cellScores,
        Map<LoadedChunkCoordinate, Double> positiveScores,
        ChunkHeatmapScale scale,
        TinyColor[] colorRamp
    ) {
      this.worldId = worldId;
      this.minChunkX = minChunkX;
      this.maxChunkX = maxChunkX;
      this.minChunkZ = minChunkZ;
      this.maxChunkZ = maxChunkZ;
      this.significantThreshold = significantThreshold;
      this.stampMs = stampMs;
      this.cellScores = cellScores;
      this.positiveScores = positiveScores;
      this.scale = scale;
      this.colorRamp = colorRamp;
    }

    private static ScanSnapshot empty() {
      return new ScanSnapshot(
          null,
          0,
          -1,
          0,
          -1,
          0D,
          0L,
          new double[0],
          Map.of(),
          ChunkHeatmapScale.fromValues(new double[0], 0D),
          new TinyColor[0]
      );
    }

    private boolean matches(
        UUID requestedWorldId,
        ChunkHeatmapLayout layout,
        double requestedThreshold,
        long now
    ) {
      return requestedWorldId.equals(worldId)
          && minChunkX == layout.minChunkX()
          && maxChunkX == layout.maxChunkX()
          && minChunkZ == layout.minChunkZ()
          && maxChunkZ == layout.maxChunkZ()
          && Double.compare(significantThreshold, requestedThreshold) == 0
          && now - stampMs >= 0L
          && now - stampMs < SCAN_CACHE_TTL_MS;
    }
  }
}
