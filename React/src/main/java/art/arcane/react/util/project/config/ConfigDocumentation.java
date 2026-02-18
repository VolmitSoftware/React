package art.arcane.react.util.config;

import java.lang.reflect.Field;
import java.util.*;

public final class ConfigDocumentation {
  private static final Map<String, String> SUMMARY_BY_KEY = Map.ofEntries(
      Map.entry("enabled", "Enables or disables this component."),
      Map.entry("debug", "Enables extra debugging output and diagnostics."),
      Map.entry("verbose", "Enables verbose logging for additional runtime detail."),
      Map.entry("customColors", "Enables custom color rendering in monitors and UI."),
      Map.entry("baseValue", "Base material value used by value calculations."),
      Map.entry("valueMutlipliers", "Per-material value multipliers used to tune priority and valuation."),
      Map.entry("maxRecipeListPrecaution", "Safety cap used while exploring recipe chains to avoid runaway cost loops."),
      Map.entry("monitorConfiguration", "Default monitor layout and sampler grouping."),
      Map.entry("updateCooldownSeconds", "Cooldown between dynamic updates to reduce jitter and churn."),
      Map.entry("secondsToPurge", "Delay before purge actions start removing entities."),
      Map.entry("countUpTickTimeThresholdMS", "Threshold after which TPS sampler switches to elapsed-time reporting.")
  );

  private static final Map<String, String> IMPACT_BY_KEY = Map.ofEntries(
      Map.entry("enabled", "Set to false to disable this behavior without removing the config file."),
      Map.entry("debug", "Higher verbosity can help troubleshooting but may add console noise."),
      Map.entry("verbose", "Enable only when diagnosing behavior to avoid noisy logs."),
      Map.entry("baseValue", "Higher values increase baseline valuation; lower values make value scores more conservative."),
      Map.entry("valueMutlipliers", "Higher multipliers increase weight for selected materials; lower values decrease it."),
      Map.entry("maxRecipeListPrecaution", "Higher values allow deeper recipe traversal; lower values reduce CPU and avoid recursion risk."),
      Map.entry("updateCooldownSeconds", "Higher values reduce update frequency; lower values react faster but can oscillate more."),
      Map.entry("secondsToPurge", "Higher values give players more warning time; lower values purge sooner."),
      Map.entry("countUpTickTimeThresholdMS", "Higher values delay fallback reporting; lower values switch sooner during long stalls.")
  );

  private static final Set<String> ALWAYS_VISIBLE_KEYS = Set.of(
      "enabled",
      "debug",
      "verbose",
      "baseValue",
      "valueMutlipliers",
      "monitorConfiguration"
  );

  private ConfigDocumentation() {
  }

  public static List<String> buildFieldComments(String sourceTag, String path, Field field, Object value) {
    List<String> lines = new ArrayList<>();
    ConfigDoc annotation = field.getAnnotation(ConfigDoc.class);
    String key = field.getName();
    String summary;
    String impact;

    if (annotation != null) {
      summary = annotation.value().strip();
      impact = annotation.impact().strip();
      if (isGenericSummary(summary)) {
        summary = defaultSummary(sourceTag, path, field);
      }
      if (impact.isBlank() || isGenericImpact(impact)) {
        impact = defaultImpact(field, value);
      }
    } else {
      summary = SUMMARY_BY_KEY.getOrDefault(key, defaultSummary(sourceTag, path, field));
      impact = IMPACT_BY_KEY.getOrDefault(key, defaultImpact(field, value));
    }

    if (summary != null && !summary.isBlank()) {
      lines.add(summary);
    }
    if (!impact.isBlank()) {
      lines.add("Effect: " + impact);
    }

    String options = optionHints(field, value);
    if (!options.isBlank()) {
      lines.add("Options: " + options);
    }

    lines.addAll(rangeHints(field));
    return lines;
  }

  public static String buildRootDescription(String sourceTag, Class<?> rootType) {
    if (sourceTag != null && sourceTag.startsWith("feature:")) {
      return "Configuration for the " + sourceTag.substring("feature:".length()) + " feature.";
    }
    if (sourceTag != null && sourceTag.startsWith("tweak:")) {
      return "Configuration for the " + sourceTag.substring("tweak:".length()) + " tweak.";
    }
    if (sourceTag != null && sourceTag.startsWith("action:")) {
      return "Configuration for the " + sourceTag.substring("action:".length()) + " action.";
    }
    if (sourceTag != null && sourceTag.startsWith("sampler:")) {
      return "Configuration for the " + sourceTag.substring("sampler:".length()) + " sampler.";
    }
    if (sourceTag != null && sourceTag.startsWith("core:")) {
      return "Configuration for the " + sourceTag.substring("core:".length()) + " core controller.";
    }

    if (rootType != null) {
      return "Configuration for " + humanize(rootType.getSimpleName()) + ".";
    }

    return "React configuration.";
  }

