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

import art.arcane.react.api.feature.ReactFeature;
import art.arcane.react.api.rendering.MegamapGrid;
import art.arcane.react.api.rendering.ReactRenderer;
import art.arcane.react.api.web.heatmap.ChunkGridExporter;
import art.arcane.react.api.web.heatmap.HeatmapWorldRef;
import art.arcane.react.content.feature.ChunkHeatmapRenderer.Pixel;
import art.arcane.react.content.feature.ChunkHeatmapRenderer.ProjectionAnchor;
import art.arcane.react.core.controller.LoadedChunkCoordinate;
import art.arcane.react.platform.ChunkHeatmapHost;
import art.arcane.react.platform.MapAnchorHost;
import art.arcane.react.platform.bukkit.BukkitChunkHeatmapHost;
import art.arcane.react.platform.bukkit.BukkitMapAnchorHost;
import art.arcane.react.util.data.TinyColor;

import java.util.Map;

/**
 * A chunk heatmap feature. React's own heatmaps return a core {@link ChunkHeatmapRenderer} from
 * {@link #createRenderer}; integration overlays keep the default, which draws through the hook methods below.
 */
@art.arcane.react.util.project.config.ConfigDescription("Configuration for Chunk Heatmap Base feature. This feature continuously monitors server behavior and applies guardrails during runtime.")
abstract class FeatureChunkHeatmapBase extends ReactFeature implements ReactRenderer, ChunkGridExporter, ChunkHeatmapRenderer.Settings {
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
  private transient volatile ChunkHeatmapRenderer renderer;

  protected FeatureChunkHeatmapBase(String id) {
    super(id);
  }

  protected ChunkHeatmapRenderer createRenderer(ChunkHeatmapHost data, MapAnchorHost anchors) {
    return new HookRenderer(this, data, anchors);
  }

  final ChunkHeatmapRenderer renderer() {
    ChunkHeatmapRenderer current = renderer;
    if (current == null) {
      synchronized (this) {
        current = renderer;
        if (current == null) {
          current = createRenderer(BukkitChunkHeatmapHost.INSTANCE, BukkitMapAnchorHost.INSTANCE);
          renderer = current;
        }
      }
    }
    return current;
  }

  @Override
  public int chunkPixelSize() {
    return chunkPixelSize;
  }

  @Override
  public int mapRadiusChunks() {
    return mapRadiusChunks;
  }

  @Override
  public boolean drawCenterMarker() {
    return drawCenterMarker;
  }

  @Override
  public boolean drawLabel() {
    return drawLabel;
  }

  @Override
  public double minSignificantScore() {
    return minSignificantScore;
  }

  @Override
  public void onDeactivate() {
    renderer().clearCache();
  }

  @Override
  public String heatmapId() {
    return getId();
  }

  @Override
  public String heatmapLabel() {
    return renderer().heatmapLabel();
  }

  @Override
  public double scoreChunk(HeatmapWorldRef world, int chunkX, int chunkZ) {
    return renderer().scoreChunk(world, chunkX, chunkZ);
  }

  @Override
  public MegamapGrid.MegamapCapability megamapCapability() {
    return renderer().megamapCapability();
  }

  @Override
  public void render() {
    renderer().render();
  }

  protected TinyColor backgroundColor() {
    return new TinyColor(6, 8, 12);
  }

  protected TinyColor headerColor() {
    return new TinyColor(68, 122, 176);
  }

  protected String mapLabel() {
    return getName();
  }

  protected double chunkScore(HeatmapWorldRef world, int chunkX, int chunkZ) {
    return 0D;
  }

  protected TinyColor colorFor(double normalized, double rawScore) {
    return renderer().defaultColorFor(normalized, rawScore);
  }

  protected void renderOverlay(Map<LoadedChunkCoordinate, Double> score, double min, double max) {
  }

  protected Pixel projectChunk(LoadedChunkCoordinate chunk) {
    return renderer().projectChunk(chunk);
  }

  protected double chunkTotalScore(HeatmapWorldRef world, int chunkX, int chunkZ) {
    return renderer().chunkTotalScore(world, chunkX, chunkZ);
  }

  protected TinyColor gradient(double normalized, TinyColor low, TinyColor high) {
    return renderer().gradient(normalized, low, high);
  }

  protected Pixel projectBlockDelta(ProjectionAnchor anchor, double targetX, double targetZ) {
    return renderer().projectBlockDelta(anchor, targetX, targetZ);
  }

  protected ProjectionAnchor projectionAnchor() {
    return renderer().projectionAnchor();
  }

  private static final class HookRenderer extends ChunkHeatmapRenderer {
    private final FeatureChunkHeatmapBase feature;

    private HookRenderer(FeatureChunkHeatmapBase feature, ChunkHeatmapHost data, MapAnchorHost anchors) {
      super(feature.getId(), data, anchors, feature);
      this.feature = feature;
    }

    @Override
    protected String mapLabel() {
      return feature.mapLabel();
    }

    @Override
    protected TinyColor headerColor() {
      return feature.headerColor();
    }

    @Override
    protected TinyColor backgroundColor() {
      return feature.backgroundColor();
    }

    @Override
    protected double chunkScore(HeatmapWorldRef world, int chunkX, int chunkZ) {
      return feature.chunkScore(world, chunkX, chunkZ);
    }

    @Override
    protected TinyColor colorFor(double normalized, double rawScore) {
      return feature.colorFor(normalized, rawScore);
    }

    @Override
    protected void renderOverlay(Map<LoadedChunkCoordinate, Double> score, double min, double max) {
      feature.renderOverlay(score, min, max);
    }
  }
}
