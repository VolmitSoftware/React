package art.arcane.react.core.history;

import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

public record MetricSnapshot(
    long sequence,
    long capturedAtMs,
    List<MetricSnapshotValue> values,
    Map<String, MetricSnapshotValue> valuesById
) {
  public static MetricSnapshot of(long sequence, long capturedAtMs, List<MetricSnapshotValue> values) {
    List<MetricSnapshotValue> immutableValues = List.copyOf(values);
    Map<String, MetricSnapshotValue> valuesById = new LinkedHashMap<>(immutableValues.size());
    for (MetricSnapshotValue value : immutableValues) {
      valuesById.put(value.id(), value);
    }
    return new MetricSnapshot(
        sequence,
        capturedAtMs,
        immutableValues,
        Map.copyOf(valuesById)
    );
  }

  public static MetricSnapshot empty() {
    return new MetricSnapshot(0L, 0L, List.of(), Map.of());
  }

  public MetricSnapshotValue value(String id) {
    return valuesById.get(id);
  }
}
