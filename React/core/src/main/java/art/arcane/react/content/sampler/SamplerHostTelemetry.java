package art.arcane.react.content.sampler;

import art.arcane.react.api.sampler.SamplerHosts;
import art.arcane.react.core.telemetry.HostTelemetrySnapshot;
import art.arcane.react.platform.RuntimeStatsHost;

final class SamplerHostTelemetry {
  private SamplerHostTelemetry() {
  }

  static HostTelemetrySnapshot current() {
    return SamplerHosts.runtime().map(RuntimeStatsHost::hostTelemetry).orElseGet(HostTelemetrySnapshot::empty);
  }
}
