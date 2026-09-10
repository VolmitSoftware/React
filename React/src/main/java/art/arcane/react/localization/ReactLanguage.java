package art.arcane.react.localization;

import art.arcane.react.React;
import art.arcane.react.localization.catalog.RuntimeMessages;
import art.arcane.react.model.ReactConfiguration;
import art.arcane.react.util.plugin.VolmitSender;
import art.arcane.react.util.project.config.ConfigFileSupport;
import art.arcane.react.util.project.config.TomlCodec;
import art.arcane.volmlib.util.director.DirectorTextResolver;
import art.arcane.volmlib.util.director.help.DirectorMiniMenu;
import art.arcane.volmlib.util.io.AtomicFileIO;
import art.arcane.volmlib.util.localization.PluginLanguageService;
import art.arcane.volmlib.util.localization.PluginLanguageEditor;
import art.arcane.volmlib.util.localization.LanguageFileEditor;
import art.arcane.volmlib.util.localization.TomlLanguageEditor;
import art.arcane.volmlib.util.localization.RemoteLanguageCatalog;
import art.arcane.volmlib.util.localization.BukkitLanguageSwitcher;
import art.arcane.volmlib.util.localization.LanguageAudience;
import art.arcane.volmlib.util.localization.VolmitLocales;
import art.arcane.volmlib.util.localization.LinesKey;
import art.arcane.volmlib.util.localization.LinesValue;
import art.arcane.volmlib.util.localization.LocaleOverlay;
import art.arcane.volmlib.util.localization.LocalizationCandidate;
import art.arcane.volmlib.util.localization.LocalizationIssue;
import art.arcane.volmlib.util.localization.LocalizationIssueCode;
import art.arcane.volmlib.util.localization.LocalizationManager;
import art.arcane.volmlib.util.localization.LocalizationReloadResult;
import art.arcane.volmlib.util.localization.LocalizationSnapshot;
import art.arcane.volmlib.util.localization.LocalizationValidationResult;
import art.arcane.volmlib.util.localization.LocalizationValidator;
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
import art.arcane.volmlib.util.plugin.ComponentMessenger;
import art.arcane.volmlib.util.plugin.ComponentText;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import static art.arcane.volmlib.util.config.TomlCodec.toJsonElement;

public final class ReactLanguage {
  private static final Object SNAPSHOT_LOCK = new Object();
  private static final long MAX_LOCALE_BYTES = 2L * 1024L * 1024L;
  private static final int MAX_REPORTED_ISSUES = 12;
  private static final int MAX_NORMALIZED_TEMPLATES = 4096;
  private static final Map<String, String> NORMALIZED_TEMPLATES = new ConcurrentHashMap<>();
  private static final Pattern LOCALE_NAME = Pattern.compile("[A-Za-z0-9_-]+");
  private static final String LEGACY_CODES = "0123456789abcdefklmnorx";
  private static final String ENGLISH_FALLBACK_SOURCE = "code-owned-English:";
  private static final MessageCatalog CATALOG = ReactMessages.catalog();
  private static final MiniMessage MINI_MESSAGE = MiniMessage.builder().strict(true).build();
  private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();
  private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();
  private static final LocalizationManager MANAGER = new LocalizationManager(
      LocalizationCandidate.english(CATALOG, PluralSelector.oneOther())
  );
  private static volatile String activeLocale = CATALOG.englishLocale();
  private static RemoteLanguageCatalog remoteCatalog;
  private static PluginLanguageService languageService;
  private static BukkitLanguageSwitcher languageSwitcher;

  static {
    validateCatalogTemplates();
  }

  private ReactLanguage() {
  }

  public static boolean initialize() {
    close();
    remoteCatalog = RemoteLanguageCatalog.load(new RemoteLanguageCatalog.Options(
        "React", URI.create("https://raw.githubusercontent.com/VolmitSoftware/React/"),
        "React/src/main/resources/languages", ".toml", "language-source.properties",
        ReactLanguage.class.getClassLoader()));
    boolean loaded = reload();
    languageService = new PluginLanguageService(new PluginLanguageService.Options(
        languageFolder().toPath().resolve("language-preferences.properties"), VolmitLocales::all,
        () -> ReactConfiguration.get().getLanguage(), MANAGER::snapshot,
        locale -> LocalizationSnapshot.create(loadCandidate(locale, null)), ReactLanguage::selectDefault,
        React.instance.getLogger()));
    languageSwitcher = BukkitLanguageSwitcher.register(React.instance, languageService,
        new BukkitLanguageSwitcher.Options("react", "react.use",
            DirectorMiniMenu.Theme.reactBlue(), directorResolver(), editorOptions()));
    return loaded;
  }

