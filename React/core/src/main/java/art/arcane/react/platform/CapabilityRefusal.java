package art.arcane.react.platform;

import java.util.Objects;

public record CapabilityRefusal(PlatformCapability capability, String reason) {
  public CapabilityRefusal {
    Objects.requireNonNull(capability, "capability");
    reason = reason == null || reason.isBlank() ? capability.label() + " is unavailable" : reason.strip();
  }

  public static CapabilityRefusal unsupported(PlatformCapability capability, String platformName) {
    return new CapabilityRefusal(capability, platformName + " does not provide " + capability.label());
  }
}
