package art.arcane.react.core.pluginapi;

import art.arcane.react.api.metric.ReactMetricKind;
import art.arcane.react.core.pluginapi.PluginApiPackDefinition.MetricDefinition;
import art.arcane.react.core.pluginapi.PluginApiPackDefinition.SourceDefinition;
import art.arcane.react.core.pluginapi.PluginApiPackDefinition.SourceType;
import art.arcane.react.core.pluginapi.PluginApiPackDefinition.TransformDefinition;
import art.arcane.react.core.pluginapi.PluginApiPackDefinition.TransformMode;
import art.arcane.react.util.project.config.TomlCodec;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.bukkit.Material;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

public final class PluginApiPackParser {
  public static final String SCHEMA = "react.plugin-api/v1";
  public static final int MAX_FILE_BYTES = 256 * 1024;
  public static final int MAX_METRICS = 24;
  public static final long MIN_SAMPLE_MS = 1_000L;
  public static final long MAX_SAMPLE_MS = 10_000L;
  public static final long MAX_STALE_MS = 60_000L;

  private static final Gson GSON = new Gson();
  private static final Pattern ID = Pattern.compile("[a-z0-9][a-z0-9._-]{1,47}");
  private static final Pattern METRIC_ID = Pattern.compile("[a-z0-9][a-z0-9_-]{1,47}");
  private static final Pattern CLASS_NAME = Pattern.compile("[A-Za-z_$][A-Za-z0-9_$]*(\\.[A-Za-z_$][A-Za-z0-9_$]*)+");
  private static final Set<String> ROOT_KEYS = Set.of(
      "schema", "id", "version", "name", "authors", "enabled", "trusted",
      "targetPlugin", "targetVersions", "metrics"
  );
  private static final Set<String> METRIC_KEYS = Set.of(
      "id", "displayName", "kind", "unit", "icon", "decimals", "sampleEveryMs",
      "staleAfterMs", "source", "transform"
  );
  private static final Set<String> SOURCE_KEYS = Set.of(
      "type", "pluginId", "key", "placeholder", "eventClass", "foliaSafe"
  );
  private static final Set<String> TRANSFORM_KEYS = Set.of("mode", "scale", "offset", "minimum", "maximum");

  private PluginApiPackParser() {
  }

  public static PluginApiPackDefinition parse(Path sourcePath, String rawContent) throws IOException {
    String raw = rawContent == null ? "" : rawContent;
    if (raw.getBytes(StandardCharsets.UTF_8).length > MAX_FILE_BYTES) {
      throw new IOException("Plugin API pack exceeds " + MAX_FILE_BYTES + " bytes");
    }

    JsonElement parsed = TomlCodec.toJsonElement(raw);
    if (parsed == null || !parsed.isJsonObject()) {
      throw new IOException("Plugin API pack must contain a root table");
    }

    JsonObject root = parsed.getAsJsonObject();
    rejectUnknown(root, ROOT_KEYS, "root");
    JsonElement metricsElement = root.get("metrics");
    if (metricsElement != null && metricsElement.isJsonArray()) {
      for (int index = 0; index < metricsElement.getAsJsonArray().size(); index++) {
        JsonElement metricElement = metricsElement.getAsJsonArray().get(index);
        if (!metricElement.isJsonObject()) {
          throw new IOException("metrics[" + index + "] must be a table");
        }
        JsonObject metric = metricElement.getAsJsonObject();
        rejectUnknown(metric, METRIC_KEYS, "metrics[" + index + "]");
        rejectChild(metric, "source", SOURCE_KEYS, "metrics[" + index + "].source", true);
        rejectChild(metric, "transform", TRANSFORM_KEYS, "metrics[" + index + "].transform", false);
      }
    }

    RawPack document = GSON.fromJson(root, RawPack.class);
    return validate(sourcePath, raw, document);
  }