  public static void close() {
    NORMALIZED_TEMPLATES.clear();
    if (languageSwitcher != null) {
      languageSwitcher.close();
      languageSwitcher = null;
    }
    if (languageService != null) {
      languageService.close();
      languageService = null;
    }
    if (remoteCatalog != null) {
      remoteCatalog.close();
      remoteCatalog = null;
    }
  }

  public static BukkitLanguageSwitcher switcher() {
    return languageSwitcher;
  }

  public static PluginLanguageEditor.Options editorOptions() {
    return new PluginLanguageEditor.Options(
        locale -> LocalizationSnapshot.create(loadCandidate(locale, null)), ReactLanguage::writeMessage);
  }

  private static LocalizationSnapshot writeMessage(PluginLanguageEditor.Edit edit) throws IOException {
    LocaleOverlay proposed = LocaleOverlay.builder("language editor", edit.locale()).put(edit.key(), edit.value()).build();
    LocalizationSnapshot.create(new LocalizationCandidate(CATALOG, List.of(proposed), PluralSelector.oneOther()));
    if (edit.value() instanceof TextValue text) {
      validateTemplate(edit.key(), edit.key(), text.template());
    } else if (edit.value() instanceof LinesValue lines) {
      validateLines(edit.key(), edit.key(), lines.lines());
    }
    File file = new File(languageFolder(), edit.locale() + ".toml");
    SavedLanguage saved = LanguageFileEditor.update(file.toPath(), raw -> {
      LocalizationSnapshot current = editorSnapshot(edit.locale(), file, raw);
      if (!current.value(CATALOG.require(edit.key())).equals(edit.expected())) {
        throw new IOException("Language message changed; reopen it before saving");
      }
      String updated = TomlLanguageEditor.upsert(raw, edit.key(), edit.value()).content();
      SavedLanguage prepared = new SavedLanguage(updated, editorSnapshot(edit.locale(), file, updated));
      return new LanguageFileEditor.Prepared<>(updated, prepared);
    });
    ConfigFileSupport.noteSelfWrite(file, saved.rawContent());
    NORMALIZED_TEMPLATES.clear();
    synchronized (SNAPSHOT_LOCK) {
      if (edit.locale().equals(activeLocale)) {
        MANAGER.install(saved.snapshot());
      }
    }
    return saved.snapshot();
  }

  private static LocalizationSnapshot editorSnapshot(String locale, File file, String raw) throws IOException {
    try {
      return LocalizationSnapshot.create(new LocalizationCandidate(CATALOG,
          List.of(parseRuntimeOverlay(file.toString(), locale, raw)), PluralSelector.oneOther()));
    } catch (Exception failure) {
      throw new IOException("Could not validate React language " + locale, failure);
    }
  }

  private static void selectDefault(String locale, LocalizationSnapshot prepared) throws Exception {
    File file = React.instance.getDataFile("react.toml");
    JsonObject configuration = ConfigFileSupport.parseToJsonElement(Files.readString(file.toPath()), file).getAsJsonObject();
    configuration.addProperty("language", locale);
    ReactConfiguration next = new Gson().fromJson(configuration, ReactConfiguration.class);
    String raw = TomlCodec.toToml(next, "main-config");
    AtomicFileIO.writeString(file.toPath(), raw);
    ReactConfiguration.applyHotloadSnapshot(next);
    synchronized (SNAPSHOT_LOCK) {
      MANAGER.install(prepared);
      NORMALIZED_TEMPLATES.clear();
      activeLocale = locale;
    }
    ConfigFileSupport.noteSelfWrite(file, raw);
  }

  public static boolean reload() {
    return reload(null, null);
  }

  public static boolean reload(File languageFile, String rawContent) {
    String configuredLocale = ReactConfiguration.get().getLanguage();
    try {
      return applyPreparedHotload(prepareHotload(languageFile, rawContent, configuredLocale));
    } catch (Exception failure) {
      LocalizationSnapshot retained = MANAGER.snapshot();
      reportRejectedReload(configuredLocale, new LocalizationReloadResult(
          false, retained, retained, LocalizationValidationResult.empty(), failure));
      return false;
    }
  }

