package art.arcane.react.core.controller;

import art.arcane.react.React;
import art.arcane.react.localization.ReactLanguage;
import art.arcane.react.localization.ReactMessages;
import art.arcane.react.localization.catalog.CommandMessages;
import art.arcane.react.model.ReactConfiguration;
import art.arcane.react.util.common.scheduling.Ticker;
import art.arcane.react.util.project.config.ConfigFileSupport;
import art.arcane.react.util.project.config.ConfigHotloadSnapshot;
import art.arcane.volmlib.util.hotload.ConfigHotloadEngine;
import art.arcane.volmlib.util.localization.LocalizationCandidate;
import art.arcane.volmlib.util.localization.LocalizationManager;
import art.arcane.volmlib.util.localization.LocalizationSnapshot;
import art.arcane.volmlib.util.localization.PluginLanguageEditor;
import art.arcane.volmlib.util.localization.PluginLanguageService;
import art.arcane.volmlib.util.localization.PluralSelector;
import art.arcane.volmlib.util.localization.RemoteLanguageCatalog;
import art.arcane.volmlib.util.localization.TextValue;
import art.arcane.volmlib.util.localization.TomlLanguageEditor;
import art.arcane.volmlib.util.localization.VolmitLocales;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LanguageInstallHotloadTest {
  private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

  @TempDir
  Path directory;

  private React previousPlugin;
  private RemoteLanguageCatalog previousRemote;
  private String previousActiveLocale;
  private HotloadController controller;
  private ConfigHotloadEngine engine;
  private HotloadPendingQueue queue;
  private Map<String, String> appliedContents;
  private Object runtime;
  private PluginLanguageService languages;
  private PluginLanguageEditor editor;
  private String germanSource;

  @BeforeEach
  void prepareLanguageInstallAndHotloadQueue() throws Exception {
    previousPlugin = React.instance;
    previousRemote = (RemoteLanguageCatalog) field(ReactLanguage.class, "remoteCatalog").get(null);
    previousActiveLocale = ReactLanguage.activeLocale();
    field(ReactLanguage.class, "activeLocale").set(null, "en_US");
    germanSource = Files.readString(Path.of("src/main/resources/languages/de_DE.toml"), StandardCharsets.UTF_8);
    Files.createDirectories(directory.resolve("languages"));
    React plugin = mock(React.class);
    when(plugin.getDataFolder()).thenReturn(directory.toFile());
    when(plugin.getTicker()).thenReturn(mock(Ticker.class));
    React.instance = plugin;

    RemoteLanguageCatalog remote = mock(RemoteLanguageCatalog.class);
    when(remote.readOrInstall(eq("de_DE"), any(Path.class), any(RemoteLanguageCatalog.ContentValidator.class)))
        .thenAnswer(invocation -> {
          Path destination = invocation.getArgument(1);
          RemoteLanguageCatalog.ContentValidator validator = invocation.getArgument(2);
          validator.validate("de_DE", germanSource);
          Files.writeString(destination, germanSource, StandardCharsets.UTF_8);
          return germanSource;
        });
    field(ReactLanguage.class, "remoteCatalog").set(null, remote);

    controller = new HotloadController();
    field(HotloadController.class, "dataFolder").set(controller, directory.toFile());
    field(HotloadController.class, "reactToml").set(controller, directory.resolve("react.toml").toFile());
    field(HotloadController.class, "localeFolder").set(controller, directory.resolve("languages").toFile());
    engine = (ConfigHotloadEngine) invoke(controller, "createHotloadEngine");
    engine.configure(100L, 100L, List.of(), List.of(directory.resolve("languages").toFile()));
    queue = new HotloadPendingQueue(0L, 0L, System::nanoTime);
    appliedContents = new ConcurrentHashMap<>();
    runtime = constructNested("HotloadRuntime", engine, queue, new HotloadRevisionTracker(), appliedContents,
        new ConcurrentHashMap<String, String>(), new ConcurrentLinkedQueue<>(), new AtomicBoolean(),
        mock(HotloadTaskExecutor.class));
    field(HotloadController.class, "hotloadRuntime").set(controller, runtime);
    ConfigFileSupport.setSelfWriteListener((file, content) -> invoke(controller, "noteSelfWrite", file, content));

    LocalizationSnapshot english = LocalizationSnapshot.create(
        LocalizationCandidate.english(ReactMessages.catalog(), PluralSelector.oneOther()));
    PluginLanguageEditor.Options options = ReactLanguage.editorOptions();
    languages = new PluginLanguageService(new PluginLanguageService.Options(
        directory.resolve("players.properties"), VolmitLocales::all, () -> "en_US", () -> english,
        options.loader()::load, (locale, snapshot) -> {
          throw new AssertionError("A personal selection must not change the server language");
        }, Logger.getLogger("LanguageInstallHotloadTest")));
    editor = new PluginLanguageEditor(languages, options);
  }

  @AfterEach
  void restoreLanguageAndHotloadState() throws Exception {
    ConfigFileSupport.setSelfWriteListener(null);
    if (editor != null) {
      editor.close();
    }
    if (languages != null) {
      languages.close();
    }
    if (engine != null) {
      engine.clear();
    }
    field(ReactLanguage.class, "remoteCatalog").set(null, previousRemote);
    field(ReactLanguage.class, "activeLocale").set(null, previousActiveLocale);
    React.instance = previousPlugin;
  }

  @Test
  void startupEnglishCreationDoesNotPrepareOperatorDiffs() throws Exception {
    ReactLanguage.PreparedReload prepared = ReactLanguage.prepareHotload(null, null, "en_US");

    Path english = languageFile("en_US");
    assertEquals(CommandMessages.VERSION.englishValue(), prepared.snapshot().value(CommandMessages.VERSION));
    assertTrue(Files.isRegularFile(english));
    assertTrue(prepareChanges(english).isEmpty(),
        "Creating the startup English reference must not look like a manual catalog edit");
    assertEquals(ConfigFileSupport.normalize(Files.readString(english)), appliedContents.get(english.toString()));
  }

  @Test
  void languageSelectionAcknowledgesInstalledCatalogsBeforeDelayedWatcherEvents() throws Exception {
    UUID player = UUID.randomUUID();

    languages.selectPlayer(player, "de_DE").get(5, TimeUnit.SECONDS);

    Path german = languageFile("de_DE");
    Path english = languageFile("en_US");
    assertEquals("de_DE", languages.playerLocale(player).orElseThrow());
    assertEquals(germanSource, Files.readString(german));
    assertTrue(Files.isRegularFile(english));
    assertTrue(prepareChanges(english, german).isEmpty(),
        "Installing catalogs must not prepare <missing>-to-template operator diffs");
    assertEquals(ConfigFileSupport.normalize(germanSource), appliedContents.get(german.toString()));
    assertEquals(ConfigFileSupport.normalize(Files.readString(english)), appliedContents.get(english.toString()));
  }

  @Test
  void serverLanguageSelectionAcknowledgesItsMainConfigAndInstalledCatalogs() throws Exception {
    Path mainConfig = directory.resolve("react.toml").toAbsolutePath().normalize();
    Files.writeString(mainConfig, "language = \"en_US\"\n");
    when(React.instance.getDataFile("react.toml")).thenReturn(mainConfig.toFile());
    invoke(controller, "primeAppliedContents", runtime);
    Field configurationField = field(ReactConfiguration.class, "configuration");
    ReactConfiguration previousConfiguration = (ReactConfiguration) configurationField.get(null);
    LocalizationManager manager = (LocalizationManager) field(ReactLanguage.class, "MANAGER").get(null);
    LocalizationSnapshot previousSnapshot = manager.snapshot();
    Method selectDefault = ReactLanguage.class.getDeclaredMethod("selectDefault", String.class, LocalizationSnapshot.class);
    selectDefault.setAccessible(true);
    ReactConfiguration.applyHotloadSnapshot(new ReactConfiguration());
    try (PluginLanguageService serverLanguages = new PluginLanguageService(new PluginLanguageService.Options(
        directory.resolve("server-players.properties"), VolmitLocales::all,
        () -> ReactConfiguration.get().getLanguage(), manager::snapshot, ReactLanguage.editorOptions().loader()::load,
        (locale, snapshot) -> selectDefault.invoke(null, locale, snapshot),
        Logger.getLogger("ServerLanguageInstallHotloadTest")))) {
      serverLanguages.selectDefault("de_DE").get(5, TimeUnit.SECONDS);

      assertEquals("de_DE", serverLanguages.defaultLocale());
      assertEquals("de_DE", ReactConfiguration.get().getLanguage());
      assertTrue(prepareChanges(mainConfig, languageFile("en_US"), languageFile("de_DE")).isEmpty(),
          "The server language command already applies its config and must not trigger duplicate hotload notices");
      assertEquals(ConfigFileSupport.normalize(Files.readString(mainConfig)), appliedContents.get(mainConfig.toString()));
    } finally {
      manager.install(previousSnapshot);
      configurationField.set(null, previousConfiguration);
    }
  }

  @Test
  void manualEditBeforeInstallAcknowledgementIsDrainedStillPreparesOneChange() throws Exception {
    languages.selectPlayer(UUID.randomUUID(), "de_DE").get(5, TimeUnit.SECONDS);
    Path german = languageFile("de_DE");
    String edited = editVersion(german, "Angepasste React-Version {version}");

    List<Object> changes = prepareChanges(languageFile("en_US"), german);

    assertManualVersionChange(changes, german, edited);
  }

  @Test
  void loadingAnExistingLocaleDoesNotHideAnUnprocessedManualEdit() throws Exception {
    languages.selectPlayer(UUID.randomUUID(), "de_DE").get(5, TimeUnit.SECONDS);
    Path german = languageFile("de_DE");
    assertTrue(prepareChanges(languageFile("en_US"), german).isEmpty());
    String edited = editVersion(german, "Meine React-Version {version}");

    LocalizationSnapshot loaded = ReactLanguage.editorOptions().loader().load("de_DE");

    assertEquals(new TextValue("Meine React-Version {version}"), loaded.value(CommandMessages.VERSION));
    assertManualVersionChange(prepareChanges(german), german, edited);
  }

  @Test
  void savingInTheLanguageEditorDoesNotPrepareDuplicateOperatorDiffs() throws Exception {
    languages.selectPlayer(UUID.randomUUID(), "de_DE").get(5, TimeUnit.SECONDS);
    Path german = languageFile("de_DE");
    assertTrue(prepareChanges(languageFile("en_US"), german).isEmpty());
    PluginLanguageEditor.Document original = editor.load("de_DE").get(5, TimeUnit.SECONDS);
    TextValue updated = new TextValue("Bearbeitete React-Version {version}");

    editor.save(new PluginLanguageEditor.Edit("de_DE", CommandMessages.VERSION.id(),
        original.snapshot().value(CommandMessages.VERSION), updated)).get(5, TimeUnit.SECONDS);

    assertEquals(updated, editor.load("de_DE").get(5, TimeUnit.SECONDS).snapshot().value(CommandMessages.VERSION));
    assertTrue(prepareChanges(german).isEmpty(),
        "The editor already confirms its own save; watcher events must not repeat the template diff");
  }

  private Path languageFile(String locale) {
    return directory.resolve("languages").resolve(locale + ".toml").toAbsolutePath().normalize();
  }

  private String editVersion(Path file, String value) throws Exception {
    String edited = TomlLanguageEditor.upsert(Files.readString(file), CommandMessages.VERSION.id(),
        new TextValue(value)).content();
    Files.writeString(file, edited);
    return edited;
  }

  private List<Object> prepareChanges(Path... files) throws Exception {
    invoke(controller, "drainSelfWriteNotices", runtime);
    Set<File> touched = new LinkedHashSet<>();
    for (Path file : files) {
      touched.add(file.toFile());
    }
    invoke(controller, "enqueueTouchedFiles", runtime, touched);
    Object context = constructNested("PreparationContext", "de_DE");
    List<Object> prepared = new ArrayList<>();
    try {
      for (HotloadPendingQueue.ReadyChange change : queue.beginDrain()) {
        Object candidate = invoke(controller, "prepareQueuedChange", runtime, context,
            change.path().toFile(), change.present());
        if (candidate != null) {
          prepared.add(candidate);
        }
      }
    } finally {
      queue.finishDrain();
    }
    return prepared;
  }

  private void assertManualVersionChange(List<Object> changes, Path file, String edited) {
    assertEquals(1, changes.size(), "Only the manually edited message must produce an operator update");
    Object change = changes.getFirst();
    ConfigHotloadSnapshot snapshot = (ConfigHotloadSnapshot) invoke(change, "snapshot");
    assertEquals(file, snapshot.path());
    assertEquals(edited, snapshot.rawContent());
    List<?> notifications = (List<?>) invoke(change, "notifications");
    assertEquals(1, notifications.size());
    String notification = PLAIN.serialize((Component) notifications.getFirst());
    assertTrue(notification.replace('\\', '/').contains("languages/de_DE.toml"));
    assertTrue(notification.contains(CommandMessages.VERSION.id()));
    assertFalse(notification.contains("<missing>"));
  }

  private static Field field(Class<?> owner, String name) throws ReflectiveOperationException {
    Field field = owner.getDeclaredField(name);
    field.setAccessible(true);
    return field;
  }

  private static Object constructNested(String name, Object... arguments) throws ReflectiveOperationException {
    Class<?> type = Class.forName(HotloadController.class.getName() + "$" + name);
    Constructor<?> constructor = type.getDeclaredConstructors()[0];
    constructor.setAccessible(true);
    return constructor.newInstance(arguments);
  }

  private static Object invoke(Object target, String name, Object... arguments) {
    for (Method method : target.getClass().getDeclaredMethods()) {
      if (method.getName().equals(name) && method.getParameterCount() == arguments.length) {
        try {
          method.setAccessible(true);
          return method.invoke(target, arguments);
        } catch (InvocationTargetException failure) {
          throw new AssertionError(name + " failed", failure.getCause());
        } catch (ReflectiveOperationException failure) {
          throw new AssertionError("Could not invoke " + name, failure);
        }
      }
    }
    throw new AssertionError("Method not found: " + name);
  }
}
