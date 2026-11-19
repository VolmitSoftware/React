package art.arcane.react.platform;

import art.arcane.react.api.sampler.Sampler;
import art.arcane.react.core.controller.SampleController;
import com.google.common.util.concurrent.AtomicDouble;

import java.util.Optional;

/**
 * Wires the core sampler runtime into the platform: controller lookup, renderer and monitor
 * refreshes, and per-chunk attribution of sampler values.
 */
public interface SamplerHost {
  Optional<SampleController> sampleController();

  /**
   * Called after a sampler started. Platforms that dispatch events to sampler objects attach them here.
   */
  void attachSampler(Sampler sampler);

  void detachSampler(Sampler sampler);

  /**
   * A sampler was added at runtime, after the initial registry scan. Maps show it from now on.
   */
  void samplerRegistered(Sampler sampler);

  void samplerUnregistered(String samplerId);

  /**
   * Rebuilds player monitors. Always called on the main thread.
   */
  void refreshMonitors();

  /**
   * Per-chunk counter for this sampler. {@code chunkOrBlock} is a native chunk or block.
   */
  AtomicDouble chunkCounter(Object chunkOrBlock, Sampler sampler);

  Optional<Double> sampleChunk(Object chunk, Sampler sampler);
}
