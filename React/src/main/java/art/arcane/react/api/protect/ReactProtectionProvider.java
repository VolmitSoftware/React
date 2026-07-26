package art.arcane.react.api.protect;

import java.util.List;

public interface ReactProtectionProvider {
  List<ReactProtectionRule> rules();

  default String providerId() {
    return getClass().getName();
  }
}