  private static PluginApiPackDefinition validate(Path sourcePath, String raw, RawPack document) throws IOException {
    if (document == null || !SCHEMA.equals(text(document.schema))) {
      throw new IOException("schema must be exactly " + SCHEMA);
    }

    String id = normalizedId(document.id, "id", ID);
    String version = requiredText(document.version, "version", 32);
    String name = requiredText(document.name, "name", 64);
    String targetPlugin = requiredText(document.targetPlugin, "targetPlugin", 64);
    List<String> authors = boundedStrings(document.authors, "authors", 8, 64);
    List<String> targetVersions = boundedStrings(document.targetVersions, "targetVersions", 32, 64);
    List<RawMetric> rawMetrics = document.metrics == null ? List.of() : document.metrics;
    if (rawMetrics.isEmpty() || rawMetrics.size() > MAX_METRICS) {
      throw new IOException("metrics must contain between 1 and " + MAX_METRICS + " entries");
    }

    boolean trusted = Boolean.TRUE.equals(document.trusted);
    Set<String> metricIds = new HashSet<>();
    Set<String> samplerIds = new HashSet<>();
    List<MetricDefinition> metrics = new ArrayList<>(rawMetrics.size());
    for (int index = 0; index < rawMetrics.size(); index++) {
      RawMetric rawMetric = rawMetrics.get(index);
      if (rawMetric == null) {
        throw new IOException("metrics[" + index + "] is missing");
      }
      String metricId = normalizedId(rawMetric.id, "metrics[" + index + "].id", METRIC_ID);
      if (!metricIds.add(metricId)) {
        throw new IOException("Duplicate metric id: " + metricId);
      }
      String samplerId = samplerId(id, metricId);
      if (!samplerIds.add(samplerId)) {
        throw new IOException("Duplicate sampler id: " + samplerId);
      }
      ReactMetricKind kind = enumValue(ReactMetricKind.class, rawMetric.kind, "metrics[" + index + "].kind");
      Material icon = Material.matchMaterial(requiredText(rawMetric.icon, "metrics[" + index + "].icon", 64));
      if (icon == null) {
        throw new IOException("metrics[" + index + "].icon must be a material");
      }
      int decimals = rawMetric.decimals == null ? defaultDecimals(kind) : rawMetric.decimals;
      if (decimals < 0 || decimals > 4) {
        throw new IOException("metrics[" + index + "].decimals must be between 0 and 4");
      }
      long sampleEveryMs = rawMetric.sampleEveryMs == null ? 1_000L : rawMetric.sampleEveryMs;
      if (sampleEveryMs < MIN_SAMPLE_MS || sampleEveryMs > MAX_SAMPLE_MS) {
        throw new IOException("metrics[" + index + "].sampleEveryMs must be between " + MIN_SAMPLE_MS + " and " + MAX_SAMPLE_MS);
      }
      long staleAfterMs = rawMetric.staleAfterMs == null ? 15_000L : rawMetric.staleAfterMs;
      if (staleAfterMs < sampleEveryMs || staleAfterMs > MAX_STALE_MS) {
        throw new IOException("metrics[" + index + "].staleAfterMs must be between sampleEveryMs and " + MAX_STALE_MS);
      }
      SourceDefinition source = source(rawMetric.source, trusted, index);
      TransformDefinition transform = transform(rawMetric.transform, index);
      metrics.add(new MetricDefinition(
          metricId,
          samplerId,
          requiredText(rawMetric.displayName, "metrics[" + index + "].displayName", 64),
          kind,
          optionalText(rawMetric.unit, 16),
          icon,
          decimals,
          sampleEveryMs,
          staleAfterMs,
          source,
          transform
      ));
    }

    return new PluginApiPackDefinition(
        SCHEMA,
        id,
        version,
        name,
        authors,
        document.enabled == null || document.enabled,
        trusted,
        targetPlugin,
        targetVersions,
        List.copyOf(metrics),
        sourcePath,
        raw
    );
  }

  private static SourceDefinition source(RawSource raw, boolean trusted, int metricIndex) throws IOException {
    if (raw == null) {
      throw new IOException("metrics[" + metricIndex + "].source is required");
    }
    SourceType type = enumValue(SourceType.class, raw.type, "metrics[" + metricIndex + "].source.type");
    if (type == SourceType.PLACEHOLDER && !trusted) {
      throw new IOException("metrics[" + metricIndex + "] uses " + type.name().toLowerCase(Locale.ROOT)
          + " and requires trusted = true");
    }
    String pluginId = optionalText(raw.pluginId, 64);
    String key = optionalText(raw.key, 160);
    String placeholder = optionalText(raw.placeholder, 256);
    String eventClass = optionalText(raw.eventClass, 160);

    switch (type) {
      case INTEGRATION -> {
        required(pluginId, "source.pluginId");
        required(key, "source.key");
      }
      case PLACEHOLDER -> {
        if (placeholder.length() < 3 || placeholder.charAt(0) != '%' || placeholder.charAt(placeholder.length() - 1) != '%') {
          throw new IOException("source.placeholder must be one complete %placeholder% token");
        }
      }
      case ORAXEN -> {
        if (!Set.of("items", "blocks", "furniture").contains(key)) {
          throw new IOException("source.key for Oraxen must be items, blocks, or furniture");
        }
      }
      case EVENT_COUNTER -> {
        if (!CLASS_NAME.matcher(eventClass).matches()) {
          throw new IOException("source.eventClass is invalid");
        }
      }
    }
    return new SourceDefinition(type, pluginId, key, placeholder, eventClass, Boolean.TRUE.equals(raw.foliaSafe));
  }

  private static TransformDefinition transform(RawTransform raw, int metricIndex) throws IOException {
    if (raw == null) {
      return new TransformDefinition(TransformMode.VALUE, 1D, 0D, null, null);
    }
    TransformMode mode = raw.mode == null || raw.mode.isBlank()
        ? TransformMode.VALUE
        : enumValue(TransformMode.class, raw.mode, "metrics[" + metricIndex + "].transform.mode");
    double scale = raw.scale == null ? 1D : raw.scale;
    double offset = raw.offset == null ? 0D : raw.offset;
    if (!Double.isFinite(scale) || !Double.isFinite(offset)
        || (raw.minimum != null && !Double.isFinite(raw.minimum))
        || (raw.maximum != null && !Double.isFinite(raw.maximum))) {
      throw new IOException("metrics[" + metricIndex + "].transform values must be finite");
    }
    if (raw.minimum != null && raw.maximum != null && raw.minimum > raw.maximum) {
      throw new IOException("metrics[" + metricIndex + "].transform.minimum exceeds maximum");
    }
    return new TransformDefinition(mode, scale, offset, raw.minimum, raw.maximum);
  }

