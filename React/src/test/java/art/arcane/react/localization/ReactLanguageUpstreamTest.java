package art.arcane.react.localization;

import art.arcane.react.React;
import art.arcane.react.localization.catalog.CommandMessages;
import art.arcane.react.localization.catalog.EnvironmentMessages;
import art.arcane.volmlib.util.localization.LanguageMeta;
import art.arcane.volmlib.util.localization.LanguageUpstreamSync;
import art.arcane.volmlib.util.localization.LocalizationCandidate;
import art.arcane.volmlib.util.localization.LocalizationSnapshot;
import art.arcane.volmlib.util.localization.PluginLanguageService;
import art.arcane.volmlib.util.localization.PluralSelector;
import art.arcane.volmlib.util.localization.TextValue;
import art.arcane.volmlib.util.localization.TomlLanguageEditor;
import art.arcane.volmlib.util.localization.TomlLanguageParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReactLanguageUpstreamTest {
  private static final String REVISION_ONE = "1111111111111111111111111111111111111111";
  private static final String REVISION_TWO = "2222222222222222222222222222222222222222";

  @TempDir
  Path directory;

  private React previousPlugin;
  private String previousActiveLocale;
  private PluginLanguageService previousLanguages;
  private PluginLanguageService languages;
  private HttpServer server;
  private final AtomicReference<String> revision = new AtomicReference<>(REVISION_ONE);
  private final Map<String, String> files = new ConcurrentHashMap<>();

  @BeforeEach
  void prepareRepository() throws Exception {
    previousPlugin = React.instance;
    previousActiveLocale = ReactLanguage.activeLocale();
    previousLanguages = (PluginLanguageService) field("languageService").get(null);
    field("activeLocale").set(null, "en_US");
    React plugin = mock(React.class);
    when(plugin.getDataFolder()).thenReturn(directory.toFile());
    when(plugin.getLogger()).thenReturn(Logger.getLogger("ReactLanguageUpstreamTest"));
    React.instance = plugin;
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/api/", this::api);
    server.createContext("/raw/", this::raw);
    server.start();
    URI root = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/");
    Method open = ReactLanguage.class.getDeclaredMethod("openUpstream", URI.class, URI.class, Duration.class);
    open.setAccessible(true);
    open.invoke(null, root.resolve("api/"), root.resolve("raw/"), Duration.ZERO);
  }

  @AfterEach
  void closeRepository() throws Exception {
    if (languages != null) {
      languages.close();
    }
    Method close = ReactLanguage.class.getDeclaredMethod("closeUpstream");
    close.setAccessible(true);
    close.invoke(null);
    server.stop(0);
    field("languageService").set(null, previousLanguages);
    field("activeLocale").set(null, previousActiveLocale);
    ReactLanguage.reloadCandidate(LocalizationCandidate.english(ReactMessages.catalog(), PluralSelector.oneOther()));
    React.instance = previousPlugin;
  }

  @Test
  void updateMergesTheBranchHeadWhileKeepingManualEditsAndCustomFiles() throws Exception {
    files.put(REVISION_ONE + "/fr_FR", "# fr v1\n\n[environment]\nreact_version = \"Version React {version}\"\n");
    languages = selections();
    UUID player = UUID.randomUUID();

    languages.selectPlayer(player, "fr_FR").get(30, TimeUnit.SECONDS);

    Path french = directory.resolve("languages/fr_FR.toml");
    assertTrue(Files.isRegularFile(french));
    assertTrue(Files.isRegularFile(directory.resolve("languages/.upstream/fr_FR.toml")));
    assertTrue(Files.isRegularFile(directory.resolve("languages/.upstream/upstream.properties")));
    assertEquals(new TextValue("Version React {version}"), languages.snapshot(player).value(EnvironmentMessages.REACT_VERSION));

    Files.writeString(french, TomlLanguageEditor.upsertText(Files.readString(french),
        EnvironmentMessages.REACT_VERSION.id(), "Ma version React {version}").content());
    Path pirate = directory.resolve("languages/pirate.toml");
    String pirateSource = "[meta]\nname = \"Pirate Speak\"\nbase = \"fr_FR\"\n";
    Files.writeString(pirate, pirateSource);
    revision.set(REVISION_TWO);
    files.put(REVISION_TWO + "/fr_FR", "# fr v2\n\n[environment]\nreact_version = \"Version React v2 {version}\"\n\n"
        + "[command.description]\nmonitor = \"Surveiller le serveur\"\n");

    LanguageUpstreamSync.SyncReport report = ReactLanguage.updateAction().update().get(30, TimeUnit.SECONDS);

    assertTrue(report.failure().isEmpty());
    assertEquals(List.of("fr_FR"), report.changedLocales());
    assertTrue(report.merged().get("fr_FR").kept().contains(EnvironmentMessages.REACT_VERSION.id()));
    assertTrue(report.merged().get("fr_FR").added().contains(CommandMessages.MONITOR_DESCRIPTION.id()));
    String merged = Files.readString(french);
    assertTrue(merged.startsWith("# fr v2\n"));
    assertTrue(merged.contains("Ma version React {version}"));
    assertTrue(merged.contains("Surveiller le serveur"));
    assertFalse(merged.contains("Version React v2"));
    assertTrue(Files.readString(directory.resolve("languages/.upstream/fr_FR.toml")).contains("Version React v2"));
    assertEquals(pirateSource, Files.readString(pirate));
    assertEquals(new TextValue("Surveiller le serveur"), languages.snapshot(player).value(CommandMessages.MONITOR_DESCRIPTION));
    assertEquals(REVISION_TWO, ReactLanguage.upstreamStatus().orElseThrow().revision().orElseThrow());

    LanguageUpstreamSync.SyncReport unchanged = ReactLanguage.updateAction().update().get(30, TimeUnit.SECONDS);

    assertTrue(unchanged.unchanged());
    assertTrue(unchanged.changedLocales().isEmpty());
    assertEquals(merged, Files.readString(french));
  }

  @Test
  void customLocaleMetaProvidesItsNameAndResolvesItsBaseBeforeEnglish() throws Exception {
    Files.createDirectories(directory.resolve("languages"));
    Files.writeString(directory.resolve("languages/fr_FR.toml"),
        "[environment]\nreact_version = \"Version React {version}\"\n\n[command.description]\nmonitor = \"Surveiller\"\n");
    Files.writeString(directory.resolve("languages/pirate.toml"), "[meta]\nname = \"Pirate Speak\"\nbase = \"fr_FR\"\n\n"
        + "[environment]\nreact_version = \"Arr, React {version}\"\n");

    LocalizationSnapshot pirate = ReactLanguage.editorOptions().loader().load("pirate");

    assertEquals(new TextValue("Arr, React {version}"), pirate.value(EnvironmentMessages.REACT_VERSION));
    assertEquals(new TextValue("Surveiller"), pirate.value(CommandMessages.MONITOR_DESCRIPTION));
    assertEquals(EnvironmentMessages.SERVER_TYPE.englishValue(), pirate.value(EnvironmentMessages.SERVER_TYPE));
    assertTrue(ReactLanguage.availableLocales().contains("pirate"));
    assertTrue(ReactLanguage.availableLocales().contains("de_DE"));
    assertEquals("Pirate Speak", ReactLanguage.localeDisplayName("pirate"));
    assertEquals("Pirate Speak", ReactLanguage.displayNames().displayName("pirate"));
    assertNull(ReactLanguage.displayNames().displayName("fr_FR"));
    assertEquals("French (France)", ReactLanguage.localeDisplayName("fr_FR"));
    assertEquals("klingon", ReactLanguage.localeDisplayName("klingon"));

    ReactLanguage.editorOptions().loader().load("klingon");
    LanguageMeta generated = LanguageMeta.parse(Files.readString(directory.resolve("languages/klingon.toml")));

    assertEquals("klingon", generated.name().orElseThrow());
    assertEquals("en_US", generated.base().orElseThrow());
  }

  @Test
  void englishReferenceGainsNewCatalogKeysWhileKeepingEdits() throws Exception {
    ReactLanguage.prepareHotload(null, null, "en_US");
    Path english = directory.resolve("languages/en_US.toml");
    Path sidecar = directory.resolve("languages/.upstream/en_US.toml");
    String older = TomlLanguageEditor.remove(Files.readString(sidecar), CommandMessages.MONITOR_DESCRIPTION.id()).content();
    Files.writeString(sidecar, older);
    Files.writeString(english, TomlLanguageEditor.upsertText(older, EnvironmentMessages.REACT_VERSION.id(),
        "&bServer React {version}&r").content());

    ReactLanguage.PreparedReload prepared = ReactLanguage.prepareHotload(null, null, "en_US");

    Map<String, String> values = TomlLanguageParser.parseText(Files.readString(english));
    assertEquals(CommandMessages.MONITOR_DESCRIPTION.englishValue(),
        new TextValue(values.get(CommandMessages.MONITOR_DESCRIPTION.id())));
    assertEquals("&bServer React {version}&r", values.get(EnvironmentMessages.REACT_VERSION.id()));
    assertEquals(new TextValue("&bServer React {version}&r"), prepared.snapshot().value(EnvironmentMessages.REACT_VERSION));
    assertEquals(ReactLanguageReference.englishCatalog(), Files.readString(sidecar));
  }

  private PluginLanguageService selections() throws Exception {
    LocalizationSnapshot english = LocalizationSnapshot.create(
        LocalizationCandidate.english(ReactMessages.catalog(), PluralSelector.oneOther()));
    PluginLanguageService created = new PluginLanguageService(new PluginLanguageService.Options(
        directory.resolve("players.properties"), ReactLanguage::availableLocales, () -> "en_US", () -> english,
        ReactLanguage.editorOptions().loader()::load, (locale, snapshot) -> {
          throw new AssertionError("A personal selection must not change the server language");
        }, Logger.getLogger("ReactLanguageUpstreamTest")));
    field("languageService").set(null, created);
    return created;
  }

  private void api(HttpExchange exchange) throws IOException {
    String etag = "\"" + revision.get() + "\"";
    if (etag.equals(exchange.getRequestHeaders().getFirst("If-None-Match"))) {
      exchange.sendResponseHeaders(304, -1);
      exchange.close();
      return;
    }
    exchange.getResponseHeaders().set("ETag", etag);
    byte[] response = ("[{\"sha\":\"" + revision.get() + "\"}]").getBytes(StandardCharsets.UTF_8);
    exchange.sendResponseHeaders(200, response.length);
    exchange.getResponseBody().write(response);
    exchange.close();
  }

  private void raw(HttpExchange exchange) throws IOException {
    String[] parts = exchange.getRequestURI().getPath().split("/");
    String content = files.get(parts[4] + "/" + parts[parts.length - 1].replace(".toml", ""));
    if (content == null) {
      exchange.sendResponseHeaders(404, -1);
      exchange.close();
      return;
    }
    byte[] response = content.getBytes(StandardCharsets.UTF_8);
    exchange.sendResponseHeaders(200, response.length);
    exchange.getResponseBody().write(response);
    exchange.close();
  }

  private static Field field(String name) throws ReflectiveOperationException {
    Field field = ReactLanguage.class.getDeclaredField(name);
    field.setAccessible(true);
    return field;
  }
}
