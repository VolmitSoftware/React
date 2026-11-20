package art.arcane.react.platform.bukkit;

import art.arcane.react.React;
import art.arcane.react.core.controller.IncidentController;
import art.arcane.react.core.incident.IncidentRecord;
import art.arcane.react.platform.IncidentHost;

import java.util.List;

public final class BukkitIncidentHost implements IncidentHost {
  @Override
  public void record(IncidentRecord record) {
    IncidentController controller = React.controller(IncidentController.class);
    if (controller != null) {
      controller.record(record);
    }
  }

  @Override
  public List<IncidentRecord> recent(int limit) {
    IncidentController controller = React.controller(IncidentController.class);
    return controller == null ? List.of() : controller.recent(limit);
  }
}
