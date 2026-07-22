package art.arcane.react.localization.catalog;

import art.arcane.volmlib.util.localization.MessageCatalog;
import art.arcane.volmlib.util.localization.TextKey;

import java.util.Map;

public final class ConfigMessages {
  private static final Map<String, TextKey> SUMMARIES = Map.ofEntries(
      summary("enabled", "Enables or disables this component."),
      summary("debug", "Enables extra debugging output and diagnostics."),
      summary("verbose", "Enables verbose logging for additional runtime detail."),
      summary("customColors", "Enables custom color rendering in monitors and UI."),
      summary("baseValue", "Base material value used by value calculations."),
      summary("valueMutlipliers", "Per-material value multipliers used to tune priority and valuation."),
      summary("maxRecipeListPrecaution", "Safety cap used while exploring recipe chains to avoid runaway cost loops."),
      summary("monitorConfiguration", "Default monitor layout and sampler grouping."),
      summary("updateCooldownSeconds", "Cooldown between dynamic updates to reduce jitter and churn."),
      summary("secondsToPurge", "Delay before purge actions start removing entities."),
      summary("countUpTickTimeThresholdMS", "Threshold after which TPS sampler switches to elapsed-time reporting.")
  );
  private static final Map<String, TextKey> IMPACTS = Map.ofEntries(
      impact("enabled", "Set to false to disable this behavior without removing the config file."),
      impact("debug", "Higher verbosity can help troubleshooting but may add console noise."),
      impact("verbose", "Enable only when diagnosing behavior to avoid noisy logs."),
      impact("baseValue", "Higher values increase baseline valuation; lower values make value scores more conservative."),
      impact("valueMutlipliers", "Higher multipliers increase weight for selected materials; lower values decrease it."),
      impact("maxRecipeListPrecaution", "Higher values allow deeper recipe traversal; lower values reduce CPU and avoid recursion risk."),
      impact("updateCooldownSeconds", "Higher values reduce update frequency; lower values react faster but can oscillate more."),
      impact("secondsToPurge", "Higher values give players more warning time; lower values purge sooner."),
      impact("countUpTickTimeThresholdMS", "Higher values delay fallback reporting; lower values switch sooner during long stalls.")
  );
  public static final TextKey EFFECT = TextKey.of("config.documentation.effect", "Effect: {impact}");
  public static final TextKey OPTIONS = TextKey.of("config.documentation.options", "Options: {options}");
  public static final TextKey ROOT_FEATURE = TextKey.of("config.documentation.root.feature", "Configuration for the {name} feature.");
  public static final TextKey ROOT_TWEAK = TextKey.of("config.documentation.root.tweak", "Configuration for the {name} tweak.");
  public static final TextKey ROOT_ACTION = TextKey.of("config.documentation.root.action", "Configuration for the {name} action.");
  public static final TextKey ROOT_SAMPLER = TextKey.of("config.documentation.root.sampler", "Configuration for the {name} sampler.");
  public static final TextKey ROOT_CORE = TextKey.of("config.documentation.root.core", "Configuration for the {name} core controller.");
  public static final TextKey ROOT_TYPE = TextKey.of("config.documentation.root.type", "Configuration for {name}.");
  public static final TextKey ROOT_DEFAULT = TextKey.of("config.documentation.root.default", "React configuration.");
  public static final TextKey SECTION_FEATURE = TextKey.of("config.documentation.section.feature", "Settings for the {name} feature {section} section.");
  public static final TextKey SECTION_TWEAK = TextKey.of("config.documentation.section.tweak", "Settings for the {name} tweak {section} section.");
  public static final TextKey SECTION_ACTION = TextKey.of("config.documentation.section.action", "Settings for the {name} action {section} section.");
  public static final TextKey SECTION_SAMPLER = TextKey.of("config.documentation.section.sampler", "Settings for the {name} sampler {section} section.");
  public static final TextKey SECTION_CORE = TextKey.of("config.documentation.section.core", "Settings for the {name} core controller {section} section.");
  public static final TextKey SECTION_DEFAULT = TextKey.of("config.documentation.section.default", "Settings for {section}.");
  public static final TextKey TUNING_RATIO = TextKey.of("config.documentation.tuning.ratio", "Tuning: Start with small changes and validate behavior under load before increasing aggressively.");
  public static final TextKey TUNING_TIMING = TextKey.of("config.documentation.tuning.timing", "Tuning: Lower values run more often and can cost more CPU; increase carefully on busy servers.");
  public static final TextKey TUNING_RANGE = TextKey.of("config.documentation.tuning.range", "Tuning: Larger ranges increase affected entities/blocks and may increase per-tick work.");
  public static final TextKey SUMMARY_COOLDOWN = TextKey.of("config.documentation.summary.cooldown", "Cooldown between {subject} operations.");
  public static final TextKey SUMMARY_RATIO = TextKey.of("config.documentation.summary.ratio", "Chance/ratio tuning used by {subject}.");
  public static final TextKey SUMMARY_SCALING = TextKey.of("config.documentation.summary.scaling", "Scaling applied to {subject}.");
  public static final TextKey SUMMARY_TIMING = TextKey.of("config.documentation.summary.timing", "Duration or timing value used by {subject}.");
  public static final TextKey SUMMARY_RANGE = TextKey.of("config.documentation.summary.range", "Distance or area limit used by {subject}.");
  public static final TextKey SUMMARY_MINIMUM = TextKey.of("config.documentation.summary.minimum", "Minimum threshold required for {subject}.");
  public static final TextKey SUMMARY_MAXIMUM = TextKey.of("config.documentation.summary.maximum", "Maximum cap applied to {subject}.");
  public static final TextKey CONTROL_FEATURE = TextKey.of("config.documentation.control.feature", "Controls {label} for the {name} feature.");
  public static final TextKey CONTROL_TWEAK = TextKey.of("config.documentation.control.tweak", "Controls {label} for the {name} tweak.");
  public static final TextKey CONTROL_ACTION = TextKey.of("config.documentation.control.action", "Controls {label} for the {name} action.");
  public static final TextKey CONTROL_SAMPLER = TextKey.of("config.documentation.control.sampler", "Controls {label} for the {name} sampler.");
  public static final TextKey CONTROL_CORE = TextKey.of("config.documentation.control.core", "Controls {label} for the {name} core controller.");
  public static final TextKey CONTROL_SECTION = TextKey.of("config.documentation.control.section", "Controls {label} in the {section} section.");
  public static final TextKey CONTROL_DEFAULT = TextKey.of("config.documentation.control.default", "Controls {label}.");
  public static final TextKey IMPACT_BOOLEAN = TextKey.of("config.documentation.impact.boolean", "True enables this behavior and false disables it.");
  public static final TextKey IMPACT_RATIO = TextKey.of("config.documentation.impact.ratio", "Higher values trigger behavior more often; lower values trigger it less often.");
  public static final TextKey IMPACT_TIMING = TextKey.of("config.documentation.impact.timing", "Higher values run less frequently; lower values run more frequently.");
  public static final TextKey IMPACT_SCALING = TextKey.of("config.documentation.impact.scaling", "Higher values amplify the effect; lower values reduce it.");
  public static final TextKey IMPACT_RANGE = TextKey.of("config.documentation.impact.range", "Higher values affect a wider area; lower values keep the effect tighter.");
  public static final TextKey IMPACT_MINIMUM = TextKey.of("config.documentation.impact.minimum", "Higher values make activation stricter; lower values make it easier to trigger.");
  public static final TextKey IMPACT_MAXIMUM = TextKey.of("config.documentation.impact.maximum", "Higher values raise the upper limit; lower values clamp behavior sooner.");
  public static final TextKey IMPACT_NUMBER = TextKey.of("config.documentation.impact.number", "Higher values generally increase intensity, cost, or limits; lower values reduce them.");
  public static final TextKey IMPACT_ENUM = TextKey.of("config.documentation.impact.enum", "Changing this selects a different operating mode.");
  public static final TextKey IMPACT_TEXT = TextKey.of("config.documentation.impact.text", "Changing this updates the identifier or label used by the component.");
  public static final TextKey IMPACT_LIST = TextKey.of("config.documentation.impact.list", "Add or remove entries to tune which values are included.");
  public static final TextKey IMPACT_MAP = TextKey.of("config.documentation.impact.map", "Edit keys to define targeted overrides for this behavior.");
  public static final TextKey OPTION_BOOLEAN = TextKey.of("config.documentation.option.boolean", "true | false");
  public static final TextKey OPTION_NUMBER = TextKey.of("config.documentation.option.number", "Numeric value.");
  public static final TextKey OPTION_TEXT = TextKey.of("config.documentation.option.text", "Text value.");
  public static final TextKey OPTION_LIST = TextKey.of("config.documentation.option.list", "TOML array, for example: [\"value-a\", \"value-b\"]");
  public static final TextKey OPTION_MAP = TextKey.of("config.documentation.option.map", "TOML key/value table entries.");
  public static final TextKey SUBJECT_FEATURE = TextKey.of("config.documentation.subject.feature", "the {name} feature");
  public static final TextKey SUBJECT_TWEAK = TextKey.of("config.documentation.subject.tweak", "the {name} tweak");
  public static final TextKey SUBJECT_ACTION = TextKey.of("config.documentation.subject.action", "the {name} action");
  public static final TextKey SUBJECT_SAMPLER = TextKey.of("config.documentation.subject.sampler", "the {name} sampler");
  public static final TextKey SUBJECT_CORE = TextKey.of("config.documentation.subject.core", "the {name} core controller");
  public static final TextKey SUBJECT_SECTION = TextKey.of("config.documentation.subject.section", "the {section} section");
  public static final TextKey SUBJECT_DEFAULT = TextKey.of("config.documentation.subject.default", "this component");
  public static final TextKey SETTING_DEFAULT = TextKey.of("config.documentation.setting.default", "this setting");