  public static boolean shouldExposeField(String sourceTag, String path, Field field, Object value) {
    if (field == null) {
      return false;
    }

    if (field.getAnnotation(ConfigAdvanced.class) != null) {
      return false;
    }

    String key = field.getName();
    if (ALWAYS_VISIBLE_KEYS.contains(key)) {
      return true;
    }

    String lowered = key.toLowerCase(Locale.ROOT);

    // Runtime/internal values should not be persisted as user-facing knobs.
    if (lowered.startsWith("last")
        || lowered.startsWith("cached")
        || lowered.startsWith("runtime")
        || lowered.equals("unknown")
        || lowered.equals("lowerbound")
        || lowered.equals("upperbound")
        || lowered.equals("randomdelay")) {
      return false;
    }

    return true;
  }

  public static List<String> buildSectionComments(String sourceTag, String path) {
    if (path == null || path.isBlank()) {
      return List.of();
    }

    String leaf = path;
    int idx = leaf.lastIndexOf('.');
    if (idx >= 0 && idx + 1 < leaf.length()) {
      leaf = leaf.substring(idx + 1);
    }

    String humanLeaf = humanize(leaf);
    if (sourceTag != null && sourceTag.startsWith("feature:")) {
      return List.of("Settings for the " + sourceTag.substring("feature:".length()) + " feature " + humanLeaf + " section.");
    }
    if (sourceTag != null && sourceTag.startsWith("tweak:")) {
      return List.of("Settings for the " + sourceTag.substring("tweak:".length()) + " tweak " + humanLeaf + " section.");
    }
    if (sourceTag != null && sourceTag.startsWith("action:")) {
      return List.of("Settings for the " + sourceTag.substring("action:".length()) + " action " + humanLeaf + " section.");
    }
    if (sourceTag != null && sourceTag.startsWith("sampler:")) {
      return List.of("Settings for the " + sourceTag.substring("sampler:".length()) + " sampler " + humanLeaf + " section.");
    }
    if (sourceTag != null && sourceTag.startsWith("core:")) {
      return List.of("Settings for the " + sourceTag.substring("core:".length()) + " core controller " + humanLeaf + " section.");
    }

    return List.of("Settings for " + humanLeaf + ".");
  }

  private static List<String> rangeHints(Field field) {
    List<String> hints = new ArrayList<>();
    String lower = field.getName().toLowerCase(Locale.ROOT);

    if (lower.contains("chance") || lower.contains("percent") || lower.contains("ratio")) {
      hints.add("Tuning: Start with small changes and validate behavior under load before increasing aggressively.");
    }
    if (lower.contains("tick") || lower.contains("interval") || lower.endsWith("ms") || lower.contains("cooldown")) {
      hints.add("Tuning: Lower values run more often and can cost more CPU; increase carefully on busy servers.");
    }
    if (lower.contains("radius") || lower.contains("distance") || lower.contains("range")) {
      hints.add("Tuning: Larger ranges increase affected entities/blocks and may increase per-tick work.");
    }

    return hints;
  }

  private static String defaultSummary(String sourceTag, String path, Field field) {
    String key = field.getName();
    String lower = key.toLowerCase(Locale.ROOT);
    String subject = subject(sourceTag, path);
    if (lower.contains("cooldown")) {
      return "Cooldown between " + subject + " operations.";
    }
    if (lower.contains("chance") || lower.contains("percent") || lower.contains("ratio")) {
      return "Chance/ratio tuning used by " + subject + ".";
    }
    if (lower.contains("multiplier") || lower.contains("factor") || lower.contains("scalar")) {
      return "Scaling applied to " + subject + ".";
    }
    if (lower.contains("duration") || lower.contains("ticks") || lower.contains("millis") || lower.endsWith("ms") || lower.contains("interval")) {
      return "Duration or timing value used by " + subject + ".";
    }
    if (lower.contains("radius") || lower.contains("range") || lower.contains("distance")) {
      return "Distance or area limit used by " + subject + ".";
    }
    if (lower.startsWith("min") || lower.contains("threshold")) {
      return "Minimum threshold required for " + subject + ".";
    }
    if (lower.startsWith("max") || lower.contains("cap") || lower.contains("limit")) {
      return "Maximum cap applied to " + subject + ".";
    }

    String label = humanize(field.getName());
    if (sourceTag != null && sourceTag.startsWith("feature:")) {
      return "Controls " + label + " for the " + sourceTag.substring("feature:".length()) + " feature.";
    }
    if (sourceTag != null && sourceTag.startsWith("tweak:")) {
      return "Controls " + label + " for the " + sourceTag.substring("tweak:".length()) + " tweak.";
    }
    if (sourceTag != null && sourceTag.startsWith("action:")) {
      return "Controls " + label + " for the " + sourceTag.substring("action:".length()) + " action.";
    }
    if (sourceTag != null && sourceTag.startsWith("sampler:")) {
      return "Controls " + label + " for the " + sourceTag.substring("sampler:".length()) + " sampler.";
    }
    if (sourceTag != null && sourceTag.startsWith("core:")) {
      return "Controls " + label + " for the " + sourceTag.substring("core:".length()) + " core controller.";
    }
    if (path != null && !path.isBlank()) {
      return "Controls " + label + " in the " + path + " section.";
    }

    return "Controls " + label + ".";
  }