  public static PreparedReload prepareHotload(
      File languageFile,
      String rawContent,
      String configuredLocale
  ) throws Exception {
    String normalizedLocale = normalizeLocale(configuredLocale);
    boolean serverDefault = languageFile == null || rawContent == null;
    LanguageHotloadSnapshot hotloadSnapshot = serverDefault
        ? null
        : new LanguageHotloadSnapshot(normalizedPath(languageFile), rawContent);
    if (hotloadSnapshot != null) {
      if (!isLanguageFile(languageFile)) {
        throw new IllegalArgumentException("Not a React language file: " + languageFile);
      }
      normalizedLocale = normalizeLocale(languageFile.getName().substring(0, languageFile.getName().length() - 5));
    } else {
      createEnglishLanguageIfMissing();
      if (languageService != null) {
        synchronized (SNAPSHOT_LOCK) {
          if (normalizedLocale.equals(activeLocale)) {
            return new PreparedReload(MANAGER.snapshot(), normalizedLocale, normalizedLocale, true, true);
          }
        }
        File selectedFile = new File(languageFolder(), normalizedLocale + ".toml");
        if (selectedFile.isFile()) {
          if (selectedFile.length() > MAX_LOCALE_BYTES) {
            throw new IllegalArgumentException("Locale file is too large: " + selectedFile.getPath());
          }
          hotloadSnapshot = new LanguageHotloadSnapshot(normalizedPath(selectedFile), Files.readString(selectedFile.toPath()));
        }
      }
    }
    LocalizationCandidate candidate = loadCandidate(normalizedLocale, hotloadSnapshot);
    LocalizationSnapshot snapshot = LocalizationSnapshot.create(candidate);
    return new PreparedReload(snapshot, normalizedLocale, normalizedLocale,
        serverDefault, false);
  }

  public static boolean applyPreparedHotload(PreparedReload prepared) {
    if (prepared.unchanged()) {
      return true;
    }
    PluginLanguageService service = languageService;
    try {
      if (service == null) {
        installPreparedSnapshot(prepared, null);
      } else {
        service.commitUpdate(() -> {
          installPreparedSnapshot(prepared, service);
          return null;
        });
      }
    } catch (IOException | IllegalStateException failure) {
      React.reportError("Locale reload failed for " + prepared.requestedLocale(), failure);
      return false;
    }
    int warningCount = fallbackEntryCount(prepared.snapshot().validation());
    React.verbose("Loaded locale " + prepared.requestedLocale() + " with " + warningCount + " fallback "
        + (warningCount == 1 ? "entry" : "entries") + ".");
    return true;
  }

  private static void installPreparedSnapshot(PreparedReload prepared, PluginLanguageService service) {
    synchronized (SNAPSHOT_LOCK) {
      if (prepared.serverDefault() || prepared.normalizedLocale().equals(activeLocale)) {
        MANAGER.install(prepared.snapshot());
        activeLocale = prepared.normalizedLocale();
      }
      NORMALIZED_TEMPLATES.clear();
    }
    if (service != null) {
      service.cache(prepared.normalizedLocale(), prepared.snapshot());
    }
  }

  public static String activeLocale() {
    return activeLocale;
  }

  public static File languageFolder() {
    return new File(React.instance.getDataFolder(), "languages");
  }

  public static boolean isLanguageFile(File file) {
    if (file == null || !file.getName().toLowerCase(Locale.ROOT).endsWith(".toml")) {
      return false;
    }
    File parent = file.getParentFile();
    return parent != null && normalizedPath(parent).equals(normalizedPath(languageFolder()));
  }

  public static Component component(MessageKey key) {
    return component(key, MessageArgs.empty());
  }

  public static Component component(MessageKey key, MessageArgument... arguments) {
    return component(key, arguments(arguments));
  }

  public static Component component(MessageKey key, MessageArgs arguments) {
    LocalizationSnapshot snapshot = selectedSnapshot();
    return render(snapshot, key, arguments);
  }

  public static Component prefixedComponent(MessageKey key) {
    return prefixedComponent(key, MessageArgs.empty());
  }

  public static Component prefixedComponent(MessageKey key, MessageArgument... arguments) {
    return prefixedComponent(key, arguments(arguments));
  }

