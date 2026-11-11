package art.arcane.react.util.project.registry;

import art.arcane.react.React;
import art.arcane.react.content.feature.FeatureFastLeafDecay;
import art.arcane.react.util.project.config.ConfigFileSupport;
import art.arcane.react.util.project.config.ConfigHotloadSnapshot;
import art.arcane.react.util.project.config.TomlCodec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Follows a config file from disk to the live object through the calls HotloadController and
 * Registry make: capture the file, parse it into a fresh object, copy that onto the live one.
 */
class RegisteredHotloadFlowTest {
  private static final long HOTLOAD_FILE_LIMIT = 256L * 1024L;

  public enum Pace {
    CALM, BRISK, FRANTIC
  }

  public static class Limits {
    private int perChunk = 4;
    private double share = 0.5;
    private List<String> exempt = new ArrayList<>(List.of("villager"));
  }

  public static class BaseComponent implements Registered {
    private boolean enabled = true;
    private long baseBudget = 50L;

    @Override
    public String getId() {
      return "flow-component";
    }

    @Override
    public String getConfigCategory() {
      return "feature";
    }
  }

  public static class FlowComponent extends BaseComponent {
    private transient int appliedTicks = 7;
    private int radius = 6;
    private float falloff = 0.25F;
    private String sound = "minecraft:block.grass.break";
    private Pace pace = Pace.CALM;
    private List<String> worlds = new ArrayList<>(List.of("world"));
    private Map<String, Boolean> worldFlags = new LinkedHashMap<>(Map.of("world", true));
    private Limits limits = new Limits();
  }

  @TempDir
  Path dataFolder;

  private React previousPlugin;

  @BeforeEach
  void installPlugin() {
    previousPlugin = React.instance;
    React plugin = Mockito.mock(React.class);
    Mockito.when(plugin.getDataFile(ArgumentMatchers.any(String[].class))).thenAnswer(invocation -> {
      Path file = dataFolder;
      for (Object part : invocation.getArguments()) {
        file = file.resolve((String) part);
      }
      Files.createDirectories(file.getParent());
      return file.toFile();
    });
    React.instance = plugin;
  }

  @AfterEach
  void restorePlugin() {
    React.instance = previousPlugin;
  }

  private Path configFile(Registered component) {
    return dataFolder.resolve(component.getConfigCategory()).resolve(component.getId() + ".toml");
  }

  private static Object field(Object instance, String name) {
    Field field = ConfigurationFieldScenarios.findByName(instance.getClass(), name);
    return ConfigurationFieldScenarios.read(field, instance);
  }

  private static void write(Path file, String content) throws IOException {
    Files.createDirectories(file.getParent());
    Files.writeString(file, content, StandardCharsets.UTF_8);
  }

  private static String editedFlowToml() {
    FlowComponent edited = new FlowComponent();
    ((BaseComponent) edited).enabled = false;
    ((BaseComponent) edited).baseBudget = 75L;
    edited.radius = 12;
    edited.falloff = 0.75F;
    edited.sound = "minecraft:block.azalea_leaves.fall";
    edited.pace = Pace.FRANTIC;
    edited.worlds = new ArrayList<>(List.of("world", "world_nether"));
    edited.worldFlags = new LinkedHashMap<>(Map.of("world", false, "world_the_end", true));
    edited.limits.perChunk = 9;
    edited.limits.share = 0.9;
    edited.limits.exempt = new ArrayList<>(List.of("villager", "allay"));
    edited.appliedTicks = 99;
    return TomlCodec.toToml(edited, "feature:flow-component");
  }

  private static void assertEditedFlowValues(FlowComponent live) {
    assertEquals(false, field(live, "enabled"));
    assertEquals(75L, field(live, "baseBudget"));
    assertEquals(12, live.radius);
    assertEquals(0.75F, live.falloff);
    assertEquals("minecraft:block.azalea_leaves.fall", live.sound);
    assertEquals(Pace.FRANTIC, live.pace);
    assertEquals(List.of("world", "world_nether"), live.worlds);
    assertEquals(Map.of("world", false, "world_the_end", true), live.worldFlags);
    assertEquals(9, live.limits.perChunk);
    assertEquals(0.9, live.limits.share);
    assertEquals(List.of("villager", "allay"), live.limits.exempt);
  }

