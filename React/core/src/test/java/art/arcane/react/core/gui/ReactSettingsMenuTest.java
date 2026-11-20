package art.arcane.react.core.gui;

import art.arcane.react.core.controller.SampleController;
import art.arcane.react.platform.SettingsAction;
import art.arcane.react.platform.SettingsClick;
import art.arcane.react.platform.SettingsScreen;
import art.arcane.react.platform.SettingsScreenHost;
import art.arcane.react.platform.SettingsTile;
import art.arcane.react.util.project.config.TomlCodec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReactSettingsMenuTest {
  enum Mode {
    QUIET,
    NORMAL,
    LOUD
  }

  static final class Display {
    private boolean actionBar = true;
  }

  static final class Main {
    private String language = "en_US";
    private boolean verbose = true;
    private int interval = 5;
    private Mode mode = Mode.NORMAL;
    private Display display = new Display();
    private List<String> names = new ArrayList<>(List.of("a", "b"));
  }

  static final class MapCore {
    private int refreshTicks = 20;
  }

  @TempDir
  Path folder;

  private final Main main = new Main();
  private final AtomicInteger mainReloads = new AtomicInteger();
  private final List<String> writes = new ArrayList<>();

  private final SettingsSource source = new SettingsSource() {
    @Override
    public boolean canConfigure(UUID viewerId) {
      return true;
    }

    @Override
    public Object mainSettings() {
      return main;
    }

    @Override
    public File mainFile() {
      return folder.resolve("react.toml").toFile();
    }

    @Override
    public boolean reloadMain() {
      mainReloads.incrementAndGet();
      try {
        Main loaded = TomlCodec.fromToml(Files.readString(mainFile().toPath(), StandardCharsets.UTF_8), Main.class);
        main.verbose = loaded.verbose;
        main.interval = loaded.interval;
        main.mode = loaded.mode;
        return true;
      } catch (IOException failure) {
        return false;
      }
    }

    @Override
    public List<CoreSection> coreSections() {
      return List.of(new CoreSection("map", "Map", "filled_map", new MapCore()));
    }

    @Override
    public boolean reloadCore(String id) {
      return true;
    }

    @Override
    public File dataFile(String category, String fileName) {
      return folder.resolve(category).resolve(fileName).toFile();
    }

    @Override
    public SampleController samples() {
      return null;
    }

    @Override
    public void writeConfig(File file, String content) throws IOException {
      writes.add(file.getName());
      Files.writeString(file.toPath(), content, StandardCharsets.UTF_8);
    }

    @Override
    public void openServerLanguage(UUID viewerId) {
    }

    @Override
    public Optional<SettingsInputSessions> inputSessions() {
      return Optional.empty();
    }
  };

  private final ReactSettingsMenu menu = new ReactSettingsMenu(source, new SettingsScreenHost() {
    @Override
    public void open(UUID viewerId, SettingsScreen screen, ClickHandler handler) {
    }

    @Override
    public void close(UUID viewerId) {
    }

    @Override
    public void playPageTurn(UUID viewerId) {
    }
  });

  @Test
  void rootPlacesProfilesAndNavigationLikeBukkit() {
    SettingsScreen root = menu.screen("", 0).orElseThrow();

    assertEquals("Configure React", root.title());
    assertEquals(6, root.rows());
    assertEquals(ReactSettingsMenu.BACKGROUND, root.background());
    SettingsTile empty = tile(root, "cfg-empty");
    assertEquals(4, empty.slot());
    assertEquals("paper", empty.icon());
    SettingsTile off = tile(root, "cfg-preset-off");
    assertEquals(3 * 9 + 1, off.slot());
    assertEquals("gray_dye", off.icon());
    assertEquals(new SettingsAction.Preset("off"), off.actionFor(SettingsClick.LEFT));
    assertEquals(3 * 9 + 7, tile(root, "cfg-preset-high").slot());
    SettingsTile mainTile = tile(root, "cfg-root-main");
    assertEquals(5 * 9 + 3, mainTile.slot());
    assertEquals(new SettingsAction.Open("main", 0), mainTile.actionFor(SettingsClick.LEFT));
    assertEquals(new SettingsAction.Open("advanced", 0), tile(root, "cfg-root-advanced").actionFor(SettingsClick.LEFT));
  }

  @Test
  void mainSectionListsSectionsFirstThenValuesWithTheirClicks() {
    SettingsScreen screen = menu.screen("main", 0).orElseThrow();

    assertEquals(2, screen.rows());
    List<String> keys = new ArrayList<>();
    for (SettingsTile tile : screen.tiles()) {
      keys.add(tile.key());
    }
    assertEquals(List.of("cfg-main.display", "cfg-main.interval", "cfg-main.language", "cfg-main.mode", "cfg-main.names",
        "cfg-main.verbose", "cfg-back"), keys);

    SettingsTile display = tile(screen, "cfg-main.display");
    assertEquals(-3, display.position());
    assertEquals("compass", display.icon());
    assertEquals(new SettingsAction.Open("main.display", 0), display.actionFor(SettingsClick.LEFT));

    SettingsTile interval = tile(screen, "cfg-main.interval");
    assertEquals("clock", interval.icon());
    assertInstanceOf(SettingsAction.Input.class, interval.actionFor(SettingsClick.LEFT));
    SettingsAction.Input input = (SettingsAction.Input) interval.actionFor(SettingsClick.LEFT);
    assertEquals("main.interval", input.valuePath());
    assertEquals(int.class, input.type());
    assertEquals("Interval", input.label());

    assertEquals(new SettingsAction.ServerLanguage(), tile(screen, "cfg-main.language").actionFor(SettingsClick.LEFT));

    SettingsTile mode = tile(screen, "cfg-main.mode");
    assertEquals(new SettingsAction.Apply("main", 0, "main.mode", Mode.LOUD), mode.actionFor(SettingsClick.LEFT));
    assertEquals(new SettingsAction.Apply("main", 0, "main.mode", Mode.QUIET), mode.actionFor(SettingsClick.RIGHT));

    SettingsTile names = tile(screen, "cfg-main.names");
    assertEquals("barrel", names.icon());
    assertTrue(names.actions().isEmpty());

    SettingsTile verbose = tile(screen, "cfg-main.verbose");
    assertTrue(verbose.enchanted());
    assertEquals("lime_dye", verbose.icon());
    assertEquals(new SettingsAction.Apply("main", 0, "main.verbose", false), verbose.actionFor(SettingsClick.LEFT));

    SettingsTile back = tile(screen, "cfg-back");
    assertEquals(9 + 8, back.slot());
    assertEquals(new SettingsAction.Open("", 0), back.actionFor(SettingsClick.LEFT));
  }

  @Test
  void valueNamesCarryTypeAndCurrentValue() {
    SettingsScreen screen = menu.screen("main", 0).orElseThrow();

    assertEquals("§a[Boolean] §fVerbose§7 = §benabled", tile(screen, "cfg-main.verbose").name());
    assertEquals("§b[Number] §fInterval§7 = §b5", tile(screen, "cfg-main.interval").name());
    assertEquals("§9[Section] §fDisplay§7 (§b1§7)", tile(screen, "cfg-main.display").name());
  }

  @Test
  void coreIndexListsPlatformSectionsWithTheirFiles() {
    SettingsScreen screen = menu.screen("core", 0).orElseThrow();

    SettingsTile map = tile(screen, "cfg-index-core.map");
    assertEquals("filled_map", map.icon());
    assertEquals("slime_ball", map.fallbackIcon());
    assertEquals(new SettingsAction.Open("core.map", 0), map.actionFor(SettingsClick.LEFT));
    assertNull(map.actionFor(SettingsClick.RIGHT));
    assertTrue(menu.screen("core.map", 0).isPresent());
    assertFalse(menu.screen("core.missing", 0).isPresent());
  }

  @Test
  void applyWritesTheFileThenReloadsAndKeepsTheLiveObjectUntilReload() throws IOException {
    assertTrue(menu.applyAndSave(null, "main.interval", "12"));

    assertEquals(List.of("react.toml"), writes);
    assertEquals(1, mainReloads.get());
    assertEquals(12, main.interval);
    assertTrue(Files.readString(source.mainFile().toPath()).contains("interval = 12"));
  }

  @Test
  void applyRejectsUnknownPaths() {
    assertFalse(menu.applyAndSave(null, "main", 1));
    assertFalse(menu.applyAndSave(null, "feature.nothing.enabled", true));
    assertTrue(writes.isEmpty());
  }

  @Test
  void parseInputAcceptsBukkitSpellings() {
    assertEquals(true, ReactSettingsMenu.parseInputValue(boolean.class, "on").value());
    assertEquals(Mode.LOUD, ReactSettingsMenu.parseInputValue(Mode.class, "loud").value());
    assertFalse(ReactSettingsMenu.parseInputValue(double.class, "NaN").success());
    assertEquals("enum", ReactSettingsMenu.typeName(Mode.class));
    assertEquals("integer", ReactSettingsMenu.typeName(int.class));
  }

  private static SettingsTile tile(SettingsScreen screen, String key) {
    for (SettingsTile tile : screen.tiles()) {
      if (tile.key().equals(key)) {
        return tile;
      }
    }
    throw new AssertionError("no tile " + key + " in " + screen.tiles());
  }
}
