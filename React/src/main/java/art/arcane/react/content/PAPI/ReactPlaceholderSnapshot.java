package art.arcane.react.content.PAPI;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

public record ReactPlaceholderSnapshot(
    Map<String, String> named,
    Map<String, String> samplers,
    Set<String> samplerIds,
    Map<UUID, String> worldMspt
) {
  public ReactPlaceholderSnapshot {
    named = Map.copyOf(named);
    samplers = Map.copyOf(samplers);
    samplerIds = Set.copyOf(samplerIds);
    worldMspt = Map.copyOf(worldMspt);
  }
}