  private static void assertDefaultFlowValues(FlowComponent live) {
    assertEquals(true, field(live, "enabled"));
    assertEquals(50L, field(live, "baseBudget"));
    assertEquals(6, live.radius);
    assertEquals(0.25F, live.falloff);
    assertEquals("minecraft:block.grass.break", live.sound);
    assertEquals(Pace.CALM, live.pace);
    assertEquals(List.of("world"), live.worlds);
    assertEquals(Map.of("world", true), live.worldFlags);
    assertEquals(4, live.limits.perChunk);
  }

  @Test
  void firstLoadWritesDefaultsAndLeavesTheLiveObjectAlone() throws IOException {
    FlowComponent live = new FlowComponent();
    Limits limitsBefore = live.limits;

    live.loadConfiguration();

    String written = Files.readString(configFile(live));
    assertTrue(written.contains("radius = 6"), written);
    assertTrue(written.contains("baseBudget = 50"), written);
    assertFalse(written.contains("appliedTicks"), written);
    assertDefaultFlowValues(live);
    assertSame(limitsBefore, live.limits);
  }

  @Test
  void reloadAfterAFileEditUpdatesEveryFieldOfTheLiveObject() throws IOException {
    FlowComponent live = new FlowComponent();
    live.loadConfiguration();
    write(configFile(live), editedFlowToml());

    assertTrue(live.reloadConfiguration());

    assertEditedFlowValues(live);
    assertEquals(7, live.appliedTicks);
  }

  @Test
  void secondEditReplacesTheFirst() throws IOException {
    FlowComponent live = new FlowComponent();
    live.loadConfiguration();
    write(configFile(live), editedFlowToml());
    assertTrue(live.reloadConfiguration());

    write(configFile(live), Files.readString(configFile(live)).replace("radius = 12", "radius = 3"));
    assertTrue(live.reloadConfiguration());

    assertEquals(3, live.radius);
    assertEquals(Pace.FRANTIC, live.pace);
  }

  @Test
  void keyMissingFromTheFileFallsBackToTheClassDefault() throws IOException {
    FlowComponent live = new FlowComponent();
    live.radius = 40;
    live.sound = "runtime-sound";
    write(configFile(live), "radius = 15\n");

    assertTrue(live.reloadConfiguration());

    assertEquals(15, live.radius);
    assertEquals("minecraft:block.grass.break", live.sound);
  }

  @Test
  void malformedFileOnReloadKeepsLiveValuesAndTheFileItself() throws IOException {
    FlowComponent live = new FlowComponent();
    live.loadConfiguration();
    write(configFile(live), editedFlowToml());
    assertTrue(live.reloadConfiguration());
    String malformed = "radius = [unterminated\n= = =";
    write(configFile(live), malformed);

    assertFalse(live.reloadConfiguration());

    assertEditedFlowValues(live);
    assertEquals(malformed, Files.readString(configFile(live)));
    assertFalse(Files.exists(configFile(live).resolveSibling("flow-component.toml.bak")));
  }

  @Test
  void wrongValueTypeOnReloadKeepsLiveValues() throws IOException {
    FlowComponent live = new FlowComponent();
    live.loadConfiguration();
    write(configFile(live), "radius = \"wide\"\nsound = \"changed\"\n");

    assertFalse(live.reloadConfiguration());

    assertDefaultFlowValues(live);
  }

  @Test
  void malformedFileAtStartupIsBackedUpAndResetToDefaults() throws IOException {
    FlowComponent live = new FlowComponent();
    String malformed = "radius = [unterminated\n= = =";
    write(configFile(live), malformed);

    live.loadConfiguration();

    assertDefaultFlowValues(live);
    assertEquals(malformed, Files.readString(configFile(live).resolveSibling("flow-component.toml.bak")));
    assertTrue(Files.readString(configFile(live)).contains("radius = 6"));
  }

