package art.arcane.react.api.protect;

import java.util.List;

/** A mod's standing protection rules, registered with {@code ReactModApi.registerProtectionProvider}. */
public interface ReactModProtectionProvider {
  List<ReactModProtectionRule> rules();

  default String providerId() {
    return getClass().getName();
  }
}
