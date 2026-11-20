package art.arcane.react.content.sampler;

import art.arcane.react.api.sampler.SamplerHosts;
import art.arcane.react.platform.TickStatsHost;
import art.arcane.react.platform.timing.SaveTimings;

import java.util.Optional;

/**
 * The platform's save timings, for the samplers that only register where saves can be timed.
 */
final class SaveTimingSource {
  private SaveTimingSource() {
  }

  static Optional<SaveTimings> timings() {
    return SamplerHosts.ticks().flatMap(TickStatsHost::saveTimings);
  }
}
