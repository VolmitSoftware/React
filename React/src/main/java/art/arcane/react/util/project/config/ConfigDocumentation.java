package art.arcane.react.util.project.config;

import art.arcane.react.localization.ReactLanguage;
import art.arcane.react.localization.catalog.ConfigMessages;
import art.arcane.volmlib.util.localization.MessageArgument;
import art.arcane.volmlib.util.localization.TextKey;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class ConfigDocumentation {
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
      summary = ConfigLocalization.fieldSummary(field);
      impact = ConfigLocalization.fieldImpact(field);
      if (isGenericSummary(summary)) {
        summary = defaultSummary(sourceTag, path, field);
      }
      if (impact.isBlank() || isGenericImpact(impact)) {
        impact = defaultImpact(field, value);
      }
    } else {
      TextKey summaryKey = ConfigMessages.summaryFor(key);
      TextKey impactKey = ConfigMessages.impactFor(key);
      summary = summaryKey == null ? defaultSummary(sourceTag, path, field) : ReactLanguage.raw(summaryKey);
      impact = impactKey == null ? defaultImpact(field, value) : ReactLanguage.raw(impactKey);
    }

    if (summary != null && !summary.isBlank()) {
      lines.add(summary);
    }
    if (!impact.isBlank()) {
      lines.add(ReactLanguage.raw(ConfigMessages.EFFECT, MessageArgument.untrusted("impact", impact)));
    }

    String options = optionHints(field, value);
    if (!options.isBlank()) {
      lines.add(ReactLanguage.raw(ConfigMessages.OPTIONS, MessageArgument.untrusted("options", options)));
    }

    lines.addAll(rangeHints(field));
    return lines;
  }

  public static String buildRootDescription(String sourceTag, Class<?> rootType) {
    if (sourceTag != null && sourceTag.startsWith("feature:")) {
      return ReactLanguage.raw(
          ConfigMessages.ROOT_FEATURE,
          MessageArgument.untrusted("name", sourceTag.substring("feature:".length()))
      );
    }
    if (sourceTag != null && sourceTag.startsWith("tweak:")) {
      return ReactLanguage.raw(
          ConfigMessages.ROOT_TWEAK,
          MessageArgument.untrusted("name", sourceTag.substring("tweak:".length()))
      );
    }
    if (sourceTag != null && sourceTag.startsWith("action:")) {
      return ReactLanguage.raw(
          ConfigMessages.ROOT_ACTION,
          MessageArgument.untrusted("name", sourceTag.substring("action:".length()))
      );
    }
    if (sourceTag != null && sourceTag.startsWith("sampler:")) {
      return ReactLanguage.raw(
          ConfigMessages.ROOT_SAMPLER,
          MessageArgument.untrusted("name", sourceTag.substring("sampler:".length()))
      );
    }
    if (sourceTag != null && sourceTag.startsWith("core:")) {
      return ReactLanguage.raw(
          ConfigMessages.ROOT_CORE,
          MessageArgument.untrusted("name", sourceTag.substring("core:".length()))
      );
    }

    if (rootType != null) {
      String description = ConfigLocalization.description(rootType);
      if (!description.isBlank()) {
        return description;
      }
      return ReactLanguage.raw(
          ConfigMessages.ROOT_TYPE,
          MessageArgument.untrusted("name", humanize(rootType.getSimpleName()))
      );
    }

    return ReactLanguage.raw(ConfigMessages.ROOT_DEFAULT);
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
      return List.of(ReactLanguage.raw(
          ConfigMessages.SECTION_FEATURE,
          MessageArgument.untrusted("name", sourceTag.substring("feature:".length())),
          MessageArgument.untrusted("section", humanLeaf)
      ));
    }
    if (sourceTag != null && sourceTag.startsWith("tweak:")) {
      return List.of(ReactLanguage.raw(
          ConfigMessages.SECTION_TWEAK,
          MessageArgument.untrusted("name", sourceTag.substring("tweak:".length())),
          MessageArgument.untrusted("section", humanLeaf)
      ));
    }
    if (sourceTag != null && sourceTag.startsWith("action:")) {
      return List.of(ReactLanguage.raw(
          ConfigMessages.SECTION_ACTION,
          MessageArgument.untrusted("name", sourceTag.substring("action:".length())),
          MessageArgument.untrusted("section", humanLeaf)
      ));
    }
    if (sourceTag != null && sourceTag.startsWith("sampler:")) {
      return List.of(ReactLanguage.raw(
          ConfigMessages.SECTION_SAMPLER,
          MessageArgument.untrusted("name", sourceTag.substring("sampler:".length())),
          MessageArgument.untrusted("section", humanLeaf)
      ));
    }
    if (sourceTag != null && sourceTag.startsWith("core:")) {
      return List.of(ReactLanguage.raw(
          ConfigMessages.SECTION_CORE,
          MessageArgument.untrusted("name", sourceTag.substring("core:".length())),
          MessageArgument.untrusted("section", humanLeaf)
      ));
    }

    return List.of(ReactLanguage.raw(
        ConfigMessages.SECTION_DEFAULT,
        MessageArgument.untrusted("section", humanLeaf)
    ));
  }

  private static List<String> rangeHints(Field field) {
    List<String> hints = new ArrayList<>();
    String lower = field.getName().toLowerCase(Locale.ROOT);

    if (lower.contains("chance") || lower.contains("percent") || lower.contains("ratio")) {
      hints.add(ReactLanguage.raw(ConfigMessages.TUNING_RATIO));
    }
    if (lower.contains("tick") || lower.contains("interval") || lower.endsWith("ms") || lower.contains("cooldown")) {
      hints.add(ReactLanguage.raw(ConfigMessages.TUNING_TIMING));
    }
    if (lower.contains("radius") || lower.contains("distance") || lower.contains("range")) {
      hints.add(ReactLanguage.raw(ConfigMessages.TUNING_RANGE));
    }

    return hints;
  }

  private static String defaultSummary(String sourceTag, String path, Field field) {
    String key = field.getName();
    String lower = key.toLowerCase(Locale.ROOT);
    String subject = subject(sourceTag, path);
    if (lower.contains("cooldown")) {
      return ReactLanguage.raw(ConfigMessages.SUMMARY_COOLDOWN, MessageArgument.untrusted("subject", subject));
    }
    if (lower.contains("chance") || lower.contains("percent") || lower.contains("ratio")) {
      return ReactLanguage.raw(ConfigMessages.SUMMARY_RATIO, MessageArgument.untrusted("subject", subject));
    }
    if (lower.contains("multiplier") || lower.contains("factor") || lower.contains("scalar")) {
      return ReactLanguage.raw(ConfigMessages.SUMMARY_SCALING, MessageArgument.untrusted("subject", subject));
    }
    if (lower.contains("duration") || lower.contains("ticks") || lower.contains("millis") || lower.endsWith("ms") || lower.contains("interval")) {
      return ReactLanguage.raw(ConfigMessages.SUMMARY_TIMING, MessageArgument.untrusted("subject", subject));
    }
    if (lower.contains("radius") || lower.contains("range") || lower.contains("distance")) {
      return ReactLanguage.raw(ConfigMessages.SUMMARY_RANGE, MessageArgument.untrusted("subject", subject));
    }
    if (lower.startsWith("min") || lower.contains("threshold")) {
      return ReactLanguage.raw(ConfigMessages.SUMMARY_MINIMUM, MessageArgument.untrusted("subject", subject));
    }
    if (lower.startsWith("max") || lower.contains("cap") || lower.contains("limit")) {
      return ReactLanguage.raw(ConfigMessages.SUMMARY_MAXIMUM, MessageArgument.untrusted("subject", subject));
    }

    String label = humanize(field.getName());
    if (sourceTag != null && sourceTag.startsWith("feature:")) {
      return ReactLanguage.raw(
          ConfigMessages.CONTROL_FEATURE,
          MessageArgument.untrusted("label", label),
          MessageArgument.untrusted("name", sourceTag.substring("feature:".length()))
      );
    }
    if (sourceTag != null && sourceTag.startsWith("tweak:")) {
      return ReactLanguage.raw(
          ConfigMessages.CONTROL_TWEAK,
          MessageArgument.untrusted("label", label),
          MessageArgument.untrusted("name", sourceTag.substring("tweak:".length()))
      );
    }
    if (sourceTag != null && sourceTag.startsWith("action:")) {
      return ReactLanguage.raw(
          ConfigMessages.CONTROL_ACTION,
          MessageArgument.untrusted("label", label),
          MessageArgument.untrusted("name", sourceTag.substring("action:".length()))
      );
    }
    if (sourceTag != null && sourceTag.startsWith("sampler:")) {
      return ReactLanguage.raw(
          ConfigMessages.CONTROL_SAMPLER,
          MessageArgument.untrusted("label", label),
          MessageArgument.untrusted("name", sourceTag.substring("sampler:".length()))
      );
    }
    if (sourceTag != null && sourceTag.startsWith("core:")) {
      return ReactLanguage.raw(
          ConfigMessages.CONTROL_CORE,
          MessageArgument.untrusted("label", label),
          MessageArgument.untrusted("name", sourceTag.substring("core:".length()))
      );
    }
    if (path != null && !path.isBlank()) {
      return ReactLanguage.raw(
          ConfigMessages.CONTROL_SECTION,
          MessageArgument.untrusted("label", label),
          MessageArgument.untrusted("section", path)
      );
    }

    return ReactLanguage.raw(ConfigMessages.CONTROL_DEFAULT, MessageArgument.untrusted("label", label));
  }

  private static String defaultImpact(Field field, Object value) {
    Class<?> type = field.getType();
    String lower = field.getName().toLowerCase(Locale.ROOT);

    if (type == boolean.class || type == Boolean.class) {
      return ReactLanguage.raw(ConfigMessages.IMPACT_BOOLEAN);
    }
    if (lower.contains("chance") || lower.contains("ratio") || lower.contains("percent")) {
      return ReactLanguage.raw(ConfigMessages.IMPACT_RATIO);
    }
    if (lower.contains("cooldown") || lower.contains("interval") || lower.endsWith("ms") || lower.contains("tick")) {
      return ReactLanguage.raw(ConfigMessages.IMPACT_TIMING);
    }
    if (lower.contains("multiplier") || lower.contains("factor") || lower.contains("scalar")) {
      return ReactLanguage.raw(ConfigMessages.IMPACT_SCALING);
    }
    if (lower.contains("radius") || lower.contains("range") || lower.contains("distance")) {
      return ReactLanguage.raw(ConfigMessages.IMPACT_RANGE);
    }
    if (lower.startsWith("min") || lower.contains("threshold")) {
      return ReactLanguage.raw(ConfigMessages.IMPACT_MINIMUM);
    }
    if (lower.startsWith("max") || lower.contains("cap") || lower.contains("limit")) {
      return ReactLanguage.raw(ConfigMessages.IMPACT_MAXIMUM);
    }
    if (Number.class.isAssignableFrom(type) || (type.isPrimitive() && type != boolean.class && type != char.class)) {
      return ReactLanguage.raw(ConfigMessages.IMPACT_NUMBER);
    }
    if (type.isEnum()) {
      return ReactLanguage.raw(ConfigMessages.IMPACT_ENUM);
    }
    if (type == String.class || type == char.class || type == Character.class) {
      return ReactLanguage.raw(ConfigMessages.IMPACT_TEXT);
    }
    if (value instanceof List<?>) {
      return ReactLanguage.raw(ConfigMessages.IMPACT_LIST);
    }
    if (value instanceof Map<?, ?>) {
      return ReactLanguage.raw(ConfigMessages.IMPACT_MAP);
    }

    return "";
  }

  private static String optionHints(Field field, Object value) {
    if (field == null) {
      return "";
    }

    Class<?> type = field.getType();
    if (type == boolean.class || type == Boolean.class) {
      return ReactLanguage.raw(ConfigMessages.OPTION_BOOLEAN);
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
      return ReactLanguage.raw(ConfigMessages.OPTION_NUMBER);
    }
    if (type == String.class || type == char.class || type == Character.class) {
      return ReactLanguage.raw(ConfigMessages.OPTION_TEXT);
    }
    if (value instanceof List<?>) {
      return ReactLanguage.raw(ConfigMessages.OPTION_LIST);
    }
    if (value instanceof Map<?, ?>) {
      return ReactLanguage.raw(ConfigMessages.OPTION_MAP);
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
      return ReactLanguage.raw(
          ConfigMessages.SUBJECT_FEATURE,
          MessageArgument.untrusted("name", sourceTag.substring("feature:".length()))
      );
    }
    if (sourceTag != null && sourceTag.startsWith("tweak:")) {
      return ReactLanguage.raw(
          ConfigMessages.SUBJECT_TWEAK,
          MessageArgument.untrusted("name", sourceTag.substring("tweak:".length()))
      );
    }
    if (sourceTag != null && sourceTag.startsWith("action:")) {
      return ReactLanguage.raw(
          ConfigMessages.SUBJECT_ACTION,
          MessageArgument.untrusted("name", sourceTag.substring("action:".length()))
      );
    }
    if (sourceTag != null && sourceTag.startsWith("sampler:")) {
      return ReactLanguage.raw(
          ConfigMessages.SUBJECT_SAMPLER,
          MessageArgument.untrusted("name", sourceTag.substring("sampler:".length()))
      );
    }
    if (sourceTag != null && sourceTag.startsWith("core:")) {
      return ReactLanguage.raw(
          ConfigMessages.SUBJECT_CORE,
          MessageArgument.untrusted("name", sourceTag.substring("core:".length()))
      );
    }
    if (path != null && !path.isBlank()) {
      return ReactLanguage.raw(
          ConfigMessages.SUBJECT_SECTION,
          MessageArgument.untrusted("section", path)
      );
    }
    return ReactLanguage.raw(ConfigMessages.SUBJECT_DEFAULT);
  }

  private static String humanize(String key) {
    if (key == null || key.isBlank()) {
      return ReactLanguage.raw(ConfigMessages.SETTING_DEFAULT);
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