  public static Component prefixedComponent(MessageKey key, MessageArgs arguments) {
    LocalizationSnapshot snapshot = selectedSnapshot();
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
    LocalizationSnapshot snapshot = selectedSnapshot();
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
      LanguageAudience.run(sender instanceof Player player ? player.getUniqueId() : null,
          () -> ComponentMessenger.send(sender, ComponentText.component(component(key, arguments))));
    }
  }

  public static void send(VolmitSender sender, MessageKey key, MessageArgument... arguments) {
    if (sender != null) {
      LanguageAudience.run(sender.isPlayer() ? sender.player().getUniqueId() : null,
          () -> sender.sendComponent(component(key, arguments)));
    }
  }

  public static void sendPrefixed(CommandSender sender, MessageKey key, MessageArgument... arguments) {
    if (sender != null) {
      LanguageAudience.run(sender instanceof Player player ? player.getUniqueId() : null,
          () -> ComponentMessenger.send(sender, ComponentText.component(prefixedComponent(key, arguments))));
    }
  }

  public static void sendPrefixed(VolmitSender sender, MessageKey key, MessageArgument... arguments) {
    if (sender != null) {
      LanguageAudience.run(sender.isPlayer() ? sender.player().getUniqueId() : null,
          () -> sender.sendComponent(prefixedComponent(key, arguments)));
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
    NORMALIZED_TEMPLATES.clear();
    return MANAGER.reload(candidate);
  }

  static LocalizationSnapshot snapshot() {
    return MANAGER.snapshot();
  }

  static int fallbackEntryCount(LocalizationValidationResult validation) {
    int count = 0;
    for (LocalizationIssue issue : validation.warnings()) {
      if (issue.code() == LocalizationIssueCode.MISSING_KEY && !issue.source().startsWith(ENGLISH_FALLBACK_SOURCE)) {
        count++;
      }
    }
    return count;
  }

  private static LocalizationSnapshot selectedSnapshot() {
    return languageService == null ? MANAGER.snapshot() : languageService.snapshot();
  }

  private static LocalizationCandidate loadCandidate(
      String locale,
      LanguageHotloadSnapshot hotloadSnapshot
  ) throws Exception {
    if (hotloadSnapshot == null) {
      createEnglishLanguageIfMissing();
    }
    List<LocaleOverlay> overlays = new ArrayList<>(2);
    File languageFile = new File(languageFolder(), locale + ".toml");
    if (hotloadSnapshot != null && hotloadSnapshot.path().equals(normalizedPath(languageFile))) {
      overlays.add(loadSnapshotOverlay(languageFile, locale, hotloadSnapshot.rawContent()));
    } else if (languageFile.exists()) {
      overlays.add(loadFileOverlay(languageFile, locale));
    } else if (!CATALOG.englishLocale().equalsIgnoreCase(locale)) {
      overlays.add(loadDownloadedOverlay(locale));
    }
    if (!CATALOG.englishLocale().equalsIgnoreCase(locale) && !overlays.isEmpty()) {
      LocaleOverlay selected = overlays.getFirst();
      LocaleOverlay.Builder englishFallback = LocaleOverlay.builder(ENGLISH_FALLBACK_SOURCE + locale, locale);
      boolean missingMessages = false;
      for (MessageKey key : CATALOG.keys()) {
        if (selected.value(key.id()) == null) {
          englishFallback.put(key.id(), key.englishValue());
          missingMessages = true;
        }
      }
      if (missingMessages) {
        overlays.add(englishFallback.build());
      }
    }
    return new LocalizationCandidate(CATALOG, overlays, PluralSelector.oneOther());
  }

  private static void createEnglishLanguageIfMissing() throws IOException {
    Path english = languageFolder().toPath().resolve(CATALOG.englishLocale() + ".toml");
    if (Files.exists(english)) {
      return;
    }
    String raw = ReactLanguageReference.englishCatalog();
    AtomicFileIO.writeString(english, raw);
    ConfigFileSupport.noteSelfWrite(english.toFile(), raw);
  }

  private static LocaleOverlay loadFileOverlay(File file, String locale) throws Exception {
    if (!file.isFile()) {
      throw new IllegalArgumentException("Locale file is not a regular file: " + file.getPath());
    }
    if (file.length() > MAX_LOCALE_BYTES) {
      throw new IllegalArgumentException("Locale file is too large: " + file.getPath());
    }
    return parseRuntimeOverlay(file.getPath(), locale, Files.readString(file.toPath()));
  }

  private static LocaleOverlay loadSnapshotOverlay(File file, String locale, String rawContent) {
    if (rawContent.getBytes(StandardCharsets.UTF_8).length > MAX_LOCALE_BYTES) {
      throw new IllegalArgumentException("Locale file is too large: " + file.getPath());
    }
    return LocalizationValidator.validValues(CATALOG, parseOverlay(file.getPath(), locale, rawContent, true));
  }

  private static String normalizedPath(File file) {
    return file.toPath().toAbsolutePath().normalize().toString();
  }

  private static LocaleOverlay loadDownloadedOverlay(String locale) throws Exception {
    Path file = languageFolder().toPath().resolve(locale + ".toml");
    String raw = remoteCatalog.readOrInstall(locale, file, (selectedLocale, content) ->
        LocalizationSnapshot.create(new LocalizationCandidate(CATALOG,
            List.of(parseRuntimeOverlay(file.toString(), selectedLocale, content)), PluralSelector.oneOther())));
    LocaleOverlay overlay = parseRuntimeOverlay(file.toString(), locale, raw);
    ConfigFileSupport.noteSelfWrite(file.toFile(), raw);
    return overlay;
  }

  static LocaleOverlay parseOverlay(String source, String locale, String raw) {
    return parseOverlay(source, locale, raw, false);
  }

  static LocaleOverlay parseRuntimeOverlay(String source, String locale, String raw) {
    try {
      return LocalizationValidator.validValues(CATALOG, parseOverlay(source, locale, raw, true));
    } catch (IllegalArgumentException invalid) {
      React.reportError("Using English for unreadable language file " + source, invalid);
      return LocaleOverlay.builder(source, locale).build();
    }
  }

  private static LocaleOverlay parseOverlay(String source, String locale, String raw, boolean tolerant) {
    LocaleOverlay.Builder builder = LocaleOverlay.builder(source, locale);
    if (raw == null || raw.isBlank()) {
      return builder.build();
    }
    JsonElement parsed;
    try {
      parsed = toJsonElement(raw);
    } catch (IOException exception) {
      throw new IllegalArgumentException("Locale source is not valid TOML: " + source, exception);
    }
    if (parsed == null || !parsed.isJsonObject()) {
      throw new IllegalArgumentException("Locale source is not valid TOML: " + source);
    }
    appendOverlay(builder, parsed.getAsJsonObject(), "", source, tolerant);
    return builder.build();
  }

  private static void appendOverlay(LocaleOverlay.Builder builder, JsonObject object, String prefix, String source, boolean tolerant) {
    for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
      String entryKey = entry.getKey();
      String key = prefix.isEmpty() ? entryKey : prefix + "." + entryKey;
      JsonElement value = entry.getValue();
      try {
        if (value == null || value.isJsonNull()) {
          throw new IllegalArgumentException("Locale value cannot be null: " + key);
        }
        if (value.isJsonObject()) {
          appendOverlay(builder, value.getAsJsonObject(), key, source, tolerant);
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
      } catch (IllegalArgumentException invalid) {
        if (!tolerant) {
          throw invalid;
        }
      }
    }
  }

  private record LanguageHotloadSnapshot(String path, String rawContent) {
  }

  private record SavedLanguage(String rawContent, LocalizationSnapshot snapshot) {
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
      return renderTemplate(key, resolved.template(), resolved.arguments());
    }
    if (key instanceof LinesKey linesKey) {
      ResolvedLines resolved = snapshot.resolve(linesKey, arguments);
      return renderTemplate(key, String.join("\n", resolved.lines()), resolved.arguments());
    }
    if (key instanceof PluralKey pluralKey) {
      ResolvedText resolved = snapshot.resolve(pluralKey, arguments);
      return renderTemplate(key, resolved.template(), resolved.arguments());
    }
    throw new IllegalArgumentException("Unsupported message key: " + key.id());
  }

  private static Component renderTemplate(MessageKey key, String template, MessageArgs arguments) {
    if (isRawTextKey(key.id())) {
      return Component.text(interpolate(template, arguments, false));
    }
    return MINI_MESSAGE.deserialize(interpolate(normalizeTemplate(template), arguments, true));
  }

  private static String normalizeTemplate(String template) {
    String cached = NORMALIZED_TEMPLATES.get(template);
    if (cached != null) {
      return cached;
    }
    String normalized = MINI_MESSAGE.stripTags(template).equals(template)
        ? MINI_MESSAGE.serialize(MiniMessage.miniMessage().deserialize(ComponentText.normalizeMarkup(template)))
        : template;
    synchronized (NORMALIZED_TEMPLATES) {
      if (NORMALIZED_TEMPLATES.size() >= MAX_NORMALIZED_TEMPLATES) {
        NORMALIZED_TEMPLATES.clear();
      }
      NORMALIZED_TEMPLATES.put(template, normalized);
    }
    return normalized;
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
        replacement = stripLegacyCodes(replacement).replace("\\", "\\\\").replace("<", "\\<");
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
      MINI_MESSAGE.deserialize(interpolate(normalizeTemplate(template), arguments.build(), true));
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
      React.reportError("Locale reload failed for " + locale, result.failure());
    }
  }

  public record PreparedReload(
      LocalizationSnapshot snapshot,
      String requestedLocale,
      String normalizedLocale,
      boolean serverDefault,
      boolean unchanged
  ) {
  }
}
