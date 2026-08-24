package art.arcane.react.web;

import art.arcane.react.React;
import art.arcane.react.api.web.WebConfiguration;
import art.arcane.react.api.web.WebConfigurationSchema;
import art.arcane.react.core.controller.WebController;
import art.arcane.react.util.project.config.TomlCodec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

class WebConfigurationSchemaTest {
  private React previous;

  @BeforeEach
  void setUp() {
    previous = React.instance;
  }

  @AfterEach
  void tearDown() {
    React.instance = previous;
  }

  @Test
  void currentListenerKeysAreAccepted(@TempDir File dataFolder) throws Exception {
    File webToml = new File(dataFolder, "web.toml");
    Files.writeString(
        webToml.toPath(),
        "listenerEnabled = true\nlistenAddress = \"::\"\n",
        StandardCharsets.UTF_8
    );

    Assertions.assertDoesNotThrow(() -> WebConfigurationSchema.requireCurrent(
        webToml,
        new File(dataFolder, "web.json")
    ));
  }

  @Test
  void generatedConfigurationContainsOnlyCurrentListenerKeys(@TempDir File dataFolder) throws Exception {
    String generated = TomlCodec.toToml(new WebConfiguration(), "web-config");
    File webToml = new File(dataFolder, "web.toml");
    Files.writeString(webToml.toPath(), generated, StandardCharsets.UTF_8);

    Assertions.assertTrue(generated.contains("listenerEnabled = true"));
    Assertions.assertTrue(generated.contains("listenAddress = \"::\""));
    Assertions.assertFalse(generated.contains("\nenabled ="));
    Assertions.assertFalse(generated.contains("\nbindAddress ="));
    Assertions.assertDoesNotThrow(() -> WebConfigurationSchema.requireCurrent(
        webToml,
        new File(dataFolder, "web.json")
    ));
  }

  @Test
  void obsoleteTomlKeysAreRejectedWithExplicitResetInstructions(@TempDir File dataFolder) throws Exception {
    File webToml = new File(dataFolder, "web.toml");
    Files.writeString(
        webToml.toPath(),
        "enabled = false\nbindAddress = \"127.0.0.1\"\n",
        StandardCharsets.UTF_8
    );

    IOException failure = Assertions.assertThrows(
        IOException.class,
        () -> WebConfigurationSchema.requireCurrent(webToml, new File(dataFolder, "web.json"))
    );
    Assertions.assertTrue(failure.getMessage().contains(webToml.getPath()));
    Assertions.assertTrue(failure.getMessage().contains("Delete this file"));
    Assertions.assertTrue(failure.getMessage().contains("deletion removes its local changes"));
    Assertions.assertTrue(failure.getMessage().contains("Restart the server"));
    Assertions.assertTrue(failure.getMessage().contains("does not migrate"));
  }

  @Test
  void obsoleteJsonConfigurationIsRejectedWithoutMigration(@TempDir File dataFolder) throws Exception {
    File webJson = new File(dataFolder, "web.json");
    Files.writeString(webJson.toPath(), "{}", StandardCharsets.UTF_8);

    IOException failure = Assertions.assertThrows(
        IOException.class,
        () -> WebConfigurationSchema.requireCurrent(new File(dataFolder, "web.toml"), webJson)
    );
    Assertions.assertTrue(failure.getMessage().contains(webJson.getPath()));
    Assertions.assertTrue(failure.getMessage().contains("no longer supported"));
    Assertions.assertTrue(failure.getMessage().contains("does not migrate"));
  }

  @Test
  void obsoleteConfigurationLeavesTheListenerFailedClosed(@TempDir File dataFolder) throws Exception {
    File webToml = new File(dataFolder, "web.toml");
    Files.writeString(
        webToml.toPath(),
        "enabled = true\nbindAddress = \"0.0.0.0\"\n",
        StandardCharsets.UTF_8
    );
    React plugin = Mockito.mock(React.class);
    Mockito.when(plugin.getDataFile("web.toml")).thenReturn(webToml);
    Mockito.when(plugin.getDataFile("web.json")).thenReturn(new File(dataFolder, "web.json"));
    React.instance = plugin;
    WebController controller = new WebController();

    controller.loadConfiguration();
    controller.postStart();

    Assertions.assertFalse(controller.getConfig().isListenerEnabled());
    Assertions.assertNull(controller.getApp());
    Assertions.assertEquals(0, controller.getBoundPort());
    Assertions.assertNotNull(controller.getStartFailure());
    Assertions.assertTrue(controller.pairingUnavailableReason().contains("Obsolete React web configuration"));
  }
}
