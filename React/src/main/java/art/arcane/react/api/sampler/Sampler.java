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

package art.arcane.react.api.sampler;

import art.arcane.react.React;
import art.arcane.react.api.rendering.Graph;
import art.arcane.react.api.rendering.MapColors;
import art.arcane.react.api.rendering.MegamapGrid;
import art.arcane.react.api.rendering.ReactRenderer;
import art.arcane.react.core.controller.ObserverController;
import art.arcane.react.core.controller.SampleController;
import art.arcane.react.localization.ReactLanguage;
import art.arcane.react.localization.catalog.MapMessages;
import art.arcane.react.localization.catalog.RendererMessages;
import art.arcane.react.util.common.scheduling.J;
import art.arcane.react.util.data.TinyColor;
import art.arcane.react.util.project.registry.Registered;
import art.arcane.volmlib.util.math.M;
import art.arcane.volmlib.util.localization.MessageArgument;
import com.google.common.util.concurrent.AtomicDouble;
import net.kyori.adventure.text.Component;
import org.bukkit.Chunk;
import org.bukkit.block.Block;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public interface Sampler extends Registered, ReactRenderer {
  Map<String, TinyColor[]> PALETTE_CACHE = new ConcurrentHashMap<>();

  double sample();

  default double sample(Chunk c) {
    return React.controller(ObserverController.class).sample(c, this).orElse(0D);
  }

  @Override
  default MegamapGrid.MegamapCapability megamapCapability() {
    return MegamapGrid.MegamapCapability.adaptiveWall();
  }

  default void render() {
    String normalizedId = normalizeSamplerId(getId());
    TinyColor[] palette = paletteFor(normalizedId);
    TinyColor fillLow = palette[1];
    TinyColor fillHigh = palette[2];
    TinyColor line = palette[3];
    TinyColor marker = palette[4];
    TinyColor backgroundTop = palette[5];
    TinyColor backgroundBottom = palette[6];

    int w = width();
    int h = height();
    int s = uiScale();
    // One sample per logical pixel: a single map keeps the historical 128 sample
    // window, a wider wall shows proportionally more history instead of stretching
    // the same 128 samples across the extra width.
    int samples = Math.max(1, Math.min(Graph.CAPACITY, w));

    Graph g = Graph.of(this);
    double now = g.get(0);
    double min = g.getMin(samples);
    double max = g.getMax(samples);
    double pmax = g.getPaddedMax(0.15, samples);
    double pmin = g.getPaddedMin(0.15, samples);
    double range = pmax - pmin;
    if (!Double.isFinite(range) || Math.abs(range) < 1.0E-9D) {
      range = 1D;
      pmax = max + 0.5D;
      pmin = min - 0.5D;
    }

    // Auto-scaling a near-flat series amplifies measurement jitter into full-height
    // swings; enforce a minimum span relative to the value magnitude so steady
    // readings render as a steady line.
    double magnitude = Math.max(Math.abs(min), Math.abs(max));
    double minSpan = magnitude * 0.25D;
    if (minSpan > 0D && range < minSpan) {
      double mid = (pmax + pmin) / 2D;
      pmin = mid - (minSpan / 2D);
      pmax = mid + (minSpan / 2D);
      range = minSpan;
    }

    int headerH = 14 * s;
    int footerH = 12 * s;
    int footerY = h - footerH;
    int chartTop = headerH + (2 * s);
    int chartBottom = h - (18 * s);
    int topRgb = backgroundTop.toRGB();
    int bottomRgb = backgroundBottom.toRGB();
    int bgSpan = Math.max(1, footerY - headerH - 1);
    int bgY0 = Math.max(headerH, clipY0());
    int bgY1 = Math.min(footerY, clipY1());
    for (int y = bgY0; y < bgY1; y++) {
      double n = (y - headerH) / (double) bgSpan;
      fillRgb(0, y, w, 1, MapColors.lerpRgb(n, topRgb, bottomRgb));
    }

    int gridRgb = MapColors.lerpRgb(0.16D, bottomRgb, line.toRGB());
    // Dot pitch tracks uiScale the same way the 24 * s row pitch does, and the run is
    // snapped onto the dot lattice at the visible tile instead of spanning the wall.
    int dotPitch = 4 * s;
    int gridX0 = (Math.max(0, clipX0()) / dotPitch) * dotPitch;
    int gridX1 = Math.min(w, clipX1());
    int gridY0 = clipY0();
    int gridY1 = clipY1();
    for (int y = chartTop + (23 * s); y < chartBottom; y += 24 * s) {
      if (y < gridY0 || y >= gridY1) {
        continue;
      }

      for (int x = gridX0; x < gridX1; x += dotPitch) {
        setRgb(x, y, gridRgb);
      }
    }

    int fillLowRgb = fillLow.toRGB();
    int fillHighRgb = fillHigh.toRGB();
    int glowRgb = MapColors.lerpRgb(0.45D, bottomRgb, line.toRGB());
    int chartSpan = Math.max(1, chartBottom - chartTop);
    int chartX0 = Math.max(0, clipX0() - 1);
    int chartX1 = Math.min(w, clipX1() + 1);
    int fillY0 = Math.max(chartTop, clipY0());
    int fillY1 = Math.min(chartBottom, clipY1() - 1);
    int prevX = -1;
    int prevY = -1;
    for (int x = chartX0; x < chartX1; x++) {
      int y = chartYFor(g, x, w, samples, pmin, range, chartTop, chartBottom);
      int fillTop = Math.max(y + 1, fillY0);
      for (int fill = fillY1; fill >= fillTop; fill--) {
        double depth = (chartBottom - (double) fill) / chartSpan;
        setRgb(x, fill, MapColors.lerpRgb(depth, fillLowRgb, fillHighRgb));
      }

      if (prevX >= 0) {
        line(prevX, Math.min(chartBottom, prevY + 1), x, Math.min(chartBottom, y + 1), new TinyColor(glowRgb));
      }
      prevX = x;
      prevY = y;
    }

    prevX = -1;
    prevY = -1;
    for (int x = chartX0; x < chartX1; x++) {
      int y = chartYFor(g, x, w, samples, pmin, range, chartTop, chartBottom);
      if (prevX >= 0) {
        line(prevX, prevY, x, y, line);
      }
      prevX = x;
      prevY = y;
    }

    int markerX = w - 1;
    int markerY = chartYFor(g, markerX, w, samples, pmin, range, chartTop, chartBottom);
    set(markerX, markerY, marker);
    set(Math.max(0, markerX - 1), markerY, marker);
    set(markerX, Math.max(chartTop, markerY - 1), marker);
    set(markerX, Math.min(chartBottom, markerY + 1), marker);

    String nowLabel = format(now);
    dashHeader(MapMessages.localizedSamplerName(normalizedId, getName()), nowLabel, line, marker);

    set(0, footerY, w, footerH, FOOTER_BAND);
    set(3 * s, footerY + (3 * s), 4 * s, 4 * s, marker);
    text(10 * s, footerY + (2 * s), nowLabel, TEXT_BRIGHT);
    String lowLabel = ReactLanguage.raw(
        RendererMessages.SAMPLER_LOW_VALUE,
        MessageArgument.untrusted("value", formattedValue(min))
    );
    String highLabel = ReactLanguage.raw(
        RendererMessages.SAMPLER_HIGH_VALUE,
        MessageArgument.untrusted("value", formattedValue(max))
    );
    int highX = w - (3 * s) - textWidth(highLabel);
    int lowX = highX - (6 * s) - textWidth(lowLabel);
    int nowEnd = (10 * s) + textWidth(nowLabel);
    if (lowX > nowEnd + (4 * s)) {
      text(lowX, footerY + (2 * s), lowLabel, TEXT_DIM);
      text(highX, footerY + (2 * s), highLabel, TEXT_DIM);
    } else if (highX > nowEnd + (4 * s)) {
      text(highX, footerY + (2 * s), highLabel, TEXT_DIM);
    }
  }

  private int chartYFor(Graph g, int x, int w, int samples, double pmin, double range, int chartTop, int chartBottom) {
    int sampleIndex = (samples - 1) - ((x * samples) / Math.max(1, w));
    double normalized = (g.get(sampleIndex) - pmin) / range;
    if (!Double.isFinite(normalized)) {
      normalized = 0.5D;
    }
    normalized = M.clip(normalized, 0D, 1D);
    int y = (int) M.lerp(chartBottom, chartTop, normalized);
    return Math.max(chartTop, Math.min(chartBottom, y));
  }

  private String normalizeSamplerId(String id) {
    return id == null ? "" : id.toLowerCase();
  }

  private TinyColor[] paletteFor(String normalizedId) {
    return PALETTE_CACHE.computeIfAbsent(normalizedId, this::computePalette);
  }

  private TinyColor[] computePalette(String normalizedId) {
    if (containsAny(normalizedId, "redstone")) {
      return palette(
          new TinyColor(156, 44, 36),
          new TinyColor(96, 18, 14),
          new TinyColor(226, 74, 56),
          new TinyColor(255, 188, 72),
          new TinyColor(255, 230, 120),
          new TinyColor(18, 10, 10),
          new TinyColor(26, 14, 14)
      );
    }
    if (containsAny(normalizedId, "hopper", "fluid", "physics")) {
      return palette(
          new TinyColor(44, 128, 140),
          new TinyColor(18, 62, 78),
          new TinyColor(72, 198, 214),
          new TinyColor(180, 236, 250),
          new TinyColor(226, 252, 255),
          new TinyColor(10, 16, 20),
          new TinyColor(12, 24, 28)
      );
    }
    if (containsAny(normalizedId, "event", "listener", "plugin")) {
      return palette(
          new TinyColor(162, 92, 46),
          new TinyColor(94, 48, 22),
          new TinyColor(228, 128, 62),
          new TinyColor(255, 206, 108),
          new TinyColor(255, 236, 170),
          new TinyColor(18, 14, 10),
          new TinyColor(28, 20, 12)
      );
    }
    if (containsAny(normalizedId, "memory", "gc")) {
      return palette(
          new TinyColor(116, 78, 168),
          new TinyColor(58, 40, 98),
          new TinyColor(166, 122, 224),
          new TinyColor(228, 192, 255),
          new TinyColor(248, 230, 255),
          new TinyColor(12, 10, 18),
          new TinyColor(20, 16, 30)
      );
    }
    if (containsAny(normalizedId, "tick", "incident", "spike", "mspt", "tps")) {
      return palette(
          new TinyColor(166, 94, 42),
          new TinyColor(96, 52, 20),
          new TinyColor(236, 132, 62),
          new TinyColor(255, 196, 94),
          new TinyColor(255, 228, 138),
          new TinyColor(18, 14, 12),
          new TinyColor(26, 20, 16)
      );
    }
    if (containsAny(normalizedId, "entity", "spawn", "player", "ping")) {
      return palette(
          new TinyColor(132, 108, 42),
          new TinyColor(76, 62, 20),
          new TinyColor(198, 170, 72),
          new TinyColor(236, 214, 130),
          new TinyColor(250, 238, 184),
          new TinyColor(16, 14, 10),
          new TinyColor(24, 22, 14)
      );
    }
    if (containsAny(normalizedId, "chunk", "world")) {
      return palette(
          new TinyColor(68, 126, 176),
          new TinyColor(24, 62, 100),
          new TinyColor(102, 176, 236),
          new TinyColor(180, 220, 255),
          new TinyColor(232, 248, 255),
          new TinyColor(10, 14, 20),
          new TinyColor(16, 22, 30)
      );
    }
    if (containsAny(normalizedId, "iris")) {
      return palette(
          new TinyColor(64, 138, 98),
          new TinyColor(30, 74, 56),
          new TinyColor(94, 186, 136),
          new TinyColor(178, 236, 200),
          new TinyColor(224, 252, 238),
          new TinyColor(10, 16, 12),
          new TinyColor(14, 24, 18)
      );
    }
    if (containsAny(normalizedId, "adapt")) {
      return palette(
          new TinyColor(152, 78, 132),
          new TinyColor(82, 42, 74),
          new TinyColor(212, 112, 188),
          new TinyColor(244, 180, 232),
          new TinyColor(252, 220, 246),
          new TinyColor(18, 10, 18),
          new TinyColor(26, 14, 26)
      );
    }
    if (containsAny(normalizedId, "wormhole")) {
      return palette(
          new TinyColor(110, 64, 190),
          new TinyColor(50, 26, 92),
          new TinyColor(150, 96, 235),
          new TinyColor(200, 150, 255),
          new TinyColor(236, 210, 255),
          new TinyColor(12, 8, 20),
          new TinyColor(20, 14, 32)
      );
    }
    if (containsAny(normalizedId, "gloss")) {
      return palette(
          new TinyColor(38, 142, 134),
          new TinyColor(16, 70, 66),
          new TinyColor(66, 206, 192),
          new TinyColor(152, 240, 230),
          new TinyColor(216, 252, 248),
          new TinyColor(8, 16, 15),
          new TinyColor(12, 24, 22)
      );
    }
    if (containsAny(normalizedId, "hiddenore")) {
      return palette(
          new TinyColor(170, 126, 44),
          new TinyColor(94, 66, 20),
          new TinyColor(228, 178, 66),
          new TinyColor(255, 222, 122),
          new TinyColor(255, 242, 180),
          new TinyColor(18, 14, 8),
          new TinyColor(26, 20, 12)
      );
    }
    if (containsAny(normalizedId, "biletools")) {
      return palette(
          new TinyColor(96, 150, 50),
          new TinyColor(48, 84, 22),
          new TinyColor(142, 210, 74),
          new TinyColor(198, 244, 132),
          new TinyColor(232, 252, 190),
          new TinyColor(12, 16, 8),
          new TinyColor(18, 26, 12)
      );
    }
    if (containsAny(normalizedId, "processor", "cpu", "load", "react", "job", "queue", "backlog")) {
      return palette(
          new TinyColor(46, 126, 154),
          new TinyColor(18, 64, 86),
          new TinyColor(82, 182, 214),
          new TinyColor(168, 226, 246),
          new TinyColor(220, 248, 255),
          new TinyColor(10, 14, 18),
          new TinyColor(16, 20, 24)
      );
    }

    return palette(
        new TinyColor(34, 98, 156),
        new TinyColor(18, 72, 122),
        new TinyColor(54, 174, 210),
        new TinyColor(245, 192, 80),
        new TinyColor(255, 110, 58),
        new TinyColor(8, 12, 18),
        new TinyColor(14, 20, 28)
    );
  }

  private TinyColor[] palette(
      TinyColor header,
      TinyColor fillLow,
      TinyColor fillHigh,
      TinyColor line,
      TinyColor marker,
      TinyColor backgroundTop,
      TinyColor backgroundBottom
  ) {
    return new TinyColor[]{header, fillLow, fillHigh, line, marker, backgroundTop, backgroundBottom};
  }

  private boolean containsAny(String value, String... words) {
    if (value == null || words == null) {
      return false;
    }

    for (String word : words) {
      if (word != null && !word.isBlank() && value.contains(word)) {
        return true;
      }
    }
    return false;
  }

  default String format(double t) {
    return formattedValue(t) + " " + formattedSuffix(t);
  }

  default AtomicDouble getChunkCounter(Chunk c) {
    return React.controller(ObserverController.class).get(c, this);
  }

  default AtomicDouble getChunkCounter(Block b) {
    return React.controller(ObserverController.class).get(b, this);
  }

  @Override
  default String getConfigCategory() {
    return "sampler";
  }

  default Component format(Component value, Component suffix) {
    return Component.empty().append(value).append(suffix);
  }

  String formattedValue(double t);

  String formattedSuffix(double t);

  void start();

  void stop();

  default <T> T executeSync(Supplier<T> executor) {
    if (J.isPrimaryThread()) {
      return executor.get();
    }

    return J.sResult(executor);
  }

  default Sampler getSampler(String id) {
    return React.controller(SampleController.class).getSamplers().get(id);
  }

  default String sampleFormatted() {
    return format(sample());
  }

  default String sampleFormatted(Chunk c) {
    return format(sample(c));
  }
}
