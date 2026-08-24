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
import art.arcane.react.localization.catalog.MapMessages;
import art.arcane.react.localization.catalog.RendererMessages;
import art.arcane.react.model.SampledChunk;
import art.arcane.react.util.data.TinyColor;
import art.arcane.volmlib.util.localization.MessageArgument;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

@art.arcane.react.util.project.config.ConfigDescription("Configuration for Chunk Heatmap Base feature. This feature continuously monitors server behavior and applies guardrails during runtime.")
abstract class FeatureChunkHeatmapBase extends ReactFeature implements ReactRenderer, ChunkGridExporter {
  private static final long SCAN_CACHE_TTL_MS = 45L;
  private static final int RAMP_STEPS = 16;
  private static final ThreadLocal<ScanCache> SCAN_CACHE = ThreadLocal.withInitial(ScanCache::new);
  private static final ThreadLocal<Projection> PROJECTION = ThreadLocal.withInitial(Projection::new);
  private static final AtomicLong NEXT_SCAN_CACHE_OWNER_ID = new AtomicLong();
  private static final TinyColor FRAME_CENTER = MapTheme.INFO;
  private static final TinyColor HELD_CENTER = MapTheme.OK;
  private static final TinyColor FRAME_VALUE = MapTheme.INFO;
  private static final TinyColor COOL_LOW = new TinyColor(16, 78, 180);
  private static final TinyColor COOL_HIGH = new TinyColor(60, 175, 235);
  private static final TinyColor HOT_HIGH = new TinyColor(255, 98, 42);
  private final long scanCacheOwnerId;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Pixel scale used when chunk heatmap base draws each chunk on the map.", impact = "Higher values make chunks larger and reduce visible radius; lower values show more area with finer detail.")
  private int chunkPixelSize = 5;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Map chunks radius used by chunk heatmap base (chunks).", impact = "Higher values widen the search area and cost more work; lower values narrow scope and run cheaper.")
  private int mapRadiusChunks = 0;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Controls whether chunk heatmap base rotates map output with player heading.", impact = "Enable for orientation-aware maps; disable for fixed north-up rendering.")
  private boolean rotateWithPlayer = true;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Controls whether chunk heatmap base renders draw center marker on map output.", impact = "Enable to show this visual layer; disable for a cleaner map and slightly lower render cost.")
  private boolean drawCenterMarker = true;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Controls whether chunk heatmap base renders draw label on map output.", impact = "Enable to show this visual layer; disable for a cleaner map and slightly lower render cost.")
  private boolean drawLabel = true;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Minimum peak chunk score required before the heatmap renders activity.", impact = "Below this the map shows an explicit quiet state instead of amplifying measurement noise into full-scale colors.")
  private double minSignificantScore = 0.001;

  protected FeatureChunkHeatmapBase(String id) {
    super(id);
    scanCacheOwnerId = NEXT_SCAN_CACHE_OWNER_ID.incrementAndGet();
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
    return Math.max(0D, chunkScore(world, chunkX, chunkZ));
  }

  @Override
  public MegamapGrid.MegamapCapability megamapCapability() {
    return MegamapGrid.MegamapCapability.adaptiveWall();
  }

