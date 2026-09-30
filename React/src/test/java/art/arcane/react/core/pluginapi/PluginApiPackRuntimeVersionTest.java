package art.arcane.react.core.pluginapi;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.io.IOException;
import java.nio.file.Path;

class PluginApiPackRuntimeVersionTest {
  @Test
  void targetVersionIsReadThroughTheSpigotDescription() throws IOException {
    PluginApiPackDefinition definition = PluginApiPackParser.parse(Path.of("version.toml"), pack());
    PluginApiPackRuntime runtime = new PluginApiPackRuntime(definition);
    Plugin target = Mockito.mock(Plugin.class);
    PluginManager pluginManager = Mockito.mock(PluginManager.class);
    Mockito.when(target.isEnabled()).thenReturn(true);
    Mockito.when(target.getPluginMeta()).thenThrow(new NoSuchMethodError("getPluginMeta"));
    Mockito.when(target.getDescription()).thenReturn(new PluginDescriptionFile("Example", "2.0.0", "example.Main"));
    Mockito.when(pluginManager.getPlugin("Example")).thenReturn(target);

    try (MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class)) {
      bukkit.when(Bukkit::getPluginManager).thenReturn(pluginManager);

      Assertions.assertDoesNotThrow(() -> runtime.collect(1_000L));
    }

    Assertions.assertEquals("2.0.0", runtime.targetVersion());
    Assertions.assertEquals(PluginApiPackRuntime.PackState.INCOMPATIBLE, runtime.state());
  }

  private static String pack() {
    return """
        schema = "react.plugin-api/v1"
        id = "test.version"
        version = "1.0.0"
        name = "Version"
        authors = ["Tests"]
        enabled = true
        trusted = false
        targetPlugin = "Example"
        targetVersions = ["1.*"]

        [[metrics]]
        id = "value"
        displayName = "Value"
        kind = "gauge"
        icon = "CLOCK"
        sampleEveryMs = 1000
        staleAfterMs = 15000

        [metrics.source]
        type = "integration"
        pluginId = "example"
        key = "metric.value"
        foliaSafe = true
        """;
  }
}