  @Test
  void capturedSnapshotAppliesTheWayTheHotloadControllerDoes() throws IOException {
    FlowComponent live = new FlowComponent();
    live.loadConfiguration();
    write(configFile(live), editedFlowToml());

    ConfigHotloadSnapshot snapshot = ConfigHotloadSnapshot.capture(configFile(live), HOTLOAD_FILE_LIMIT);
    Object prepared = live.prepareConfigurationSnapshot(configFile(live).toFile(), snapshot.rawContent());
    assertDefaultFlowValues(live);
    assertTrue(live.applyConfigurationSnapshot(prepared));

    assertEditedFlowValues(live);
    assertSame(((FlowComponent) prepared).worlds, live.worlds);
    assertSame(((FlowComponent) prepared).limits, live.limits);
    assertEquals(7, live.appliedTicks);
  }

  @Test
  void malformedSnapshotIsRejectedBeforeTheLiveObjectIsTouched() throws IOException {
    FlowComponent live = new FlowComponent();
    File file = configFile(live).toFile();

    assertThrows(IOException.class, () -> live.prepareConfigurationSnapshot(file, "radius = [unterminated\n= = ="));
    assertThrows(IOException.class, () -> live.prepareConfigurationSnapshot(file, "radius = \"wide\"\n"));
    assertThrows(IOException.class, () -> live.prepareConfigurationSnapshot(file, "pace = \"NOT_A_PACE\"\nradius = true\n"));

    assertDefaultFlowValues(live);
  }

  @Test
  void snapshotOfAnotherTypeOrNullIsRefused() {
    FlowComponent live = new FlowComponent();

    assertFalse(live.applyConfigurationSnapshot(null));
    assertFalse(live.applyConfigurationSnapshot(new Object()));
    assertFalse(live.applyConfigurationSnapshot(new BaseComponent()));
    assertFalse(live.applyConfigurationSnapshot(new FeatureFastLeafDecay()));

    assertDefaultFlowValues(live);
  }

  @Test
  void reloadInsideAPassiveSnapshotReadsTheSnapshotInsteadOfTheDisk() throws IOException {
    FlowComponent live = new FlowComponent();
    live.loadConfiguration();
    String onDisk = Files.readString(configFile(live));

    boolean reloaded = ConfigFileSupport.withPassiveHotloadSnapshot(configFile(live).toFile(), editedFlowToml(), live::reloadConfiguration);

    assertTrue(reloaded);
    assertEditedFlowValues(live);
    assertEquals(onDisk, Files.readString(configFile(live)));
  }

  @Test
  void shippedFeatureFileRoundTripsThroughLoadEditAndReload() throws IOException {
    FeatureFastLeafDecay live = new FeatureFastLeafDecay();
    Path file = configFile(live);
    live.loadConfiguration();
    String defaults = Files.readString(file);
    assertTrue(defaults.contains("leafDecayDistance = 6"), defaults);
    assertEquals("feature", file.getParent().getFileName().toString());

    write(file, defaults
        .replace("leafDecayDistance = 6", "leafDecayDistance = 3")
        .replace("soundChance = 0.25", "soundChance = 0.5")
        .replace("playSounds = true", "playSounds = false")
        .replace("decaySound = \"minecraft:block.azalea_leaves.fall\"", "decaySound = \"minecraft:block.grass.break\"")
        .replace("enabled = true", "enabled = false"));
    assertTrue(live.reloadConfiguration());

    assertEquals(3, field(live, "leafDecayDistance"));
    assertEquals(0.5, field(live, "soundChance"));
    assertEquals(false, field(live, "playSounds"));
    assertEquals("minecraft:block.grass.break", field(live, "decaySound"));
    assertFalse(live.isEnabled());
    assertEquals(5, field(live, "leafDecayRadius"));

    write(file, "leafDecayDistance = [unterminated");
    assertFalse(live.reloadConfiguration());
    assertEquals(3, field(live, "leafDecayDistance"));
    assertFalse(live.isEnabled());
  }
}
