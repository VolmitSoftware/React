package art.arcane.react.core.incident;

import java.util.List;
import java.util.Map;

public record IncidentRecord(
    String id,
    String incidentId,
    String kind,
    String phase,
    String severity,
    long occurredAtMs,
    long startedAtMs,
    String source,
    String title,
    String summary,
    String cause,
    IncidentLocation location,
    List<IncidentEvidence> evidence,
    List<IncidentAction> actions,
    Map<String, String> context
) {
  public IncidentRecord {
    evidence = evidence == null ? List.of() : List.copyOf(evidence);
    actions = actions == null ? List.of() : List.copyOf(actions);
    context = context == null ? Map.of() : Map.copyOf(context);
  }
}
