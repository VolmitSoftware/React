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
import art.arcane.react.api.rendering.ReactRenderer;
import art.arcane.react.core.controller.MapController;
import art.arcane.react.core.controller.ObserverController;
import art.arcane.react.model.SampledChunk;
import art.arcane.react.util.data.TinyColor;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;

@art.arcane.react.util.config.ConfigDescription("Configuration for Chunk Heatmap Base feature. This feature continuously monitors server behavior and applies guardrails during runtime.")
abstract class FeatureChunkHeatmapBase extends ReactFeature implements ReactRenderer {
  @art.arcane.react.util.config.ConfigDoc(value = "Pixel scale used when chunk heatmap base draws each chunk on the map.", impact = "Higher values make chunks larger and reduce visible radius; lower values show more area with finer detail.")
  private int chunkPixelSize = 5;
  @art.arcane.react.util.config.ConfigDoc(value = "Map chunks radius used by chunk heatmap base (chunks).", impact = "Higher values widen the search area and cost more work; lower values narrow scope and run cheaper.")
  private int mapRadiusChunks = 0;
  @art.arcane.react.util.config.ConfigDoc(value = "Controls whether chunk heatmap base rotates map output with player heading.", impact = "Enable for orientation-aware maps; disable for fixed north-up rendering.")
  private boolean rotateWithPlayer = true;
  @art.arcane.react.util.config.ConfigDoc(value = "Controls whether chunk heatmap base renders draw center marker on map output.", impact = "Enable to show this visual layer; disable for a cleaner map and slightly lower render cost.")
  private boolean drawCenterMarker = true;
  @art.arcane.react.util.config.ConfigDoc(value = "Controls whether chunk heatmap base renders draw label on map output.", impact = "Enable to show this visual layer; disable for a cleaner map and slightly lower render cost.")
  private boolean drawLabel = true;

  protected FeatureChunkHeatmapBase(String id) {
    super(id);
  }

  @Override
  public void onActivate() {

  }

  @Override
  public void onDeactivate() {

  }

  @Override
  public int getTickInterval() {
    return -1;
  }

