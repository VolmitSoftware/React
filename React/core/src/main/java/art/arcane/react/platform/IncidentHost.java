package art.arcane.react.platform;

import art.arcane.react.core.incident.IncidentRecord;

import java.util.List;

public interface IncidentHost {
  void record(IncidentRecord record);

  /**
   * The newest records first, at most {@code limit}.
   */
  List<IncidentRecord> recent(int limit);
}
