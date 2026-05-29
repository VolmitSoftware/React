package art.arcane.react.core.bridge;

import java.util.List;
import java.util.Optional;

public record NmsBridgeDescriptor(
    String logicalId,
    BridgeKind kind,
    List<String> classNames,
    String memberName,
    List<List<String>> parameterTypeNames,
    String returnTypeName,
    Optional<String> mappingsKey
) {}
