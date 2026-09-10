package art.arcane.react.api.benchmark;

import art.arcane.react.React;
import art.arcane.react.localization.ReactLanguage;
import art.arcane.react.localization.catalog.EnvironmentMessages;
import art.arcane.react.util.reflect.Platform;
import art.arcane.volmlib.util.localization.LanguageAudience;
import art.arcane.volmlib.util.plugin.ComponentMessenger;
import art.arcane.volmlib.util.plugin.ComponentText;
import art.arcane.volmlib.util.scheduling.FoliaScheduler;
import art.arcane.volmlib.util.web.MclogsClient;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginDescriptionFile;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.UUID;

class EnvironmentReportTest {
  private static final String SOURCE = "VolmitSoftware - React - v9.9.9-environment";
  private static final String USER_AGENT = "VolmitSoftware/React/9.9.9-environment";
  private static final String FAILURE_MESSAGE = "Unable to upload React environment report to mclo.gs";

  @Test
  void publishesOnlyTheEnvironmentSummaryAndSendsTheLocalizedClickableLink() throws Exception {
    try (Fixture fixture = new Fixture()) {
      URI url = URI.create("https://mclo.gs/Env123");
      Mockito.when(fixture.uploader.publish(Mockito.anyString(), Mockito.eq(SOURCE), Mockito.eq(USER_AGENT)))
          .thenReturn(url);

      fixture.report.upload(fixture.sender);

      Mockito.verifyNoInteractions(fixture.uploader);
      fixture.platform.verifyNoInteractions();
      fixture.messenger.verifyNoInteractions();
      fixture.bukkit.verify(Bukkit::getVersion);
      fixture.bukkit.clearInvocations();
      Mockito.clearInvocations(fixture.plugin);
      fixture.runAsync();

      ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
      Mockito.verify(fixture.uploader).publish(payload.capture(), Mockito.eq(SOURCE), Mockito.eq(USER_AGENT));
      String content = payload.getValue();
      Assertions.assertTrue(content.contains("9.9.9-environment"));
      Assertions.assertTrue(content.contains("test-server-version"));
      Assertions.assertTrue(content.contains("test-platform-version"));
      Assertions.assertTrue(content.contains("test-platform-name"));
      Assertions.assertTrue(content.contains("test-java-vendor"));
      Assertions.assertTrue(content.contains("test-java-version"));
      Assertions.assertTrue(content.contains("test-cpu-architecture"));
      Assertions.assertTrue(content.contains("Storage Information"));
      Assertions.assertTrue(content.contains("Memory Information"));
      Assertions.assertTrue(content.contains("CPU Overview"));
      Assertions.assertFalse(content.contains("diagnostic report"));
      Assertions.assertFalse(content.contains("JVM arguments"));
      Mockito.verify(fixture.plugin, Mockito.never()).debugDump();
      Mockito.verify(fixture.plugin, Mockito.never()).getDescription();
      fixture.bukkit.verifyNoInteractions();
      fixture.messenger.verifyNoInteractions();
      fixture.language.verifyNoInteractions();
      Assertions.assertTrue(fixture.entityReplies.isEmpty());
      fixture.runGlobalReply();

      ArgumentCaptor<ComponentText> message = ArgumentCaptor.forClass(ComponentText.class);
      fixture.messenger.verify(() -> ComponentMessenger.send(Mockito.eq(fixture.sender), message.capture()));
      Component component = MiniMessage.miniMessage().deserialize(message.getValue().miniMessage());
      Assertions.assertEquals("Bericht öffnen", message.getValue().plain());
      Assertions.assertEquals(ClickEvent.openUrl(url.toString()), component.clickEvent());
      fixture.language.verify(() -> ReactLanguage.component(EnvironmentMessages.UPLOAD_LINK));
      fixture.language.verify(() -> ReactLanguage.send(fixture.sender, EnvironmentMessages.UPLOAD_FAILED), Mockito.never());
      fixture.logging.verifyNoInteractions();
    }
  }

  @ParameterizedTest
  @MethodSource("uploadFailures")
  void uploadFailureLogsTheOriginalThrowableAndSendsLocalizedFeedback(Exception failure) throws Exception {
    try (Fixture fixture = new Fixture()) {
      Mockito.when(fixture.uploader.publish(Mockito.anyString(), Mockito.eq(SOURCE), Mockito.eq(USER_AGENT)))
          .thenThrow(failure);

      fixture.report.upload(fixture.sender);

      Mockito.verifyNoInteractions(fixture.uploader);
      fixture.runAsync();

      fixture.logging.verify(() -> React.warn(FAILURE_MESSAGE, failure));
      fixture.language.verifyNoInteractions();
      fixture.runGlobalReply();
      fixture.language.verify(() -> ReactLanguage.send(fixture.sender, EnvironmentMessages.UPLOAD_FAILED));
      fixture.messenger.verifyNoInteractions();
      Mockito.verify(fixture.uploader).publish(Mockito.anyString(), Mockito.eq(SOURCE), Mockito.eq(USER_AGENT));
    }
  }

