package art.arcane.react.core.gui;

import art.arcane.react.React;
import art.arcane.react.model.ReactConfiguration;
import art.arcane.react.util.project.config.ConfigFileSupport;
import art.arcane.react.util.project.config.TomlCodec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class MainConfigEditIsolationTest {
  @TempDir
  Path temporaryDirectory;

  @Test
  void failedBytecodeSettingSaveDoesNotExposeTheUncommittedSetting() throws IOException {
    assertFailedWriteKeepsActiveConfiguration("main.unsafeBytecode", true);
  }

  @Test
  void failedNestedSettingSaveDoesNotMutateActiveCollections() throws IOException {
    assertFailedWriteKeepsActiveConfiguration("main.value.valueMutlipliers", Map.of("DIAMOND", 500.0));
  }

  private void assertFailedWriteKeepsActiveConfiguration(String path, Object value) throws IOException {
    React previousPlugin = React.instance;
    React plugin = mock(React.class);
    ReactConfiguration active = new ReactConfiguration();
    File configFile = temporaryDirectory.resolve("react.toml").toFile();
    AtomicInteger writes = new AtomicInteger();
    when(plugin.getDataFile("react.toml")).thenReturn(configFile);
    React.instance = plugin;
    try (MockedStatic<ReactConfiguration> configuration = mockStatic(ReactConfiguration.class);
         MockedStatic<ConfigFileSupport> files = mockStatic(ConfigFileSupport.class)) {
      configuration.when(ReactConfiguration::get).thenReturn(active);
      configuration.when(ReactConfiguration::reload).thenReturn(false);
      files.when(() -> ConfigFileSupport.writeConfig(any(File.class), anyString())).thenAnswer(invocation -> {
        assertFalse(active.isUnsafeBytecode());
        assertEquals(25.0, active.getValue().getValueMutlipliers().get("DIAMOND"));
        if (writes.getAndIncrement() == 0) {
          ReactConfiguration candidate = TomlCodec.fromToml(invocation.getArgument(1), ReactConfiguration.class);
          if (value instanceof Boolean) {
            assertTrue(candidate.isUnsafeBytecode());
          } else {
            assertEquals(500.0, candidate.getValue().getValueMutlipliers().get("DIAMOND"));
          }
          throw new IOException("Write rejected");
        }
        return null;
      });

      assertFalse(ReactConfigGUI.applyAndSave(null, path, value));
      assertEquals(2, writes.get());
      assertFalse(active.isUnsafeBytecode());
      assertEquals(25.0, active.getValue().getValueMutlipliers().get("DIAMOND"));
    } finally {
      React.instance = previousPlugin;
    }
  }
}
