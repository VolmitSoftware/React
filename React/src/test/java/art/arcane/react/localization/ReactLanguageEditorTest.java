package art.arcane.react.localization;

import art.arcane.react.React;
import art.arcane.react.localization.catalog.CommandMessages;
import art.arcane.volmlib.util.localization.LocalizationCandidate;
import art.arcane.volmlib.util.localization.LocalizationSnapshot;
import art.arcane.volmlib.util.localization.PluginLanguageEditor;
import art.arcane.volmlib.util.localization.PluginLanguageService;
import art.arcane.volmlib.util.localization.PluralSelector;
import art.arcane.volmlib.util.localization.RemoteLanguageCatalog;
import art.arcane.volmlib.util.localization.TextValue;
import art.arcane.volmlib.util.localization.VolmitLocales;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReactLanguageEditorTest {
  @TempDir
  Path directory;

  private React previous;
  private RemoteLanguageCatalog previousRemote;
  private RemoteLanguageCatalog remote;
  private PluginLanguageService languages;
  private PluginLanguageEditor editor;

  @BeforeEach
  void prepareEditor() throws Exception {
    previous = React.instance;
    React plugin = mock(React.class);
    when(plugin.getDataFolder()).thenReturn(directory.toFile());
    React.instance = plugin;
    Field field = ReactLanguage.class.getDeclaredField("remoteCatalog");
    field.setAccessible(true);
    previousRemote = (RemoteLanguageCatalog) field.get(null);
    remote = RemoteLanguageCatalog.load(new RemoteLanguageCatalog.Options(
        "React", URI.create("https://raw.githubusercontent.com/VolmitSoftware/React/"),
        "React/src/main/resources/languages", ".toml", "language-source.properties",
        directory.resolve("languages/cache"), ReactLanguage.class.getClassLoader()));
    field.set(null, remote);
    Files.createDirectories(directory.resolve("languages"));
    Files.copy(Path.of("src/main/resources/languages/fr_FR.toml"), directory.resolve("languages/fr_FR.toml"));
    PluginLanguageEditor.Options options = ReactLanguage.editorOptions();
    LocalizationSnapshot english = LocalizationSnapshot.create(
        LocalizationCandidate.english(ReactMessages.catalog(), PluralSelector.oneOther()));
    languages = new PluginLanguageService(new PluginLanguageService.Options(
        directory.resolve("players.properties"), VolmitLocales::all, () -> "en_US", () -> english,
        options.loader()::load, (locale, snapshot) -> {
          throw new AssertionError("Editing must not select a server language");
        }, Logger.getLogger("ReactLanguageEditorTest")));
    editor = new PluginLanguageEditor(languages, options);
  }

  @AfterEach
  void closeEditor() throws Exception {
    editor.close();
    languages.close();
    remote.close();
    Field field = ReactLanguage.class.getDeclaredField("remoteCatalog");
    field.setAccessible(true);
    field.set(null, previousRemote);
    React.instance = previous;
  }

  @Test
  void savesOneLocaleAndRefreshesItsPersonalSnapshotWithoutSelectingIt() throws Exception {
    UUID player = UUID.randomUUID();
    languages.selectPlayer(player, "fr_FR").get(5, TimeUnit.SECONDS);
    String active = ReactLanguage.activeLocale();
    PluginLanguageEditor.Document original = editor.load("fr_FR").get(5, TimeUnit.SECONDS);
    TextValue value = new TextValue("React version {version}");
    editor.save(new PluginLanguageEditor.Edit("fr_FR", CommandMessages.VERSION.id(),
        original.snapshot().value(CommandMessages.VERSION), value)).get(5, TimeUnit.SECONDS);

    assertTrue(Files.readString(directory.resolve("languages/overrides/fr_FR.toml")).contains("React version {version}"));
    assertEquals(value, ReactLanguage.editorOptions().loader().load("fr_FR").value(CommandMessages.VERSION));
    assertEquals(value, languages.snapshot(player).value(CommandMessages.VERSION));
    assertEquals("fr_FR", languages.playerLocale(player).orElseThrow());
    assertEquals("en_US", languages.defaultLocale());
    assertEquals(active, ReactLanguage.activeLocale());
    assertFalse(Files.exists(directory.resolve("languages/overrides/en_US.toml")));
  }

  @Test
  void invalidMessageLeavesTheLocaleFileIntact() throws Exception {
    PluginLanguageEditor.Document original = editor.load("fr_FR").get(5, TimeUnit.SECONDS);
    editor.save(new PluginLanguageEditor.Edit("fr_FR", CommandMessages.VERSION.id(),
        original.snapshot().value(CommandMessages.VERSION), new TextValue("React version {version}")))
        .get(5, TimeUnit.SECONDS);
    Path file = directory.resolve("languages/overrides/fr_FR.toml");
    byte[] before = Files.readAllBytes(file);
    PluginLanguageEditor.Document saved = editor.load("fr_FR").get(5, TimeUnit.SECONDS);

    assertThrows(ExecutionException.class, () -> editor.save(new PluginLanguageEditor.Edit("fr_FR",
        CommandMessages.VERSION.id(), saved.snapshot().value(CommandMessages.VERSION),
        new TextValue("Missing placeholder"))).get(5, TimeUnit.SECONDS));
    assertArrayEquals(before, Files.readAllBytes(file));
  }

  @Test
  void incompleteDownloadedLocaleCanBeEditedWithoutBeingSelected() throws Exception {
    Files.writeString(directory.resolve("languages/fr_FR.toml"), "");
    PluginLanguageEditor.Document original = editor.load("fr_FR").get(5, TimeUnit.SECONDS);
    TextValue value = new TextValue("React version {version}");
    PluginLanguageEditor.Document saved = editor.save(new PluginLanguageEditor.Edit("fr_FR", CommandMessages.VERSION.id(),
        original.snapshot().value(CommandMessages.VERSION), value)).get(5, TimeUnit.SECONDS);
    assertEquals(value, saved.snapshot().value(CommandMessages.VERSION));
    assertEquals("en_US", languages.defaultLocale());
  }
}
