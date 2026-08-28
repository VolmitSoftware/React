package art.arcane.react.core.pluginapi;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PluginApiPlaceholderReaderTest {
  @Test
  void parsesNumericPlaceholderFormatting() {
    assertEquals(19.75D, PluginApiPlaceholderReader.parseResolved("§a*19.75%", "%spark_tps_1m%"));
    assertEquals(-2D, PluginApiPlaceholderReader.parseResolved(" -2 ", "%example_value%"));
  }

  @Test
  void rejectsUnresolvedAndNonNumericValues() {
    assertTrue(Double.isNaN(PluginApiPlaceholderReader.parseResolved("%server_online%", "%server_online%")));
    assertTrue(Double.isNaN(PluginApiPlaceholderReader.parseResolved("20 players", "%server_online%")));
    assertTrue(Double.isNaN(PluginApiPlaceholderReader.parseResolved("NaN", "%server_online%")));
  }
}
