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

import art.arcane.react.core.controller.SampleController;
import art.arcane.react.platform.SamplerHost;
import art.arcane.react.util.common.scheduling.J;
import art.arcane.react.util.project.registry.Registered;
import com.google.common.util.concurrent.AtomicDouble;
import net.kyori.adventure.text.Component;

import java.util.function.Supplier;

public interface Sampler extends Registered {
  double sample();

  default Reading captureReading() {
    double value = capture();
    return reading(value, System.currentTimeMillis(), isSampleAvailable());
  }

  default Metadata metadata() {
    return Metadata.UNKNOWN;
  }

  default String availabilityReason() {
    return "source-unavailable";
  }

  default Diagnostics diagnostics() {
    return Diagnostics.EMPTY;
  }

  default Reading reading(double value, long sampledAtMs, boolean available) {
    return reading(value, sampledAtMs, available, availabilityReason());
  }

  default Reading reading(double value, long sampledAtMs, boolean available, String reason) {
    return new Reading(value, sampledAtMs, available, metadata(), reason);
  }

  enum Quality {
    UNKNOWN,
    MEASURED,
    ESTIMATED
  }

  record Metadata(long intervalMs, long windowMs, Quality quality, String unit) {
    public static final Metadata UNKNOWN = new Metadata(0L, 0L, Quality.UNKNOWN, "");

    public Metadata {
      intervalMs = Math.max(0L, intervalMs);
      windowMs = Math.max(0L, windowMs);
      quality = quality == null ? Quality.UNKNOWN : quality;
      unit = unit == null ? "" : unit.trim();
    }
  }

  record Reading(double value, long sampledAtMs, boolean available, Metadata metadata, String unavailableReason) {
    public Reading {
      metadata = metadata == null ? Metadata.UNKNOWN : metadata;
      if (!Double.isFinite(value)) {
        available = false;
        unavailableReason = "non-finite";
      }
      available = available && sampledAtMs > 0L;
      unavailableReason = available ? "" : unavailableReason == null || unavailableReason.isBlank()
          ? sampledAtMs <= 0L ? "warming-up" : "source-unavailable" : unavailableReason;
    }
  }

  record Diagnostics(long attempts, long successes, long failures, long cacheHits, long inFlightSkips,
                     long lastDurationNanos, long maxDurationNanos, long lastQueueNanos, long maxQueueNanos,
                     long lastSuccessAtMs, long totalDurationNanos, long totalQueueNanos) {
    public static final Diagnostics EMPTY = new Diagnostics(0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L);
  }

  default double capture() {
    return sample();
  }

  default void markDemand() {
  }

  default boolean isSampleAvailable() {
    return true;
  }

  default boolean isChunkGauge() {
    return false;
  }

  /**
   * Value attributed to one chunk. {@code chunk} is a native chunk.
   */
  default double sample(Object chunk) {
    SamplerHost host = SamplerHosts.sampler().orElse(null);
    return host == null ? 0D : host.sampleChunk(chunk, this).orElse(0D);
  }

  default String format(double t) {
    return formattedValue(t) + " " + formattedSuffix(t);
  }

  /**
   * Per-chunk counter for this sampler. {@code chunkOrBlock} is a native chunk or block.
   */
  default AtomicDouble getChunkCounter(Object chunkOrBlock) {
    SamplerHost host = SamplerHosts.sampler().orElse(null);
    return host == null ? new AtomicDouble() : host.chunkCounter(chunkOrBlock, this);
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
    SampleController controller = SamplerHosts.sampleController();
    return controller == null ? null : controller.getSampler(id);
  }

  default String sampleFormatted() {
    return format(sample());
  }

  default String sampleFormatted(Object chunk) {
    return format(sample(chunk));
  }
}