  @Test
  void interruptedUploadRestoresTheInterruptAndReportsTheOriginalFailure() throws Exception {
    try (Fixture fixture = new Fixture()) {
      Assertions.assertFalse(Thread.currentThread().isInterrupted());
      InterruptedException failure = new InterruptedException("upload interrupted");
      Mockito.when(fixture.uploader.publish(Mockito.anyString(), Mockito.eq(SOURCE), Mockito.eq(USER_AGENT)))
          .thenThrow(failure);

      fixture.report.upload(fixture.sender);
      Assertions.assertFalse(Thread.currentThread().isInterrupted());
      Mockito.verifyNoInteractions(fixture.uploader);
      fixture.runAsync();

      Assertions.assertTrue(Thread.currentThread().isInterrupted());
      fixture.logging.verify(() -> React.warn("React environment report upload was interrupted", failure));
      fixture.language.verifyNoInteractions();
      fixture.runGlobalReply();
      fixture.language.verify(() -> ReactLanguage.send(fixture.sender, EnvironmentMessages.UPLOAD_FAILED));
      fixture.messenger.verifyNoInteractions();
    } finally {
      Thread.interrupted();
    }
  }

  @Test
  void playerReplyUsesThePlayerSchedulerAndAudienceAfterTheAsyncUpload() throws Exception {
    try (Fixture fixture = new Fixture()) {
      Player player = Mockito.mock(Player.class);
      UUID audience = UUID.fromString("f806f9f2-e16b-4aaf-b9c4-f3d36b039fd5");
      Mockito.when(player.getUniqueId()).thenReturn(audience);
      URI url = URI.create("https://mclo.gs/Player123");
      Mockito.when(fixture.uploader.publish(Mockito.anyString(), Mockito.eq(SOURCE), Mockito.eq(USER_AGENT)))
          .thenReturn(url);
      fixture.language.when(() -> ReactLanguage.component(EnvironmentMessages.UPLOAD_LINK)).thenAnswer(invocation -> {
        Assertions.assertEquals(audience, LanguageAudience.current());
        return Component.text("Bericht öffnen");
      });

      fixture.report.upload(player);

      Mockito.verifyNoInteractions(fixture.uploader);
      fixture.runAsync();
      fixture.scheduler.verify(() -> FoliaScheduler.runEntity(
          Mockito.eq(fixture.plugin), Mockito.eq(player), Mockito.any(Runnable.class)));
      Assertions.assertTrue(fixture.globalReplies.isEmpty());
      fixture.messenger.verifyNoInteractions();
      fixture.language.verifyNoInteractions();
      Assertions.assertEquals(1, fixture.entityReplies.size());
      fixture.entityReplies.removeFirst().run();

      ArgumentCaptor<ComponentText> message = ArgumentCaptor.forClass(ComponentText.class);
      fixture.messenger.verify(() -> ComponentMessenger.send(Mockito.eq(player), message.capture()));
      Assertions.assertEquals(ClickEvent.openUrl(url.toString()),
          MiniMessage.miniMessage().deserialize(message.getValue().miniMessage()).clickEvent());
      fixture.language.verify(() -> ReactLanguage.component(EnvironmentMessages.UPLOAD_LINK));
    }
  }

  @Test
  void rejectedAsyncSchedulingReportsFailureWithoutUploading() {
    try (Fixture fixture = new Fixture()) {
      fixture.scheduler.when(() -> FoliaScheduler.runAsync(Mockito.eq(fixture.plugin), Mockito.any(Runnable.class)))
          .thenReturn(false);

      fixture.report.upload(fixture.sender);

      Mockito.verifyNoInteractions(fixture.uploader);
      fixture.platform.verifyNoInteractions();
      fixture.logging.verify(() -> React.warn("Unable to schedule React environment report upload"));
      fixture.language.verify(() -> ReactLanguage.send(fixture.sender, EnvironmentMessages.UPLOAD_FAILED));
      fixture.messenger.verifyNoInteractions();
      Assertions.assertTrue(fixture.asyncTasks.isEmpty());
      Assertions.assertTrue(fixture.globalReplies.isEmpty());
      Assertions.assertTrue(fixture.entityReplies.isEmpty());
    }
  }

  @Test
  void pluginShutdownPreventsUploadFeedback() throws Exception {
    try (Fixture fixture = new Fixture()) {
      Mockito.when(fixture.uploader.publish(Mockito.anyString(), Mockito.eq(SOURCE), Mockito.eq(USER_AGENT)))
          .thenReturn(URI.create("https://mclo.gs/Stopped123"));
      fixture.report.upload(fixture.sender);
      Mockito.when(fixture.plugin.isEnabled()).thenReturn(false);

      fixture.runAsync();

      fixture.messenger.verifyNoInteractions();
      fixture.language.verifyNoInteractions();
      Assertions.assertTrue(fixture.globalReplies.isEmpty());
      Assertions.assertTrue(fixture.entityReplies.isEmpty());
    }
  }

