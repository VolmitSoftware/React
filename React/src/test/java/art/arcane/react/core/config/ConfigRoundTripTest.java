package art.arcane.react.core.config;

import art.arcane.react.model.ReactConfiguration;
import art.arcane.react.util.project.config.TomlCodec;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

public class ConfigRoundTripTest {

  public static final class WorldFlagSettings {
    public boolean enabled = true;
    public Map<String, Boolean> worldFlags = new LinkedHashMap<String, Boolean>();
  }

  public static final class SlowTickSettings {
    public ReactConfiguration.SlowTickLogMode slowTickLogMode = ReactConfiguration.SlowTickLogMode.BLAME;
  }

  @Test
  public void worldKeyedBooleanMapSurvivesTomlRoundTrip() throws Exception {
    WorldFlagSettings settings = new WorldFlagSettings();
    settings.worldFlags.put("world", true);
    settings.worldFlags.put("world_nether", false);

    String toml = TomlCodec.toToml(settings, "core:test");
    WorldFlagSettings parsed = TomlCodec.fromToml(toml, WorldFlagSettings.class);

    Assertions.assertNotNull(parsed.worldFlags);
    Assertions.assertEquals(2, parsed.worldFlags.size());
    Assertions.assertEquals(Boolean.TRUE, parsed.worldFlags.get("world"));
    Assertions.assertEquals(Boolean.FALSE, parsed.worldFlags.get("world_nether"));
    Assertions.assertTrue(parsed.enabled);
  }

  @Test
  public void falseMapValuesAreNotResetToDefaultOnRoundTrip() throws Exception {
    WorldFlagSettings settings = new WorldFlagSettings();
    settings.worldFlags.put("overworld", false);
    settings.worldFlags.put("resource_world", false);

    String toml = TomlCodec.toToml(settings, "core:test");
    WorldFlagSettings parsed = TomlCodec.fromToml(toml, WorldFlagSettings.class);

    Assertions.assertEquals(2, parsed.worldFlags.size());
    Assertions.assertEquals(Boolean.FALSE, parsed.worldFlags.get("overworld"));
    Assertions.assertEquals(Boolean.FALSE, parsed.worldFlags.get("resource_world"));
  }

  @Test
  public void worldKeyedMapValuesAreStableAcrossRepeatedRoundTrips() throws Exception {
    WorldFlagSettings settings = new WorldFlagSettings();
    settings.worldFlags.put("world", true);
    settings.worldFlags.put("world_nether", false);

    WorldFlagSettings first = TomlCodec.fromToml(
        TomlCodec.toToml(settings, "core:test"), WorldFlagSettings.class);
    WorldFlagSettings second = TomlCodec.fromToml(
        TomlCodec.toToml(first, "core:test"), WorldFlagSettings.class);

    Assertions.assertEquals(settings.worldFlags, first.worldFlags);
    Assertions.assertEquals(first.worldFlags, second.worldFlags);
  }

  @Test
  public void emptyWorldKeyedMapRoundTripsToEmptyNotNull() throws Exception {
    WorldFlagSettings settings = new WorldFlagSettings();

    String toml = TomlCodec.toToml(settings, "core:test");
    WorldFlagSettings parsed = TomlCodec.fromToml(toml, WorldFlagSettings.class);

    Assertions.assertNotNull(parsed.worldFlags);
    Assertions.assertTrue(parsed.worldFlags.isEmpty());
  }

  @Test
  public void slowTickOffModeSurvivesTomlRoundTrip() throws Exception {
    SlowTickSettings settings = new SlowTickSettings();
    settings.slowTickLogMode = ReactConfiguration.SlowTickLogMode.OFF;

    String toml = TomlCodec.toToml(settings, "main-config");
    SlowTickSettings parsed = TomlCodec.fromToml(toml, SlowTickSettings.class);

    Assertions.assertTrue(toml.contains("slowTickLogMode = \"OFF\""));
    Assertions.assertEquals(ReactConfiguration.SlowTickLogMode.OFF, parsed.slowTickLogMode);
  }

  @Test
  public void globalConfigStartsWithLanguageThenMetrics() {
    String toml = TomlCodec.toToml(new ReactConfiguration(), "main-config");

    int language = toml.indexOf("language = \"en_US\"");
    int metrics = toml.indexOf("metrics = true");
    int customColors = toml.indexOf("customColors = true");
    Assertions.assertTrue(language >= 0);
    Assertions.assertTrue(metrics > language);
    Assertions.assertTrue(customColors > metrics);
  }
}
