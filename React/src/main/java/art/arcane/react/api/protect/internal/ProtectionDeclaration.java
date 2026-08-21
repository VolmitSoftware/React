package art.arcane.react.api.protect.internal;

import art.arcane.react.api.protect.ReactProtectionRule;

import java.util.List;
import java.util.Objects;

public record ProtectionDeclaration(String providerId, String pluginName, List<ReactProtectionRule> rules) {
  public ProtectionDeclaration {
    Objects.requireNonNull(providerId, "providerId");
    pluginName = pluginName == null ? "" : pluginName;
    rules = rules == null ? List.of() : List.copyOf(rules);
  }
}