  private static List<Exception> uploadFailures() {
    return List.of(new IOException("service unavailable"), new IllegalStateException("upload failed"));
  }

  private static final class Fixture implements AutoCloseable {
    private final React previousPlugin = React.instance;
    private final React plugin = Mockito.mock(React.class);
    private final CommandSender sender = Mockito.mock(CommandSender.class);
    private final MclogsClient uploader = Mockito.mock(MclogsClient.class);
    private final EnvironmentReport report = new EnvironmentReport(uploader);
    private final MockedStatic<React> logging = Mockito.mockStatic(React.class);
    private final MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class);
    private final MockedStatic<Platform> platform = Mockito.mockStatic(Platform.class);
    private final MockedStatic<Platform.ENVIRONMENT> environment = Mockito.mockStatic(Platform.ENVIRONMENT.class);
    private final MockedStatic<Platform.STORAGE> storage = Mockito.mockStatic(Platform.STORAGE.class);
    private final MockedStatic<Platform.MEMORY.PHYSICAL> physical = Mockito.mockStatic(Platform.MEMORY.PHYSICAL.class);
    private final MockedStatic<Platform.MEMORY.VIRTUAL> virtual = Mockito.mockStatic(Platform.MEMORY.VIRTUAL.class);
    private final MockedStatic<Platform.CPU> cpu = Mockito.mockStatic(Platform.CPU.class);
    private final MockedStatic<ReactLanguage> language = Mockito.mockStatic(ReactLanguage.class);
    private final MockedStatic<ComponentMessenger> messenger = Mockito.mockStatic(ComponentMessenger.class);
    private final MockedStatic<FoliaScheduler> scheduler = Mockito.mockStatic(FoliaScheduler.class);
    private final Deque<Runnable> asyncTasks = new ArrayDeque<>();
    private final Deque<Runnable> globalReplies = new ArrayDeque<>();
    private final Deque<Runnable> entityReplies = new ArrayDeque<>();

    private Fixture() {
      React.instance = plugin;
      Mockito.when(plugin.getDescription()).thenReturn(new PluginDescriptionFile(
          "React", "9.9.9-environment", "art.arcane.react.React"));
      Mockito.when(plugin.isEnabled()).thenReturn(true);
      bukkit.when(Bukkit::getVersion).thenReturn("test-server-version");
      platform.when(Platform::getVersion).thenReturn("test-platform-version");
      platform.when(Platform::getName).thenReturn("test-platform-name");
      environment.when(Platform.ENVIRONMENT::getJavaVendor).thenReturn("test-java-vendor");
      environment.when(Platform.ENVIRONMENT::getJavaVersion).thenReturn("test-java-version");
      cpu.when(Platform.CPU::getArchitecture).thenReturn("test-cpu-architecture");
      cpu.when(Platform.CPU::getAvailableProcessors).thenReturn(8);
      cpu.when(Platform.CPU::getCPULoad).thenReturn(0.25);
      cpu.when(Platform.CPU::getLiveProcessCPULoad).thenReturn(0.125);
      language.when(() -> ReactLanguage.component(EnvironmentMessages.UPLOAD_LINK))
          .thenReturn(Component.text("Bericht öffnen"));
      scheduler.when(() -> FoliaScheduler.runAsync(Mockito.eq(plugin), Mockito.any(Runnable.class)))
          .thenAnswer(invocation -> {
            asyncTasks.addLast(invocation.getArgument(1, Runnable.class));
            return true;
          });
      scheduler.when(() -> FoliaScheduler.runGlobal(Mockito.eq(plugin), Mockito.any(Runnable.class)))
          .thenAnswer(invocation -> {
            globalReplies.addLast(invocation.getArgument(1, Runnable.class));
            return true;
          });
      scheduler.when(() -> FoliaScheduler.runEntity(Mockito.eq(plugin), Mockito.any(Player.class), Mockito.any(Runnable.class)))
          .thenAnswer(invocation -> {
            entityReplies.addLast(invocation.getArgument(2, Runnable.class));
            return true;
          });
    }

    private void runAsync() {
      Assertions.assertEquals(1, asyncTasks.size());
      asyncTasks.removeFirst().run();
    }

    private void runGlobalReply() {
      Assertions.assertEquals(1, globalReplies.size());
      globalReplies.removeFirst().run();
    }

    @Override
    public void close() {
      scheduler.close();
      messenger.close();
      language.close();
      cpu.close();
      virtual.close();
      physical.close();
      storage.close();
      environment.close();
      platform.close();
      bukkit.close();
      logging.close();
      React.instance = previousPlugin;
    }
  }
}
