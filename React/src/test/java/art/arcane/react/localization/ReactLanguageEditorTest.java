package art.arcane.react.localization;

import art.arcane.react.React;
import art.arcane.react.localization.catalog.CommandMessages;
import art.arcane.react.localization.catalog.EnvironmentMessages;
import art.arcane.volmlib.util.localization.LocalizationCandidate;
import art.arcane.volmlib.util.localization.LocaleOverlay;
import art.arcane.volmlib.util.localization.LocalizationSnapshot;
import art.arcane.volmlib.util.localization.PluginLanguageEditor;
import art.arcane.volmlib.util.localization.PluginLanguageService;
import art.arcane.volmlib.util.localization.PluralSelector;
import art.arcane.volmlib.util.localization.RemoteLanguageCatalog;
import art.arcane.volmlib.util.localization.TextValue;
import art.arcane.volmlib.util.localization.TomlLanguageEditor;
import art.arcane.volmlib.util.localization.VolmitLocales;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReactLanguageEditorTest {
  private static byte[] frenchLocale;

  @TempDir
  Path directory;

  private React previous;
  private RemoteLanguageCatalog previousRemote;
  private RemoteLanguageCatalog remote;
  private PluginLanguageService languages;
  private PluginLanguageService previousLanguages;
  private String previousActiveLocale;
  private PluginLanguageEditor editor;

  @BeforeAll
  static void readBundledFrenchLocale() throws Exception {
    frenchLocale = Files.readAllBytes(Path.of("src/main/resources/languages/fr_FR.toml"));
  }

  @BeforeEach
  void prepareEditor() throws Exception {
    previous = React.instance;
    previousActiveLocale = ReactLanguage.activeLocale();
    Field activeField = ReactLanguage.class.getDeclaredField("activeLocale");
    activeField.setAccessible(true);
    activeField.set(null, "en_US");
    React plugin = mock(React.class);
    when(plugin.getDataFolder()).thenReturn(directory.toFile());
    React.instance = plugin;
    Field field = ReactLanguage.class.getDeclaredField("remoteCatalog");
    field.setAccessible(true);
    previousRemote = (RemoteLanguageCatalog) field.get(null);
    remote = RemoteLanguageCatalog.load(new RemoteLanguageCatalog.Options(
        "React", URI.create("https://raw.githubusercontent.com/VolmitSoftware/React/"),
        "React/src/main/resources/languages", ".toml", "language-source.properties",
        ReactLanguage.class.getClassLoader()));
    field.set(null, remote);
    Files.createDirectories(directory.resolve("languages"));
    Files.write(directory.resolve("languages/fr_FR.toml"), frenchLocale);
    PluginLanguageEditor.Options options = ReactLanguage.editorOptions();
    LocalizationSnapshot english = LocalizationSnapshot.create(
        LocalizationCandidate.english(ReactMessages.catalog(), PluralSelector.oneOther()));
    languages = new PluginLanguageService(new PluginLanguageService.Options(
        directory.resolve("languages/language-preferences.properties"), VolmitLocales::all, () -> "en_US", () -> english,
        options.loader()::load, (locale, snapshot) -> {
          throw new AssertionError("Editing must not select a server language");
        }, Logger.getLogger("ReactLanguageEditorTest")));
    editor = new PluginLanguageEditor(languages, options);
    Field serviceField = ReactLanguage.class.getDeclaredField("languageService");
    serviceField.setAccessible(true);
    previousLanguages = (PluginLanguageService) serviceField.get(null);
    serviceField.set(null, languages);
  }

  @AfterEach
  void closeEditor() throws Exception {
    editor.close();
    languages.close();
    remote.close();
    Field field = ReactLanguage.class.getDeclaredField("remoteCatalog");
    field.setAccessible(true);
    field.set(null, previousRemote);
    Field serviceField = ReactLanguage.class.getDeclaredField("languageService");
    serviceField.setAccessible(true);
    serviceField.set(null, previousLanguages);
    Field activeField = ReactLanguage.class.getDeclaredField("activeLocale");
    activeField.setAccessible(true);
    activeField.set(null, previousActiveLocale);
    ReactLanguage.reloadCandidate(LocalizationCandidate.english(ReactMessages.catalog(), PluralSelector.oneOther()));
    React.instance = previous;
  }

  @Test
  void savesOneLocaleAndRefreshesItsPersonalSnapshotWithoutSelectingIt() throws Exception {
    UUID player = UUID.randomUUID();
    languages.selectPlayer(player, "fr_FR").get(5, TimeUnit.SECONDS);
    String active = ReactLanguage.activeLocale();
    PluginLanguageEditor.Document original = editor.load("fr_FR").get(5, TimeUnit.SECONDS);
    TextValue value = new TextValue("React version {version}");
    editor.save(new PluginLanguageEditor.Edit("fr_FR", EnvironmentMessages.REACT_VERSION.id(),
        original.snapshot().value(EnvironmentMessages.REACT_VERSION), value)).get(5, TimeUnit.SECONDS);

    String saved = Files.readString(directory.resolve("languages/fr_FR.toml"));
    assertTrue(saved.contains("React version {version}"));
    String originalHeader = new String(frenchLocale, StandardCharsets.UTF_8).lines()
        .takeWhile(line -> line.startsWith("#")).collect(Collectors.joining("\n"));
    String savedHeader = saved.lines().takeWhile(line -> line.startsWith("#")).collect(Collectors.joining("\n"));
    assertEquals(originalHeader, savedHeader);
    assertEquals(value, ReactLanguage.editorOptions().loader().load("fr_FR").value(EnvironmentMessages.REACT_VERSION));
    assertEquals(value, languages.snapshot(player).value(EnvironmentMessages.REACT_VERSION));
    assertEquals("fr_FR", languages.playerLocale(player).orElseThrow());
    assertEquals("en_US", languages.defaultLocale());
    assertEquals(active, ReactLanguage.activeLocale());
    assertTrue(Files.isRegularFile(directory.resolve("languages/en_US.toml")));
  }

  @Test
  void invalidMessageLeavesTheLocaleFileIntact() throws Exception {
    PluginLanguageEditor.Document original = editor.load("fr_FR").get(5, TimeUnit.SECONDS);
    editor.save(new PluginLanguageEditor.Edit("fr_FR", EnvironmentMessages.REACT_VERSION.id(),
        original.snapshot().value(EnvironmentMessages.REACT_VERSION), new TextValue("React version {version}")))
        .get(5, TimeUnit.SECONDS);
    Path file = directory.resolve("languages/fr_FR.toml");
    byte[] before = Files.readAllBytes(file);
    PluginLanguageEditor.Document saved = editor.load("fr_FR").get(5, TimeUnit.SECONDS);

    assertThrows(ExecutionException.class, () -> editor.save(new PluginLanguageEditor.Edit("fr_FR",
        EnvironmentMessages.REACT_VERSION.id(), saved.snapshot().value(EnvironmentMessages.REACT_VERSION),
        new TextValue("Missing placeholder"))).get(5, TimeUnit.SECONDS));
    assertArrayEquals(before, Files.readAllBytes(file));
  }

  @Test
  void incompleteDownloadedLocaleCanBeEditedWithoutBeingSelected() throws Exception {
    Files.writeString(directory.resolve("languages/fr_FR.toml"), "");
    PluginLanguageEditor.Document original = editor.load("fr_FR").get(5, TimeUnit.SECONDS);
    TextValue value = new TextValue("React version {version}");
    PluginLanguageEditor.Document saved = editor.save(new PluginLanguageEditor.Edit("fr_FR", EnvironmentMessages.REACT_VERSION.id(),
        original.snapshot().value(EnvironmentMessages.REACT_VERSION), value)).get(5, TimeUnit.SECONDS);
    assertEquals(value, saved.snapshot().value(EnvironmentMessages.REACT_VERSION));
    assertEquals("en_US", languages.defaultLocale());
  }

  @Test
  void selectsEveryRepositoryLocaleWithoutFallingBackToEnglish() throws Exception {
    UUID player = UUID.randomUUID();
    for (String locale : VolmitLocales.nonEnglish()) {
      Path source = Path.of("src/main/resources/languages", locale + ".toml");
      Files.write(directory.resolve("languages").resolve(locale + ".toml"), Files.readAllBytes(source));

      languages.selectPlayer(player, locale).get(5, TimeUnit.SECONDS);

      assertEquals(locale, languages.playerLocale(player).orElseThrow(), locale);
    }
  }

  @Test
  void selectsPartialPolishForPlayersAndServerWithoutRewritingIt() throws Exception {
    Path polish = directory.resolve("languages/pl_PL.toml");
    String raw = "# Server wording\n\"" + EnvironmentMessages.REACT_VERSION.id() + "\" = \"Wersja React {version}\"\n";
    Files.writeString(polish, raw);
    UUID player = UUID.randomUUID();
    languages.selectPlayer(player, "pl_PL").get(5, TimeUnit.SECONDS);

    assertEquals("pl_PL", languages.playerLocale(player).orElseThrow());
    assertEquals(new TextValue("Wersja React {version}"), languages.snapshot(player).value(EnvironmentMessages.REACT_VERSION));
    assertEquals(CommandMessages.MONITOR_DESCRIPTION.englishValue(), languages.snapshot(player).value(CommandMessages.MONITOR_DESCRIPTION));

    AtomicReference<String> serverLocale = new AtomicReference<>("en_US");
    AtomicReference<LocalizationSnapshot> serverSnapshot = new AtomicReference<>(languages.snapshot());
    try (PluginLanguageService server = new PluginLanguageService(new PluginLanguageService.Options(
        directory.resolve("server-players.properties"), VolmitLocales::all, serverLocale::get, serverSnapshot::get,
        ReactLanguage.editorOptions().loader()::load, (locale, snapshot) -> {
          serverLocale.set(locale);
          serverSnapshot.set(snapshot);
        }, Logger.getLogger("ReactLanguageSelectionTest")))) {
      server.selectDefault("pl_PL").get(5, TimeUnit.SECONDS);

      assertEquals("pl_PL", server.defaultLocale());
      assertEquals(new TextValue("Wersja React {version}"), server.snapshot().value(EnvironmentMessages.REACT_VERSION));
      assertEquals(CommandMessages.MONITOR_DESCRIPTION.englishValue(), server.snapshot().value(CommandMessages.MONITOR_DESCRIPTION));
    }
    assertEquals(raw, Files.readString(polish));
  }

  @Test
  void missingRequiredPlaceholderFallsBackWithoutRewritingTheLocale() throws Exception {
    Path polish = directory.resolve("languages/pl_PL.toml");
    String raw = "\"" + EnvironmentMessages.REACT_VERSION.id() + "\" = \"Wersja React\"\n";
    Files.writeString(polish, raw);

    LocalizationSnapshot snapshot = ReactLanguage.editorOptions().loader().load("pl_PL");
    assertEquals(EnvironmentMessages.REACT_VERSION.englishValue(), snapshot.value(EnvironmentMessages.REACT_VERSION));
    assertEquals(raw, Files.readString(polish));
  }

  @Test
  void countsOnlyTheMessageMissingFromTheEditedCatalog() throws Exception {
    Path french = directory.resolve("languages/fr_FR.toml");
    String raw = TomlLanguageEditor.remove(Files.readString(french), EnvironmentMessages.REACT_VERSION.id()).content();
    Files.writeString(french, raw);

    LocalizationSnapshot snapshot = ReactLanguage.editorOptions().loader().load("fr_FR");

    assertEquals(1, ReactLanguage.fallbackEntryCount(snapshot.validation()));
    assertEquals(EnvironmentMessages.REACT_VERSION.englishValue(), snapshot.value(EnvironmentMessages.REACT_VERSION));
    assertEquals(raw, Files.readString(french));
  }

  @Test
  void createsEnglishDuringInitialLoadWithNoLanguageDirectory() throws Exception {
    Files.delete(directory.resolve("languages/fr_FR.toml"));
    Files.delete(directory.resolve("languages"));

    ReactLanguage.PreparedReload prepared = ReactLanguage.prepareHotload(null, null, "en_US");

    assertEquals(EnvironmentMessages.REACT_VERSION.englishValue(), prepared.snapshot().value(EnvironmentMessages.REACT_VERSION));
    assertTrue(Files.isRegularFile(directory.resolve("languages/en_US.toml")));
  }

  @Test
  void createsCompleteEnglishWhilePreparingAnotherLocale() throws Exception {
    editor.load("fr_FR").get(5, TimeUnit.SECONDS);

    Path english = directory.resolve("languages/en_US.toml");
    String raw = Files.readString(english);
    LocaleOverlay overlay = ReactLanguage.parseOverlay(english.toString(), "en_US", raw);
    assertTrue(raw.startsWith("# React — en_US"));
    assertEquals(ReactMessages.catalog().byId().keySet(), overlay.values().keySet());
    assertEquals(EnvironmentMessages.REACT_VERSION.englishValue(), overlay.value(EnvironmentMessages.REACT_VERSION.id()));
    try (Stream<Path> children = Files.list(directory.resolve("languages"))) {
      assertEquals(Set.of("en_US.toml", "fr_FR.toml"),
          children.map(path -> path.getFileName().toString()).collect(Collectors.toSet()));
    }
  }

  @Test
  void keepsEnglishEditsAcrossPreparationAndSupportsDirectEditing() throws Exception {
    Path english = directory.resolve("languages/en_US.toml");
    String raw = "# Server wording\n\"" + EnvironmentMessages.REACT_VERSION.id() + "\" = \"Custom React {version}\"\n";
    Files.writeString(english, raw);

    PluginLanguageEditor.Document original = editor.load("en_US").get(5, TimeUnit.SECONDS);
    assertEquals(raw, Files.readString(english));
    assertEquals(new TextValue("Custom React {version}"), original.snapshot().value(EnvironmentMessages.REACT_VERSION));
    TextValue edited = new TextValue("Server React {version}");
    editor.save(new PluginLanguageEditor.Edit("en_US", EnvironmentMessages.REACT_VERSION.id(),
        original.snapshot().value(EnvironmentMessages.REACT_VERSION), edited)).get(5, TimeUnit.SECONDS);

    byte[] saved = Files.readAllBytes(english);
    assertEquals(edited, editor.load("en_US").get(5, TimeUnit.SECONDS).snapshot().value(EnvironmentMessages.REACT_VERSION));
    assertArrayEquals(saved, Files.readAllBytes(english));
    assertTrue(Files.readString(english).startsWith("# Server wording"));
  }

  @Test
  void preparesHotloadFromDirectLanguageFileWithoutReplacingDisk() throws Exception {
    Path french = directory.resolve("languages/fr_FR.toml");
    String raw = "\"" + EnvironmentMessages.REACT_VERSION.id() + "\" = \"Hotloaded React {version}\"\n";
    ReactLanguage.PreparedReload prepared = ReactLanguage.prepareHotload(french.toFile(), raw, "fr_FR");

    assertFalse(prepared.serverDefault());
    assertEquals(new TextValue("Hotloaded React {version}"), prepared.snapshot().value(EnvironmentMessages.REACT_VERSION));
    assertArrayEquals(frenchLocale, Files.readAllBytes(french));
    assertTrue(ReactLanguage.isLanguageFile(french.toFile()));
    assertFalse(ReactLanguage.isLanguageFile(directory.resolve("languages/nested/fr_FR.toml").toFile()));
    assertFalse(ReactLanguage.isLanguageFile(directory.resolve("languages/language-preferences.properties").toFile()));
  }

  @Test
  void hotloadsPersonalLocaleWithoutChangingTheServerOrReadingNewerDiskContent() throws Exception {
    UUID player = UUID.randomUUID();
    languages.selectPlayer(player, "fr_FR").get(5, TimeUnit.SECONDS);
    LocalizationSnapshot server = ReactLanguage.snapshot();
    Path french = directory.resolve("languages/fr_FR.toml");
    String captured = "\"" + EnvironmentMessages.REACT_VERSION.id() + "\" = \"Captured React {version}\"\n";
    ReactLanguage.PreparedReload prepared = ReactLanguage.prepareHotload(french.toFile(), captured, "en_US");
    Files.writeString(french, "\"" + EnvironmentMessages.REACT_VERSION.id() + "\" = \"Newer React {version}\"\n");

    assertFalse(prepared.serverDefault());
    assertTrue(ReactLanguage.applyPreparedHotload(prepared));
    assertSame(prepared.snapshot(), languages.snapshot(player));
    assertSame(server, ReactLanguage.snapshot());
    assertEquals("en_US", ReactLanguage.activeLocale());
    assertEquals("fr_FR", languages.playerLocale(player).orElseThrow());
  }

  @Test
  void invalidPersonalLanguageKeepsItsLastGoodSnapshotAcrossServerHotload() throws Exception {
    UUID player = UUID.randomUUID();
    languages.selectPlayer(player, "fr_FR").get(5, TimeUnit.SECONDS);
    LocalizationSnapshot french = languages.snapshot(player);
    Path file = directory.resolve("languages/fr_FR.toml");
    Files.writeString(file, "not valid TOML = [");

    assertThrows(Exception.class, () -> ReactLanguage.prepareHotload(file.toFile(), Files.readString(file), "en_US"));
    ReactLanguage.PreparedReload english = ReactLanguage.prepareHotload(null, null, "en_US");
    assertTrue(ReactLanguage.applyPreparedHotload(english));

    assertSame(french, languages.snapshot(player));
    assertEquals("fr_FR", languages.playerLocale(player).orElseThrow());
    assertSame(english.snapshot(), ReactLanguage.snapshot());
  }

  @Test
  void unrelatedMainConfigChangesKeepTheActiveLocaleWhenItsFileIsUnreadable() throws Exception {
    Path file = directory.resolve("languages/fr_FR.toml");
    ReactLanguage.PreparedReload initial = ReactLanguage.prepareHotload(null, null, "fr_FR");
    assertTrue(ReactLanguage.applyPreparedHotload(initial));
    Files.writeString(file, "not valid TOML = [");

    ReactLanguage.PreparedReload unchanged = ReactLanguage.prepareHotload(null, null, "fr_FR");
    assertSame(initial.snapshot(), unchanged.snapshot());
    assertTrue(ReactLanguage.applyPreparedHotload(unchanged));
    assertSame(initial.snapshot(), ReactLanguage.snapshot());
    assertEquals("fr_FR", ReactLanguage.activeLocale());

    ReactLanguage.PreparedReload english = ReactLanguage.prepareHotload(null, null, "en_US");
    assertTrue(ReactLanguage.applyPreparedHotload(english));
    assertThrows(IllegalArgumentException.class, () -> ReactLanguage.prepareHotload(null, null, "fr_FR"));
    assertSame(english.snapshot(), ReactLanguage.snapshot());
    assertEquals("en_US", ReactLanguage.activeLocale());
  }

  @Test
  void unchangedMainConfigPreparationDoesNotUndoANewerLanguageEdit() throws Exception {
    Path file = directory.resolve("languages/en_US.toml");
    ReactLanguage.PreparedReload mainConfig = ReactLanguage.prepareHotload(null, null, "en_US");
    String raw = "\"" + EnvironmentMessages.REACT_VERSION.id() + "\" = \"Newer English {version}\"\n";
    ReactLanguage.PreparedReload languageEdit = ReactLanguage.prepareHotload(file.toFile(), raw, "en_US");

    assertTrue(ReactLanguage.applyPreparedHotload(languageEdit));
    assertTrue(ReactLanguage.applyPreparedHotload(mainConfig));

    assertSame(languageEdit.snapshot(), ReactLanguage.snapshot());
    assertEquals("en_US", ReactLanguage.activeLocale());
  }

  @Test
  void preparedLocaleFileEditDoesNotUndoANewerServerLanguageSelection() throws Exception {
    Path file = directory.resolve("languages/en_US.toml");
    String raw = "\"" + EnvironmentMessages.REACT_VERSION.id() + "\" = \"Edited English {version}\"\n";
    ReactLanguage.PreparedReload fileEdit = ReactLanguage.prepareHotload(file.toFile(), raw, "en_US");
    ReactLanguage.PreparedReload french = ReactLanguage.prepareHotload(null, null, "fr_FR");

    assertTrue(ReactLanguage.applyPreparedHotload(french));
    assertTrue(ReactLanguage.applyPreparedHotload(fileEdit));

    assertSame(french.snapshot(), ReactLanguage.snapshot());
    assertEquals("fr_FR", ReactLanguage.activeLocale());
  }

  @Test
  void hotloadsExplicitEnglishPreferenceAlongsideTheServerDefault() throws Exception {
    UUID player = UUID.randomUUID();
    languages.selectPlayer(player, "en_US").get(5, TimeUnit.SECONDS);
    Path english = directory.resolve("languages/en_US.toml");
    String raw = "\"" + EnvironmentMessages.REACT_VERSION.id() + "\" = \"Edited English {version}\"\n";
    ReactLanguage.PreparedReload prepared = ReactLanguage.prepareHotload(english.toFile(), raw, "en_US");

    assertTrue(ReactLanguage.applyPreparedHotload(prepared));

    assertSame(prepared.snapshot(), ReactLanguage.snapshot());
    assertSame(prepared.snapshot(), languages.snapshot(player));
  }
}
