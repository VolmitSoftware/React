package art.arcane.react.localization;

import art.arcane.react.React;
import art.arcane.react.localization.catalog.RuntimeMessages;
import art.arcane.react.model.ReactConfiguration;
import art.arcane.react.util.plugin.VolmitSender;
import art.arcane.react.util.project.config.ConfigFileSupport;
import art.arcane.volmlib.util.director.DirectorTextResolver;
import art.arcane.volmlib.util.localization.LinesKey;
import art.arcane.volmlib.util.localization.LinesValue;
import art.arcane.volmlib.util.localization.LocaleOverlay;
import art.arcane.volmlib.util.localization.LocalizationCandidate;
import art.arcane.volmlib.util.localization.LocalizationIssue;
import art.arcane.volmlib.util.localization.LocalizationManager;
import art.arcane.volmlib.util.localization.LocalizationReloadResult;
import art.arcane.volmlib.util.localization.LocalizationSnapshot;
import art.arcane.volmlib.util.localization.MessageArgument;
import art.arcane.volmlib.util.localization.MessageArgumentKind;
import art.arcane.volmlib.util.localization.MessageArgs;
import art.arcane.volmlib.util.localization.MessageCatalog;
import art.arcane.volmlib.util.localization.MessageKey;
import art.arcane.volmlib.util.localization.MessageValue;
import art.arcane.volmlib.util.localization.PluralKey;
import art.arcane.volmlib.util.localization.PluralSelector;
import art.arcane.volmlib.util.localization.ResolvedLines;
import art.arcane.volmlib.util.localization.ResolvedText;
import art.arcane.volmlib.util.localization.TextKey;
import art.arcane.volmlib.util.localization.TextValue;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.command.CommandSender;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public final class ReactLanguage {
  private static final long MAX_LOCALE_BYTES = 2L * 1024L * 1024L;
  private static final int MAX_REPORTED_ISSUES = 12;
  private static final Pattern LOCALE_NAME = Pattern.compile("[A-Za-z0-9_-]+");
  private static final String LEGACY_CODES = "0123456789abcdefklmnorx";
  private static final MessageCatalog CATALOG = ReactMessages.catalog();
  private static final MiniMessage MINI_MESSAGE = MiniMessage.builder().strict(true).build();
  private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();
  private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();
  private static final LocalizationManager MANAGER = new LocalizationManager(
      LocalizationCandidate.english(CATALOG, PluralSelector.oneOther())
  );
  private static volatile String activeLocale = CATALOG.englishLocale();

  static {
    validateCatalogTemplates();
  }

  private ReactLanguage() {
  }

  public static boolean initialize() {
    return reload();
  }

  public static boolean reload() {
    return reload(null);
  }

  public static boolean reload(File overrideFile, String rawContent) {
    OverrideHotloadSnapshot hotloadSnapshot = overrideFile == null || rawContent == null
        ? null
        : new OverrideHotloadSnapshot(normalizedPath(overrideFile), rawContent);
    if (hotloadSnapshot != null) {
      String configuredLocale = normalizeLocale(ReactConfiguration.get().getLanguage());
      File activeOverride = new File(overrideFolder(), configuredLocale + ".toml");
      if (!hotloadSnapshot.path().equals(normalizedPath(activeOverride))) {
        return true;
      }
    }
    return reload(hotloadSnapshot);
  }

  private static boolean reload(OverrideHotloadSnapshot hotloadSnapshot) {
    String configuredLocale = ReactConfiguration.get().getLanguage();
    String requestedLocale = configuredLocale == null || configuredLocale.isBlank()
        ? CATALOG.englishLocale()
        : configuredLocale.trim();
    LocalizationReloadResult result = MANAGER.reload(
        () -> loadCandidate(normalizeLocale(configuredLocale), hotloadSnapshot)
    );
    if (!result.applied()) {
      reportRejectedReload(requestedLocale, result);
      return false;
    }

    activeLocale = normalizeLocale(configuredLocale);
    int warningCount = result.validation().warnings().size();
    React.info("Loaded locale " + requestedLocale + " with " + warningCount + " fallback "
        + (warningCount == 1 ? "entry" : "entries") + ".");
    return true;
  }

  public static String activeLocale() {
    return activeLocale;
  }

  public static File overrideFolder() {
    return new File(React.instance.getDataFolder(), "languages/overrides");
  }

  public static boolean isOverrideFile(File file) {
    if (file == null || !file.getName().toLowerCase(Locale.ROOT).endsWith(".toml")) {
      return false;
    }
    File parent = file.getParentFile();
    return parent != null && parent.getAbsoluteFile().equals(overrideFolder().getAbsoluteFile());
  }

  public static Component component(MessageKey key) {
    return component(key, MessageArgs.empty());
  }

  public static Component component(MessageKey key, MessageArgument... arguments) {
    return component(key, arguments(arguments));
  }

  public static Component component(MessageKey key, MessageArgs arguments) {
    LocalizationSnapshot snapshot = MANAGER.snapshot();
    return render(snapshot, key, arguments);
  }

  public static Component prefixedComponent(MessageKey key) {
    return prefixedComponent(key, MessageArgs.empty());
  }

  public static Component prefixedComponent(MessageKey key, MessageArgument... arguments) {
    return prefixedComponent(key, arguments(arguments));
  }

  public static Component prefixedComponent(MessageKey key, MessageArgs arguments) {
    LocalizationSnapshot snapshot = MANAGER.snapshot();
    Component prefix = render(snapshot, RuntimeMessages.PREFIX, MessageArgs.empty());
    return prefix.append(render(snapshot, key, arguments));
  }

  public static String text(MessageKey key) {
    return text(key, MessageArgs.empty());
  }

  public static String text(MessageKey key, MessageArgument... arguments) {
    return text(key, arguments(arguments));
  }

  public static String text(MessageKey key, MessageArgs arguments) {
    return LEGACY.serialize(component(key, arguments));
  }

  public static String prefixedText(MessageKey key, MessageArgument... arguments) {
    return LEGACY.serialize(prefixedComponent(key, arguments));
  }

  public static String plain(MessageKey key, MessageArgument... arguments) {
    return PLAIN.serialize(component(key, arguments));
  }

  public static String plain(MessageKey key, MessageArgs arguments) {
    return PLAIN.serialize(component(key, arguments));
  }

  public static String raw(MessageKey key, MessageArgument... arguments) {
    return raw(key, arguments(arguments));
  }

  public static String raw(MessageKey key, MessageArgs arguments) {
    LocalizationSnapshot snapshot = MANAGER.snapshot();
    if (!(key instanceof TextKey textKey)) {
      throw new IllegalArgumentException("Raw messages must use text keys: " + key.id());
    }
    ResolvedText resolved = snapshot.resolve(textKey, arguments);
    return interpolate(resolved.template(), resolved.arguments(), false);
  }

  public static String raw(String id, String fallback) {
    MessageKey key = CATALOG.key(id);
    if (!(key instanceof TextKey textKey)) {
      return fallback;
    }
    return raw(textKey);
  }

  public static void send(CommandSender sender, MessageKey key, MessageArgument... arguments) {
    if (sender != null) {
      // audience delivery: spigot CommandSender has no sendMessage(Component); the router
      // sends console/RCON via the legacy String path (facade drops console chat on spigot)
      React.audiences().sender(sender).sendMessage(component(key, arguments));
    }
  }

  public static void send(VolmitSender sender, MessageKey key, MessageArgument... arguments) {
    if (sender != null) {
      sender.sendComponent(component(key, arguments));
    }
  }

  public static void sendPrefixed(CommandSender sender, MessageKey key, MessageArgument... arguments) {
    if (sender != null) {
      React.audiences().sender(sender).sendMessage(prefixedComponent(key, arguments));
    }
  }

  public static void sendPrefixed(VolmitSender sender, MessageKey key, MessageArgument... arguments) {
    if (sender != null) {
      sender.sendComponent(prefixedComponent(key, arguments));
    }
  }

  public static DirectorTextResolver directorResolver() {
    return (key, arguments) -> {
      MessageKey definition = CATALOG.key(key.id());
      if (!(definition instanceof TextKey textKey)) {
        return DirectorTextResolver.ENGLISH.resolve(key, arguments);
      }
      return plain(textKey, arguments).replace(String.valueOf('\u00A7'), "");
    };
  }

  static LocalizationReloadResult reloadCandidate(LocalizationCandidate candidate) {
    return MANAGER.reload(candidate);
  }

  static LocalizationSnapshot snapshot() {
    return MANAGER.snapshot();
  }

  private static LocalizationCandidate loadCandidate(
      String locale,
      OverrideHotloadSnapshot hotloadSnapshot
  ) throws Exception {
    if (hotloadSnapshot == null) {
      Files.createDirectories(overrideFolder().toPath());
    }
    List<LocaleOverlay> overlays = new ArrayList<>();
    File override = new File(overrideFolder(), locale + ".toml");
    if (hotloadSnapshot != null && hotloadSnapshot.path().equals(normalizedPath(override))) {
      overlays.add(loadSnapshotOverlay(override, locale, hotloadSnapshot.rawContent()));
    } else if (override.exists()) {
      overlays.add(loadFileOverlay(override, locale));
    }

    if (!CATALOG.englishLocale().equalsIgnoreCase(locale)) {
      LocaleOverlay bundled = loadBundledOverlay(locale);
      if (bundled != null) {
        overlays.add(bundled);
      }
    }
    return new LocalizationCandidate(CATALOG, overlays, PluralSelector.oneOther());
  }

  private static LocaleOverlay loadFileOverlay(File file, String locale) throws Exception {
    if (!file.isFile()) {
      throw new IllegalArgumentException("Locale override is not a regular file: " + file.getPath());
    }
    if (file.length() > MAX_LOCALE_BYTES) {
      throw new IllegalArgumentException("Locale override is too large: " + file.getPath());
    }
    return parseOverlay(file.getPath(), locale, Files.readString(file.toPath()));
  }

  private static LocaleOverlay loadSnapshotOverlay(File file, String locale, String rawContent) {
    if (rawContent.getBytes(StandardCharsets.UTF_8).length > MAX_LOCALE_BYTES) {
      throw new IllegalArgumentException("Locale override is too large: " + file.getPath());
    }
    return parseOverlay(file.getPath(), locale, rawContent);
  }

  private static String normalizedPath(File file) {
    return file.toPath().toAbsolutePath().normalize().toString();
  }

  private static LocaleOverlay loadBundledOverlay(String locale) throws Exception {
    String resourceName = "languages/" + locale + ".toml";
    InputStream input = React.instance.getResource(resourceName);
    if (input == null) {
      React.warn("No bundled locale exists for " + locale + "; code-owned English will be used.");
      return null;
    }
    try (InputStream stream = input) {
      String raw = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
      return parseOverlay("bundled:" + resourceName, locale, raw);
    }
  }

  static LocaleOverlay parseOverlay(String source, String locale, String raw) {
    LocaleOverlay.Builder builder = LocaleOverlay.builder(source, locale);
    if (raw == null || raw.isBlank()) {
      return builder.build();
    }
    JsonElement parsed = ConfigFileSupport.parseToJsonElement(raw, new File(locale + ".toml"));
    if (parsed == null || !parsed.isJsonObject()) {
      throw new IllegalArgumentException("Locale source is not valid TOML: " + source);
    }
    appendOverlay(builder, parsed.getAsJsonObject(), "", source);
    return builder.build();
  }

  private static void appendOverlay(LocaleOverlay.Builder builder, JsonObject object, String prefix, String source) {
    for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
      String entryKey = normalizeOverlayKey(entry.getKey());
      String key = prefix.isEmpty() ? entryKey : prefix + "." + entryKey;
      JsonElement value = entry.getValue();
      if (value == null || value.isJsonNull()) {
        throw new IllegalArgumentException("Locale value cannot be null: " + key);
      }
      if (value.isJsonObject()) {
        appendOverlay(builder, value.getAsJsonObject(), key, source);
      } else if (value.isJsonArray()) {
        List<String> lines = readLines(key, value.getAsJsonArray());
        validateLines(source + ":" + key, key, lines);
        builder.lines(key, lines);
      } else if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
        String template = value.getAsString();
        validateTemplate(source + ":" + key, key, template);
        builder.text(key, template);
      } else {
        throw new IllegalArgumentException("Unsupported locale value: " + key);
      }
    }
  }

  private record OverrideHotloadSnapshot(String path, String rawContent) {
  }

  private static String normalizeOverlayKey(String key) {
    if (key.length() < 2) {
      return key;
    }
    char first = key.charAt(0);
    char last = key.charAt(key.length() - 1);
    if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
      return key.substring(1, key.length() - 1);
    }
    return key;
  }

  private static List<String> readLines(String key, JsonArray array) {
    List<String> lines = new ArrayList<>(array.size());
    for (JsonElement value : array) {
      if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
        throw new IllegalArgumentException("Locale line must be text: " + key);
      }
      lines.add(value.getAsString());
    }
    return lines;
  }

  private static Component render(LocalizationSnapshot snapshot, MessageKey key, MessageArgs arguments) {
    if (key instanceof TextKey textKey) {
      ResolvedText resolved = snapshot.resolve(textKey, arguments);
      return MINI_MESSAGE.deserialize(interpolate(resolved.template(), resolved.arguments(), true));
    }
    if (key instanceof LinesKey linesKey) {
      ResolvedLines resolved = snapshot.resolve(linesKey, arguments);
      return MINI_MESSAGE.deserialize(interpolate(String.join("\n", resolved.lines()), resolved.arguments(), true));
    }
    if (key instanceof PluralKey pluralKey) {
      ResolvedText resolved = snapshot.resolve(pluralKey, arguments);
      return MINI_MESSAGE.deserialize(interpolate(resolved.template(), resolved.arguments(), true));
    }
    throw new IllegalArgumentException("Unsupported message key: " + key.id());
  }

  private static String interpolate(String template, MessageArgs arguments, boolean escapeMiniMessage) {
    StringBuilder output = new StringBuilder(template.length());
    int index = 0;
    while (index < template.length()) {
      char current = template.charAt(index);
      if (current == '{' && index + 1 < template.length() && template.charAt(index + 1) == '{') {
        output.append('{');
        index += 2;
        continue;
      }
      if (current == '}' && index + 1 < template.length() && template.charAt(index + 1) == '}') {
        output.append('}');
        index += 2;
        continue;
      }
      if (current != '{') {
        output.append(current);
        index++;
        continue;
      }
      int end = template.indexOf('}', index + 1);
      String name = template.substring(index + 1, end);
      MessageArgument argument = arguments.require(name);
      String replacement = String.valueOf(argument.value());
      if (escapeMiniMessage && argument.kind() == MessageArgumentKind.UNTRUSTED) {
        replacement = MINI_MESSAGE.escapeTags(stripLegacyCodes(replacement));
      }
      output.append(replacement);
      index = end + 1;
    }
    return output.toString();
  }

  private static String stripLegacyCodes(String value) {
    int marker = value.indexOf('§');
    if (marker < 0) {
      return value;
    }
    StringBuilder output = new StringBuilder(value.length());
    output.append(value, 0, marker);
    for (int index = marker; index < value.length(); index++) {
      char current = value.charAt(index);
      if (current != '§') {
        output.append(current);
        continue;
      }
      if (index + 1 < value.length() && LEGACY_CODES.indexOf(Character.toLowerCase(value.charAt(index + 1))) >= 0) {
        index++;
      }
    }
    return output.toString();
  }

  private static MessageArgs arguments(MessageArgument... arguments) {
    MessageArgs.Builder builder = MessageArgs.builder();
    if (arguments != null) {
      for (MessageArgument argument : arguments) {
        builder.add(argument);
      }
    }
    return builder.build();
  }

  private static String normalizeLocale(String locale) {
    String value = locale == null || locale.isBlank() ? CATALOG.englishLocale() : locale.trim();
    if (!LOCALE_NAME.matcher(value).matches()) {
      throw new IllegalArgumentException("Invalid locale name: " + value);
    }
    return value;
  }

  private static void validateCatalogTemplates() {
    for (MessageKey key : CATALOG.keys()) {
      MessageValue value = key.englishValue();
      if (value instanceof TextValue text) {
        validateTemplate("catalog:" + key.id(), key.id(), text.template());
      } else if (value instanceof LinesValue lines) {
        validateLines("catalog:" + key.id(), key.id(), lines.lines());
      }
    }
  }

  private static void validateLines(String path, String key, List<String> lines) {
    for (int index = 0; index < lines.size(); index++) {
      validateTemplate(path + "[" + index + "]", key, lines.get(index));
    }
  }

  private static void validateTemplate(String path, String key, String template) {
    TextValue value = new TextValue(template);
    if (isRawTextKey(key)) {
      return;
    }
    validatePlaceholderPlacement(path, template);
    MessageArgs.Builder arguments = MessageArgs.builder();
    for (String placeholder : value.placeholders()) {
      arguments.untrusted(placeholder, "value");
    }
    try {
      MINI_MESSAGE.deserialize(interpolate(template, arguments.build(), true));
    } catch (RuntimeException exception) {
      throw new IllegalArgumentException(path + ": invalid MiniMessage", exception);
    }
  }

  private static void validatePlaceholderPlacement(String path, String template) {
    boolean insideTag = false;
    for (int index = 0; index < template.length(); index++) {
      char current = template.charAt(index);
      if (current == '\\') {
        index++;
        continue;
      }
      if (current == '<') {
        insideTag = true;
        continue;
      }
      if (current == '>') {
        insideTag = false;
        continue;
      }
      if (insideTag && current == '{' && (index + 1 >= template.length() || template.charAt(index + 1) != '{')) {
        throw new IllegalArgumentException(path + ": message placeholders cannot be used inside MiniMessage tags");
      }
    }
  }

  private static boolean isRawTextKey(String key) {
    return key.startsWith("config.documentation.annotation.")
        || key.startsWith("renderer.")
        || (key.startsWith("test.") && !key.startsWith("test.result."));
  }

  private static void reportRejectedReload(String locale, LocalizationReloadResult result) {
    React.error("Rejected locale reload for " + locale + "; continuing with " + activeLocale + ".");
    List<LocalizationIssue> issues = result.validation().errors();
    for (int index = 0; index < Math.min(issues.size(), MAX_REPORTED_ISSUES); index++) {
      LocalizationIssue issue = issues.get(index);
      React.error(issue.source() + " [" + issue.key() + "]: " + issue.detail());
    }
    if (issues.size() > MAX_REPORTED_ISSUES) {
      React.error((issues.size() - MAX_REPORTED_ISSUES) + " additional locale errors were omitted.");
    }
    if (result.failure() != null) {
      result.failure().printStackTrace();
    }
  }
}
