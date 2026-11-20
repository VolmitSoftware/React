package art.arcane.react.api.feature;

import art.arcane.react.core.controller.ReactComponents;
import art.arcane.react.platform.ComponentHost;

import java.util.Optional;
import java.util.Set;

public abstract class ReactCapabilityFeature extends ReactFeature implements CapabilityGatedFeature {
  protected ReactCapabilityFeature(String id) {
    super(id);
  }

  @Override
  public boolean autoRegister() {
    Set<String> required = requiredCapabilities();
    if (required == null || required.isEmpty()) {
      return true;
    }

    Optional<ComponentHost> host = ReactComponents.host();
    for (String capability : required) {
      if (host.isEmpty() || !host.get().isIntegrationInstalled(capability)) {
        return false;
      }
    }

    return true;
  }
}
