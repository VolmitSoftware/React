package art.arcane.react.api.metric;

import java.util.List;

/**
 * A mod's metric declaration, registered with {@code ReactModApi.registerMetricSource}. React calls {@link #metrics}
 * on the server thread about once a minute; values arrive through {@link ReactMetrics#publish}.
 */
public interface ReactModMetricSource {
  String sourceId();

  List<ReactModMetric> metrics();
}