  @Override
  public void onTick() {

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

    clear(backgroundColor());
    set(0, 0, width(), 10, headerColor(frameAnchored));
    set(120, 2, 5, 5, frameAnchored ? new TinyColor(96, 188, 255) : new TinyColor(88, 230, 140));

    Chunk center = anchor.getChunk();
    int radius = mapRadiusChunks > 0 ? mapRadiusChunks : Math.max(2, mapWorld.getViewDistance() * 2);
    int zoom = Math.max(1, chunkPixelSize);
    double pixelsPerBlock = zoom / 16D;

    int localX = Math.floorMod(anchor.getBlockX(), 16);
    int localZ = Math.floorMod(anchor.getBlockZ(), 16);
    double ox = -(localX * pixelsPerBlock);
    double oz = -(localZ * pixelsPerBlock);

    Map<Chunk, Double> score = new HashMap<>();
    double max = 0D;
    double min = Double.MAX_VALUE;

    for (Chunk chunk : mapWorld.getLoadedChunks()) {
      if (!within(center, chunk, radius)) {
        continue;
      }

      double value = Math.max(0D, chunkScore(chunk));
      if (value <= 0D) {
        continue;
      }

      score.put(chunk, value);
      max = Math.max(max, value);
      min = Math.min(min, value);
    }

    double yaw = rotateWithPlayer && !frameAnchored && viewer != null ? ((-viewer.getLocation().getYaw()) + 180D) : 0D;
    double radians = Math.toRadians(yaw);
    double cos = Math.cos(radians);
    double sin = Math.sin(radians);

    for (Map.Entry<Chunk, Double> entry : score.entrySet()) {
      Chunk chunk = entry.getKey();
      double normalized = normalize(entry.getValue(), min, max);
      TinyColor color = colorFor(normalized, entry.getValue());
      int baseX = (chunk.getX() - center.getX()) * zoom;
      int baseZ = (chunk.getZ() - center.getZ()) * zoom;

      for (int dx = 0; dx < zoom; dx++) {
        for (int dz = 0; dz < zoom; dz++) {
          double a = baseX + dx + ox;
          double b = baseZ + dz + oz;
          int rx = rotateWithPlayer ? (int) Math.round((cos * a) - (sin * b)) : (int) Math.round(a);
          int rz = rotateWithPlayer ? (int) Math.round((sin * a) + (cos * b)) : (int) Math.round(b);
          set(63 + rx, 63 + rz, color);
        }
      }
    }

    if (drawCenterMarker) {
      TinyColor centerColor = frameAnchored ? new TinyColor(102, 180, 255) : new TinyColor(0, 255, 90);
      set(63, 63, centerColor);
      set(62, 63, centerColor);
      set(64, 63, centerColor);
      set(63, 62, centerColor);
      set(63, 64, centerColor);
    }

    drawLegend(min, max);

    renderOverlay(score, min, max);

    if (drawLabel) {
      String label = mapLabel();
      if (label != null && !label.isBlank()) {
        text(3, 2, label);
      }
    }
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

  protected abstract double chunkScore(Chunk chunk);

  protected TinyColor colorFor(double normalized, double rawScore) {
    if (normalized < 0.5D) {
      return gradient(normalized * 2D, new TinyColor(16, 78, 180), new TinyColor(60, 175, 235));
    }
    return gradient((normalized - 0.5D) * 2D, new TinyColor(60, 175, 235), new TinyColor(255, 98, 42));
  }

  protected void renderOverlay(Map<Chunk, Double> score, double min, double max) {

  }

  protected double chunkSample(Chunk chunk, String samplerId) {
    var sampler = React.sampler(samplerId);
    return sampler == null ? 0D : Math.max(0D, sampler.sample(chunk));
  }

  protected double chunkTotalScore(Chunk chunk) {
    ObserverController observer = React.controller(ObserverController.class);
    if (observer == null || observer.getSampled() == null) {
      return 0D;
    }

    return observer.getSampled().optionalChunk(chunk).map(SampledChunk::totalScore).orElse(0D);
  }

  protected TinyColor gradient(double normalized, TinyColor low, TinyColor high) {
    double n = Math.max(0D, Math.min(1D, normalized));
    int r = (int) Math.round((low.getColor().getRed() * (1D - n)) + (high.getColor().getRed() * n));
    int g = (int) Math.round((low.getColor().getGreen() * (1D - n)) + (high.getColor().getGreen() * n));
    int b = (int) Math.round((low.getColor().getBlue() * (1D - n)) + (high.getColor().getBlue() * n));
    return new TinyColor(r, g, b);
  }

  private TinyColor tint(TinyColor color, double factor) {
    int r = (int) Math.round(color.getColor().getRed() * factor);
    int g = (int) Math.round(color.getColor().getGreen() * factor);
    int b = (int) Math.round(color.getColor().getBlue() * factor);
    return new TinyColor(
        Math.max(0, Math.min(255, r)),
        Math.max(0, Math.min(255, g)),
        Math.max(0, Math.min(255, b))
    );
  }

  protected Pixel projectBlockDelta(Location viewerLocation, Location targetLocation) {
    double dxBlocks = targetLocation.getX() - viewerLocation.getX();
    double dzBlocks = targetLocation.getZ() - viewerLocation.getZ();
    double pixelsPerBlock = Math.max(1, chunkPixelSize) / 16D;
    double a = dxBlocks * pixelsPerBlock;
    double b = dzBlocks * pixelsPerBlock;
    double yaw = rotateWithPlayer ? ((-viewerLocation.getYaw()) + 180D) : 0D;
    double radians = Math.toRadians(yaw);
    double cos = Math.cos(radians);
    double sin = Math.sin(radians);
    int rx = rotateWithPlayer ? (int) Math.round((cos * a) - (sin * b)) : (int) Math.round(a);
    int rz = rotateWithPlayer ? (int) Math.round((sin * a) + (cos * b)) : (int) Math.round(b);
    return new Pixel(63 + rx, 63 + rz);
  }

  private void drawLegend(double min, double max) {
    int x0 = 4;
    int x1 = 90;
    int y0 = 118;
    int y1 = 124;

    double range = Math.max(0.0001D, max - min);
    for (int x = x0; x <= x1; x++) {
      double n = (x - x0) / (double) Math.max(1, (x1 - x0));
      double raw = min + (range * n);
      TinyColor c = colorFor(n, raw);
      for (int y = y0; y <= y1; y++) {
        set(x, y, c);
      }
    }

    text(3, 108, "L:" + compact(min));
    text(94, 108, "H:" + compact(max));
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

  private boolean within(Chunk a, Chunk b, int radius) {
    int dx = a.getX() - b.getX();
    int dz = a.getZ() - b.getZ();
    return (dx * dx) + (dz * dz) <= radius * radius;
  }

  private double normalize(double value, double min, double max) {
    if (max <= min + 0.0001D) {
      return value > 0D ? 1D : 0D;
    }

    return Math.max(0D, Math.min(1D, (value - min) / (max - min)));
  }

  protected static final class Pixel {
    @art.arcane.react.util.config.ConfigDoc(value = "X-axis coordinate used by chunk heatmap base internal tracking.", impact = "This is internal state data and should not normally be changed manually.")
    private final int x;
    @art.arcane.react.util.config.ConfigDoc(value = "Y-axis coordinate used by chunk heatmap base internal tracking.", impact = "This is internal state data and should not normally be changed manually.")
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
}
