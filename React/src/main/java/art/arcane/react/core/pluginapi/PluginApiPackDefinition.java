package art.arcane.react.core.pluginapi;

import art.arcane.react.api.metric.ReactMetricKind;
import org.bukkit.Material;

import java.nio.file.Path;
import java.util.List;

public record PluginApiPackDefinition(
    String schema,
    String id,
    String version,
    String name,
    List<String> authors,
    boolean enabled,
    boolean trusted,
    String targetPlugin,
    List<String> targetVersions,
    List<MetricDefinition> metrics,
    Path sourcePath,
    String rawContent
) {
  public record MetricDefinition(
      String id,
      String samplerId,
      String displayName,
      ReactMetricKind kind,
      String unit,
      Material icon,
      int decimals,
      long sampleEveryMs,
      long staleAfterMs,
      SourceDefinition source,
      TransformDefinition transform
  ) {
  }

  public record SourceDefinition(
      SourceType type,
      String pluginId,
      String key,
      String placeholder,
      String eventClass,
      boolean foliaSafe
  ) {
  }

  public record TransformDefinition(
      TransformMode mode,
      double scale,
      double offset,
      Double minimum,
      Double maximum
  ) {
  }

  public enum SourceType {
    INTEGRATION,
    PLACEHOLDER,
    ORAXEN,
    EVENT_COUNTER
  }

  public enum TransformMode {
    VALUE,
    DELTA_PER_SECOND
  }
}
