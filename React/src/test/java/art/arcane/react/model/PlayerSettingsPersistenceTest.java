package art.arcane.react.model;

import art.arcane.react.React;
import art.arcane.react.util.common.scheduling.J;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.stream.Stream;

class PlayerSettingsPersistenceTest {
  @TempDir
  Path temporaryDirectory;

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
  void writesAreCoalescedOffThreadAndPublishedAtomically() throws Exception {
    UUID playerId = UUID.randomUUID();
    File target = temporaryDirectory.resolve(playerId + ".json").toFile();
    React plugin = Mockito.mock(React.class);
    Mockito.when(plugin.getDataFile("player-settings", playerId + ".json")).thenReturn(target);
    React.instance = plugin;
    ArgumentCaptor<Runnable> drain = ArgumentCaptor.forClass(Runnable.class);
    PlayerSettings first = new PlayerSettings();
    PlayerSettings latest = new PlayerSettings();
    latest.setVisualizing(true);

    try (MockedStatic<J> scheduler = Mockito.mockStatic(J.class)) {
      scheduler.when(() -> J.a(Mockito.any(Runnable.class))).thenAnswer(invocation -> null);

      PlayerSettings.saveSettings(playerId, first);
      PlayerSettings.saveSettings(playerId, latest);

      scheduler.verify(() -> J.a(drain.capture()), Mockito.times(1));
      Assertions.assertFalse(Files.exists(target.toPath()));
      drain.getValue().run();
    }

    Assertions.assertTrue(PlayerSettings.flushPendingSaves(0L));
    Assertions.assertTrue(PlayerSettings.get(playerId).isVisualizing());
    try (Stream<Path> files = Files.list(temporaryDirectory)) {
      Assertions.assertEquals(1L, files.count());
    }
  }

  @Test
  void failedCanonicalPublishRetainsACompleteRecoveryFile() throws Exception {
    UUID playerId = UUID.randomUUID();
    Path target = temporaryDirectory.resolve(playerId + ".json");
    Files.createDirectories(target);
    Files.writeString(target.resolve("blocker"), "occupied");
    String content = "{\"visualizing\":true}";

    Assertions.assertThrows(
        IOException.class,
        () -> PlayerSettings.writeAtomically(target.toFile(), content)
    );

    Path recovery = PlayerSettings.recoveryPath(target.toAbsolutePath());
    Assertions.assertTrue(Files.isRegularFile(recovery));
    Assertions.assertEquals(content, Files.readString(recovery).stripTrailing());
  }

  @Test
  void stagedRecoveryWinsOverTheOlderCanonicalFileAndRepublishes() throws Exception {
    UUID playerId = UUID.randomUUID();
    Path target = temporaryDirectory.resolve(playerId + ".json");
    Path recovery = PlayerSettings.recoveryPath(target.toAbsolutePath());
    Files.writeString(target, "{\"visualizing\":false}");
    Files.writeString(recovery, "{\"visualizing\":true}");
    React plugin = Mockito.mock(React.class);
    Mockito.when(plugin.getDataFile("player-settings", playerId + ".json")).thenReturn(target.toFile());
    React.instance = plugin;

    try (MockedStatic<J> scheduler = Mockito.mockStatic(J.class)) {
      scheduler.when(() -> J.a(Mockito.any(Runnable.class))).thenAnswer(invocation -> {
        Runnable drain = invocation.getArgument(0);
        drain.run();
        return null;
      });

      PlayerSettings recovered = PlayerSettings.get(playerId);

      Assertions.assertTrue(recovered.isVisualizing());
    }

    Assertions.assertFalse(Files.exists(recovery));
    Assertions.assertTrue(Files.readString(target).contains("\"visualizing\":true"));
    Assertions.assertTrue(PlayerSettings.flushPendingSaves(0L));
  }
}