  private static String defaultImpact(Field field, Object value) {
    Class<?> type = field.getType();
    String lower = field.getName().toLowerCase(Locale.ROOT);

    if (type == boolean.class || type == Boolean.class) {
      return "True enables this behavior and false disables it.";
    }
    if (lower.contains("chance") || lower.contains("ratio") || lower.contains("percent")) {
      return "Higher values trigger behavior more often; lower values trigger it less often.";
    }
    if (lower.contains("cooldown") || lower.contains("interval") || lower.endsWith("ms") || lower.contains("tick")) {
      return "Higher values run less frequently; lower values run more frequently.";
    }
    if (lower.contains("multiplier") || lower.contains("factor") || lower.contains("scalar")) {
      return "Higher values amplify the effect; lower values reduce it.";
    }
    if (lower.contains("radius") || lower.contains("range") || lower.contains("distance")) {
      return "Higher values affect a wider area; lower values keep the effect tighter.";
    }
    if (lower.startsWith("min") || lower.contains("threshold")) {
      return "Higher values make activation stricter; lower values make it easier to trigger.";
    }
    if (lower.startsWith("max") || lower.contains("cap") || lower.contains("limit")) {
      return "Higher values raise the upper limit; lower values clamp behavior sooner.";
    }
    if (Number.class.isAssignableFrom(type) || (type.isPrimitive() && type != boolean.class && type != char.class)) {
      return "Higher values generally increase intensity, cost, or limits; lower values reduce them.";
    }
    if (type.isEnum()) {
      return "Changing this selects a different operating mode.";
    }
    if (type == String.class || type == char.class || type == Character.class) {
      return "Changing this updates the identifier or label used by the component.";
    }
    if (value instanceof List<?>) {
      return "Add or remove entries to tune which values are included.";
    }
    if (value instanceof Map<?, ?>) {
      return "Edit keys to define targeted overrides for this behavior.";
    }

    return "";
  }

  private static String optionHints(Field field, Object value) {
    if (field == null) {
      return "";
    }

    Class<?> type = field.getType();
    if (type == boolean.class || type == Boolean.class) {
      return "true | false";
    }
    if (type.isEnum()) {
      Object[] constants = type.getEnumConstants();
      if (constants == null || constants.length == 0) {
        return "";
      }

      StringBuilder builder = new StringBuilder();
      for (int i = 0; i < constants.length; i++) {
        if (i > 0) {
          builder.append(", ");
        }
        builder.append(constants[i]);
      }
      return builder.toString();
    }
    if (Number.class.isAssignableFrom(type) || (type.isPrimitive() && type != boolean.class && type != char.class)) {
      return "Numeric value.";
    }
    if (type == String.class || type == char.class || type == Character.class) {
      return "Text value.";
    }
    if (value instanceof List<?>) {
      return "TOML array, for example: [\"value-a\", \"value-b\"]";
    }
    if (value instanceof Map<?, ?>) {
      return "TOML key/value table entries.";
    }

    return "";
  }

  private static boolean isGenericSummary(String summary) {
    if (summary == null || summary.isBlank()) {
      return true;
    }

    String lower = summary.toLowerCase(Locale.ROOT).trim();
    return lower.startsWith("controls ") || lower.equals("no description provided");
  }

  private static boolean isGenericImpact(String impact) {
    if (impact == null || impact.isBlank()) {
      return true;
    }

    String lower = impact.toLowerCase(Locale.ROOT);
    return lower.contains("higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        || lower.contains("true enables this behavior and false disables it.");
  }

  private static String subject(String sourceTag, String path) {
    if (sourceTag != null && sourceTag.startsWith("feature:")) {
      return "the " + sourceTag.substring("feature:".length()) + " feature";
    }
    if (sourceTag != null && sourceTag.startsWith("tweak:")) {
      return "the " + sourceTag.substring("tweak:".length()) + " tweak";
    }
    if (sourceTag != null && sourceTag.startsWith("action:")) {
      return "the " + sourceTag.substring("action:".length()) + " action";
    }
    if (sourceTag != null && sourceTag.startsWith("sampler:")) {
      return "the " + sourceTag.substring("sampler:".length()) + " sampler";
    }
    if (sourceTag != null && sourceTag.startsWith("core:")) {
      return "the " + sourceTag.substring("core:".length()) + " core controller";
    }
    if (path != null && !path.isBlank()) {
      return "the " + path + " section";
    }
    return "this component";
  }

  private static String humanize(String key) {
    if (key == null || key.isBlank()) {
      return "this setting";
    }

    String spaced = key
        .replace('_', ' ')
        .replace('-', ' ')
        .replaceAll("([a-z])([A-Z])", "$1 $2")
        .trim();
    if (spaced.isBlank()) {
      return key;
    }

    String lower = spaced.toLowerCase(Locale.ROOT);
    return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
  }
}
