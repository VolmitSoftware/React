package art.arcane.react.api.metric;

import java.util.List;

public interface ReactMetricSource {
  String sourceId();

  List<ReactMetric> metrics();
}