  private ConfigMessages() {
  }

  public static TextKey summaryFor(String key) {
    return SUMMARIES.get(key);
  }

  public static TextKey impactFor(String key) {
    return IMPACTS.get(key);
  }

  public static void addTo(MessageCatalog.Builder builder) {
    builder.addAll(SUMMARIES.values());
    builder.addAll(IMPACTS.values());
    builder.add(EFFECT);
    builder.add(OPTIONS);
    builder.add(ROOT_FEATURE);
    builder.add(ROOT_TWEAK);
    builder.add(ROOT_ACTION);
    builder.add(ROOT_SAMPLER);
    builder.add(ROOT_CORE);
    builder.add(ROOT_TYPE);
    builder.add(ROOT_DEFAULT);
    builder.add(SECTION_FEATURE);
    builder.add(SECTION_TWEAK);
    builder.add(SECTION_ACTION);
    builder.add(SECTION_SAMPLER);
    builder.add(SECTION_CORE);
    builder.add(SECTION_DEFAULT);
    builder.add(TUNING_RATIO);
    builder.add(TUNING_TIMING);
    builder.add(TUNING_RANGE);
    builder.add(SUMMARY_COOLDOWN);
    builder.add(SUMMARY_RATIO);
    builder.add(SUMMARY_SCALING);
    builder.add(SUMMARY_TIMING);
    builder.add(SUMMARY_RANGE);
    builder.add(SUMMARY_MINIMUM);
    builder.add(SUMMARY_MAXIMUM);
    builder.add(CONTROL_FEATURE);
    builder.add(CONTROL_TWEAK);
    builder.add(CONTROL_ACTION);
    builder.add(CONTROL_SAMPLER);
    builder.add(CONTROL_CORE);
    builder.add(CONTROL_SECTION);
    builder.add(CONTROL_DEFAULT);
    builder.add(IMPACT_BOOLEAN);
    builder.add(IMPACT_RATIO);
    builder.add(IMPACT_TIMING);
    builder.add(IMPACT_SCALING);
    builder.add(IMPACT_RANGE);
    builder.add(IMPACT_MINIMUM);
    builder.add(IMPACT_MAXIMUM);
    builder.add(IMPACT_NUMBER);
    builder.add(IMPACT_ENUM);
    builder.add(IMPACT_TEXT);
    builder.add(IMPACT_LIST);
    builder.add(IMPACT_MAP);
    builder.add(OPTION_BOOLEAN);
    builder.add(OPTION_NUMBER);
    builder.add(OPTION_TEXT);
    builder.add(OPTION_LIST);
    builder.add(OPTION_MAP);
    builder.add(SUBJECT_FEATURE);
    builder.add(SUBJECT_TWEAK);
    builder.add(SUBJECT_ACTION);
    builder.add(SUBJECT_SAMPLER);
    builder.add(SUBJECT_CORE);
    builder.add(SUBJECT_SECTION);
    builder.add(SUBJECT_DEFAULT);
    builder.add(SETTING_DEFAULT);
  }

  private static Map.Entry<String, TextKey> summary(String key, String english) {
    return Map.entry(key, TextKey.of("config.documentation.known_summary." + key, english));
  }

  private static Map.Entry<String, TextKey> impact(String key, String english) {
    return Map.entry(key, TextKey.of("config.documentation.known_impact." + key, english));
  }
}
