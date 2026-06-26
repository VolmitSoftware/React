package art.arcane.react.core.config;

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
}