  public static String samplerId(String packId, String metricId) {
    return ("plugin-api-" + packId + "-" + metricId)
        .toLowerCase(Locale.ROOT)
        .replaceAll("[^a-z0-9]+", "-")
        .replaceAll("^-+|-+$", "");
  }

  static boolean versionMatches(String version, List<String> patterns) {
    if (patterns == null || patterns.isEmpty()) {
      return true;
    }
    String candidate = text(version);
    for (String rawPattern : patterns) {
      String pattern = text(rawPattern);
      if ("*".equals(pattern)) {
        return true;
      }
      if (pattern.endsWith("*") && candidate.startsWith(pattern.substring(0, pattern.length() - 1))) {
        return true;
      }
      if (candidate.equals(pattern)) {
        return true;
      }
    }
    return false;
  }

  private static void rejectChild(JsonObject owner, String key, Set<String> allowed, String path, boolean required)
      throws IOException {
    JsonElement child = owner.get(key);
    if (child == null) {
      if (required) {
        throw new IOException(path + " is required");
      }
      return;
    }
    if (!child.isJsonObject()) {
      throw new IOException(path + " must be a table");
    }
    rejectUnknown(child.getAsJsonObject(), allowed, path);
  }

  private static void rejectUnknown(JsonObject object, Set<String> allowed, String path) throws IOException {
    for (String key : object.keySet()) {
      if (!allowed.contains(key)) {
        throw new IOException("Unknown key " + path + "." + key);
      }
    }
  }

  private static String normalizedId(String raw, String field, Pattern pattern) throws IOException {
    String value = requiredText(raw, field, 48).toLowerCase(Locale.ROOT);
    if (!pattern.matcher(value).matches()) {
      throw new IOException(field + " has an invalid identifier");
    }
    return value;
  }

  private static String requiredText(String raw, String field, int maxLength) throws IOException {
    String value = text(raw);
    if (value.isEmpty()) {
      throw new IOException(field + " is required");
    }
    if (value.length() > maxLength) {
      throw new IOException(field + " exceeds " + maxLength + " characters");
    }
    return value;
  }

  private static String optionalText(String raw, int maxLength) throws IOException {
    String value = text(raw);
    if (value.length() > maxLength) {
      throw new IOException("Text value exceeds " + maxLength + " characters");
    }
    return value;
  }

  private static List<String> boundedStrings(List<String> raw, String field, int maxEntries, int maxLength)
      throws IOException {
    if (raw == null) {
      return List.of();
    }
    if (raw.size() > maxEntries) {
      throw new IOException(field + " exceeds " + maxEntries + " entries");
    }
    List<String> values = new ArrayList<>(raw.size());
    for (String entry : raw) {
      String value = requiredText(entry, field, maxLength);
      values.add(value);
    }
    return List.copyOf(values);
  }

  private static <E extends Enum<E>> E enumValue(Class<E> type, String raw, String field) throws IOException {
    String value = text(raw).replace('-', '_').toUpperCase(Locale.ROOT);
    try {
      return Enum.valueOf(type, value);
    } catch (IllegalArgumentException exception) {
      throw new IOException(field + " has an unsupported value: " + raw, exception);
    }
  }

  private static int defaultDecimals(ReactMetricKind kind) {
    return switch (kind) {
      case GAUGE, COUNTER, BYTES -> 0;
      case RATE, PERCENT -> 1;
      case MILLIS -> 2;
    };
  }

  private static void required(String value, String field) throws IOException {
    if (value == null || value.isBlank()) {
      throw new IOException(field + " is required");
    }
  }

  private static String text(String raw) {
    return raw == null ? "" : raw.strip();
  }

  private static final class RawPack {
    private String schema;
    private String id;
    private String version;
    private String name;
    private List<String> authors;
    private Boolean enabled;
    private Boolean trusted;
    private String targetPlugin;
    private List<String> targetVersions;
    private List<RawMetric> metrics;
  }

  private static final class RawMetric {
    private String id;
    private String displayName;
    private String kind;
    private String unit;
    private String icon;
    private Integer decimals;
    private Long sampleEveryMs;
    private Long staleAfterMs;
    private RawSource source;
    private RawTransform transform;
  }

  private static final class RawSource {
    private String type;
    private String pluginId;
    private String key;
    private String placeholder;
    private String eventClass;
    private Boolean foliaSafe;
  }

  private static final class RawTransform {
    private String mode;
    private Double scale;
    private Double offset;
    private Double minimum;
    private Double maximum;
  }
}
