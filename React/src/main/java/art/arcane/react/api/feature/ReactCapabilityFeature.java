package art.arcane.react.api.feature;

import art.arcane.react.core.integration.IntegrationCapabilitySupport;
import art.arcane.react.model.ReactConfiguration;

import java.util.Set;

public abstract class ReactCapabilityFeature extends ReactFeature implements CapabilityGatedFeature {
  protected ReactCapabilityFeature(String id) {
    super(id);
  }

  @Override
  public boolean autoRegister() {
    if (isSecretBundle() && !ReactConfiguration.get().isIntegrationSecretsEnabled()) {
      return false;
    }

    Set<String> required = requiredCapabilities();
    if (required == null || required.isEmpty()) {
      return true;
    }

    for (String capability : required) {
      if (!IntegrationCapabilitySupport.isPluginInstalled(capability)) {
        return false;
      }
    }

    return true;
  }
}