  @Override
  public void render() {
    Player viewer = player();
    MapController mapController = React.controller(MapController.class);
    Location anchor = mapController == null ? (viewer == null ? null : viewer.getLocation().clone()) : mapController.resolveMapAnchor(view(), viewer);
    World mapWorld = view() != null && view().getWorld() != null
        ? view().getWorld()
        : anchor == null ? (viewer == null ? null : viewer.getWorld()) : anchor.getWorld();

    if (mapWorld == null) {
      // No world resolves for this view yet (fresh map, headless render); paint the
      // dashboard shell instead of leaving the frame blank.
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
    int centerChunkX = (int) Math.floor(anchor.getX() / 16D);
    int centerChunkZ = (int) Math.floor(anchor.getZ() / 16D);
    int zoom = effectiveZoom();
    int radius = effectiveRadius(mapWorld, body, zoom);
    double pixelsPerBlock = zoom / 16D;

    int localX = Math.floorMod(anchor.getBlockX(), 16);
    int localZ = Math.floorMod(anchor.getBlockZ(), 16);
    double yaw = rotateWithPlayer && !frameAnchored && viewer != null ? ((-viewer.getLocation().getYaw()) + 180D) : 0D;
    double radians = Math.toRadians(yaw);

    Projection projection = PROJECTION.get();
    projection.centerX = body.centerX();
    projection.centerY = body.centerY();
    projection.centerChunkX = centerChunkX;
    projection.centerChunkZ = centerChunkZ;
    projection.offsetX = -(localX * pixelsPerBlock);
    projection.offsetZ = -(localZ * pixelsPerBlock);
    projection.cos = Math.cos(radians);
    projection.sin = Math.sin(radians);
    projection.rotate = rotateWithPlayer;
    projection.zoom = zoom;
    projection.radiusChunks = radius;
    projection.anchorWorldId = mapWorld.getUID();
    projection.anchorX = anchor.getX();
    projection.anchorZ = anchor.getZ();
    projection.frameAnchored = frameAnchored;

    ScanCache scan = scan(mapWorld, centerChunkX, centerChunkZ, radius);
    Map<LoadedChunkCoordinate, Double> score = scan.score;
    double min = scan.min;
    double max = scan.max;
    boolean quiet = max < minSignificantScore;

    pushClip(body);
    try {
      Region drawBounds = clipRegion().intersect(tileRegion());
      if (!quiet && !drawBounds.isEmpty()) {
        boolean axisAligned = projection.axisAligned();
        for (Map.Entry<LoadedChunkCoordinate, Double> entry : score.entrySet()) {
          LoadedChunkCoordinate chunk = entry.getKey();
          int baseX = (chunk.chunkX() - centerChunkX) * zoom;
          int baseZ = (chunk.chunkZ() - centerChunkZ) * zoom;
          Region cell = projection.cellBounds(baseX, baseZ, zoom);
          if (!cell.intersects(drawBounds)) {
            continue;
          }

          double normalized = normalize(entry.getValue(), min, max);
          TinyColor color = colorFor(normalized, entry.getValue());
          if (axisAligned) {
            set(cell.x(), cell.y(), zoom, zoom, color);
            continue;
          }

          for (int dx = 0; dx < zoom; dx++) {
            for (int dz = 0; dz < zoom; dz++) {
              set(
                  projection.projectedX(baseX + dx, baseZ + dz),
                  projection.projectedY(baseX + dx, baseZ + dz),
                  color
              );
            }
          }
        }
      }

      if (drawCenterMarker) {
        TinyColor centerColor = frameAnchored ? FRAME_CENTER : HELD_CENTER;
        int marker = Math.max(1, uiScale());
        set(body.centerX() - marker, body.centerY(), (2 * marker) + 1, 1, centerColor);
        set(body.centerX(), body.centerY() - marker, 1, (2 * marker) + 1, centerColor);
      }

      if (quiet) {
        String message = ReactLanguage.raw(RendererMessages.HEATMAP_NO_ACTIVITY);
        textCenteredIn(body, Math.max(0, (body.height() - textHeight()) / 2), message, MapTheme.TEXT_MUTED);
      }

      renderOverlay(quiet ? Map.of() : score, min, max);
    } finally {
      popClip();
    }

    dashHeader(
        drawLabel ? mapLabel() : null,
        frameAnchored ? ReactLanguage.raw(RendererMessages.STATUS_FRAME) : null,
        headerColor(frameAnchored),
        FRAME_VALUE
    );
    drawLegend(min, max, quiet, radius, frameAnchored);
  }

  protected int effectiveZoom() {
    int zoom = Math.max(1, chunkPixelSize);
    // Megamap detail tiers on tile count, so a 1xN strip gets promoted like a square
    // wall even though neither axis gained room. Key off the canvas instead: zoom only
    // shrinks once the shorter axis actually spans more than one map.
    if (canvasSpan() >= 2) {
      return Math.max(2, zoom - 2);
    }
    return zoom;
  }

  protected int effectiveRadius(World world, Region body, int zoom) {
    int base = mapRadiusChunks > 0 ? mapRadiusChunks : Math.max(2, world.getViewDistance() * 2);
    int expansion = canvasExtent();
    int fit = (Math.max(body.width(), body.height()) / (2 * Math.max(1, zoom))) + 2;
    return Math.max(2, Math.min(fit, base * expansion));
  }

  // Maps spanning the shorter canvas axis: 1 for a single map, for a 1xN strip and for
  // any magnified megamap that fell back to a 128px canvas, 2 for a 2x2 wall.
  private int canvasSpan() {
    return Math.max(1, Math.min(width(), height()) / ReactRenderer.CANVAS_SIZE);
  }

  // Maps spanning the longer canvas axis, which bounds how far the loaded-chunk scan
  // may reach past the configured radius.
  private int canvasExtent() {
    return Math.max(1, Math.max(width(), height()) / ReactRenderer.CANVAS_SIZE);
  }

  protected Pixel projectChunk(LoadedChunkCoordinate chunk) {
    Projection projection = PROJECTION.get();
    int zoom = Math.max(1, projection.zoom);
    return projection.project(
        ((chunk.chunkX() - projection.centerChunkX) * zoom) + (zoom / 2),
        ((chunk.chunkZ() - projection.centerChunkZ) * zoom) + (zoom / 2)
    );
  }

  private ScanCache scan(World world, int centerChunkX, int centerChunkZ, int radius) {
    ScanCache cache = SCAN_CACHE.get();
    long now = System.currentTimeMillis();
    UUID worldId = world.getUID();
    if (cache.ownerId == scanCacheOwnerId
        && worldId.equals(cache.world)
        && cache.centerX == centerChunkX
        && cache.centerZ == centerChunkZ
        && cache.radius == radius
        && (now - cache.stampMs) < SCAN_CACHE_TTL_MS) {
      return cache;
    }

    cache.ownerId = scanCacheOwnerId;
    cache.world = worldId;
    cache.centerX = centerChunkX;
    cache.centerZ = centerChunkZ;
    cache.radius = radius;
    cache.stampMs = now;
    cache.score.clear();

    double max = 0D;
    double min = Double.MAX_VALUE;
    ObserverController observer = React.controller(ObserverController.class);
    if (observer == null) {
      cache.min = 0D;
      cache.max = 0D;
      return cache;
    }

    HeatmapWorldRef heatmapWorld = observer.heatmapWorld(worldId).orElse(null);
    if (heatmapWorld == null) {
      cache.min = 0D;
      cache.max = 0D;
      return cache;
    }

    for (LoadedChunkCoordinate chunk : observer.loadedChunkCoordinatesInRadius(
        worldId,
        centerChunkX,
        centerChunkZ,
        radius
    )) {

      double value = scoreChunk(heatmapWorld, chunk.chunkX(), chunk.chunkZ());
      if (value <= 0D) {
        continue;
      }

      cache.score.put(chunk, value);
      max = Math.max(max, value);
      min = Math.min(min, value);
    }

    if (cache.score.isEmpty()) {
      min = 0D;
      max = 0D;
    }

    cache.min = min;
    cache.max = max;
    return cache;
  }

  protected TinyColor backgroundColor() {
    return new TinyColor(6, 8, 12);
  }

  protected TinyColor headerColor(boolean frameAnchored) {
    TinyColor base = headerColor();
    if (!frameAnchored) {
      return base;
    }
    return tint(base, 1.12D);
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

  private TinyColor tint(TinyColor color, double factor) {
    int r = (int) Math.round(color.getRed() * factor);
    int g = (int) Math.round(color.getGreen() * factor);
    int b = (int) Math.round(color.getBlue() * factor);
    return new TinyColor(
        Math.max(0, Math.min(255, r)),
        Math.max(0, Math.min(255, g)),
        Math.max(0, Math.min(255, b))
    );
  }

  protected Pixel projectBlockDelta(ProjectionAnchor anchor, double targetX, double targetZ) {
    Projection projection = PROJECTION.get();
    double pixelsPerBlock = Math.max(1, projection.zoom) / 16D;
    double a = (targetX - anchor.x()) * pixelsPerBlock;
    double b = (targetZ - anchor.z()) * pixelsPerBlock;
    return projection.projectRaw(a, b);
  }

  protected double visibleRadiusBlocks() {
    return (Math.max(0, PROJECTION.get().radiusChunks) + 1D) * 16D;
  }

  protected ProjectionAnchor projectionAnchor() {
    Projection projection = PROJECTION.get();
    UUID worldId = projection.anchorWorldId;
    if (worldId == null) {
      return null;
    }
    return new ProjectionAnchor(worldId, projection.anchorX, projection.anchorZ);
  }

  protected boolean projectionFrameAnchored() {
    return PROJECTION.get().frameAnchored;
  }

  private void drawLegend(double min, double max, boolean quiet, int radius, boolean frameAnchored) {
    Region footer = footerRegion();
    if (footer.isEmpty()) {
      return;
    }

    int scale = uiScale();
    fill(footer, MapTheme.SURFACE_2);
    hSeparator(footer, 0, headerColor(frameAnchored));

    Region content = RendererLayout.footerContent(this);
    if (content.isEmpty()) {
      return;
    }

    int baseline = RendererLayout.baseline(this, content);
    String scaleLabel = ReactLanguage.raw(
        MapMessages.SCALE_CHUNKS,
        MessageArgument.untrusted("radius", Integer.toString(radius))
    );

    if (quiet) {
      textIn(content, 0, baseline, ReactLanguage.raw(RendererMessages.HEATMAP_QUIET), MapTheme.TEXT_MUTED);
      textRightIn(content, 0, baseline, scaleLabel, MapTheme.TEXT_MUTED);
      return;
    }

    String lowLabel = compact(min);
    String highLabel = compact(max);
    int gutter = MapTheme.gutter(scale);
    int lowWidth = textWidth(lowLabel);
    int highWidth = textWidth(highLabel);

    textIn(content, 0, baseline, lowLabel, MapTheme.TEXT_MUTED);
    textRightIn(content, 0, baseline, highLabel, MapTheme.TEXT_MUTED);

    Region ramp = content
        .withoutLeft(lowWidth + gutter)
        .withoutRight(highWidth + gutter)
        .inset(0, 2 * scale, 0, 2 * scale);
    if (ramp.width() < (RAMP_STEPS * scale)) {
      return;
    }

    double range = Math.max(0.0001D, max - min);
    int stepWidth = Math.max(1, ramp.width() / RAMP_STEPS);
    for (int step = 0; step < RAMP_STEPS; step++) {
      double n = step / (double) (RAMP_STEPS - 1);
      int x = ramp.x() + (step * stepWidth);
      int width = step == RAMP_STEPS - 1 ? ramp.right() - x : stepWidth;
      fill(new Region(x, ramp.y(), width, ramp.height()), colorFor(n, min + (range * n)));
    }
  }

  private String compact(double value) {
    if (!Double.isFinite(value)) {
      return "0";
    }
    if (Math.abs(value) >= 1000D) {
      return String.format("%.1fk", value / 1000D);
    }
    if (Math.abs(value) >= 100D) {
      return String.format("%.0f", value);
    }
    if (Math.abs(value) >= 10D) {
      return String.format("%.1f", value);
    }
    return String.format("%.2f", value);
  }

  private double normalize(double value, double min, double max) {
    // With a single distinct score there is no relative scale; render mid-heat
    // instead of painting trivial lone activity as maximum pressure.
    if (max <= min + 0.0001D) {
      return value > 0D ? 0.5D : 0D;
    }

    return Math.max(0D, Math.min(1D, (value - min) / (max - min)));
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
    private int centerX = 63;
    private int centerY = 63;
    private int centerChunkX;
    private int centerChunkZ;
    private int zoom = 1;
    private int radiusChunks;
    private double offsetX;
    private double offsetZ;
    private double cos = 1D;
    private double sin;
    private boolean rotate;
    private UUID anchorWorldId;
    private double anchorX;
    private double anchorZ;
    private boolean frameAnchored;

    private Pixel project(double localX, double localZ) {
      return projectRaw(localX + offsetX, localZ + offsetZ);
    }

    private Pixel projectRaw(double a, double b) {
      int rx = rotate ? (int) Math.round((cos * a) - (sin * b)) : (int) Math.round(a);
      int rz = rotate ? (int) Math.round((sin * a) + (cos * b)) : (int) Math.round(b);
      return new Pixel(centerX + rx, centerY + rz);
    }

    private boolean axisAligned() {
      return !rotate || (sin == 0D && cos == 1D);
    }

    private int projectedX(double localX, double localZ) {
      double a = localX + offsetX;
      double b = localZ + offsetZ;
      return centerX + (rotate ? (int) Math.round((cos * a) - (sin * b)) : (int) Math.round(a));
    }

    private int projectedY(double localX, double localZ) {
      double a = localX + offsetX;
      double b = localZ + offsetZ;
      return centerY + (rotate ? (int) Math.round((sin * a) + (cos * b)) : (int) Math.round(b));
    }

    private Region cellBounds(int baseX, int baseZ, int zoom) {
      int span = Math.max(1, zoom);
      if (axisAligned()) {
        return new Region(projectedX(baseX, baseZ), projectedY(baseX, baseZ), span, span);
      }

      int edge = span - 1;
      int minX = Integer.MAX_VALUE;
      int minY = Integer.MAX_VALUE;
      int maxX = Integer.MIN_VALUE;
      int maxY = Integer.MIN_VALUE;
      for (int corner = 0; corner < 4; corner++) {
        int cornerX = baseX + ((corner & 1) == 0 ? 0 : edge);
        int cornerZ = baseZ + ((corner & 2) == 0 ? 0 : edge);
        int px = projectedX(cornerX, cornerZ);
        int py = projectedY(cornerX, cornerZ);
        minX = Math.min(minX, px);
        maxX = Math.max(maxX, px);
        minY = Math.min(minY, py);
        maxY = Math.max(maxY, py);
      }

      return new Region(minX, minY, (maxX - minX) + 1, (maxY - minY) + 1);
    }
  }

  private static final class ScanCache {
    private final HashMap<LoadedChunkCoordinate, Double> score = new HashMap<>();
    private long ownerId;
    private UUID world;
    private int centerX;
    private int centerZ;
    private int radius;
    private long stampMs;
    private double min;
    private double max;
  }
}
