package art.arcane.react.api.feature;

import java.util.Set;
import java.util.stream.Collectors;

public interface CapabilityGatedFeature extends Feature {
    Set<String> requiredCapabilities();

    default boolean isSecretBundle() {
        return false;
    }

    default String requirementLabel() {
        Set<String> required = requiredCapabilities();
        if (required == null || required.isEmpty()) {
            return "none";
        }

        return required.stream()
                .map(i -> i == null ? "" : i.trim().toLowerCase())
                .filter(i -> !i.isBlank())
                .sorted()
                .collect(Collectors.joining("+"));
    }
}
