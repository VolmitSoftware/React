package art.arcane.react.api.monitor;

import art.arcane.react.React;
import art.arcane.react.api.monitor.configuration.MonitorConfiguration;
import art.arcane.react.model.PlayerSettings;
import art.arcane.react.model.ReactConfiguration;
import art.arcane.react.model.ReactPlayer;
import art.arcane.react.util.common.scheduling.J;
import art.arcane.react.util.common.scheduling.Ticker;
import art.arcane.volmlib.util.hud.HudTitleClaim;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

class ActionBarMonitorFoliaTest {
  private React previous;

  @BeforeEach
  void setUp() {
    previous = React.instance;
    React plugin = Mockito.mock(React.class);
    Mockito.when(plugin.getTicker()).thenReturn(Mockito.mock(Ticker.class));
    React.instance = plugin;
  }

  @AfterEach
  void tearDown() {
    React.instance = previous;
  }

  @Test
  void offOwnerFlushesCoalesceUntilTheAcceptedTaskTerminates() throws ReflectiveOperationException {
    Player player = Mockito.mock(Player.class);
    AtomicReference<Runnable> operation = new AtomicReference<>();
    AtomicReference<Runnable> retirement = new AtomicReference<>();

    try (MockedStatic<ReactConfiguration> configuration = Mockito.mockStatic(ReactConfiguration.class);
         MockedStatic<J> scheduling = Mockito.mockStatic(J.class)) {
      ActionBarMonitor monitor = monitor(configuration, player);
      setRunning(monitor, true);
      scheduling.when(J::isFoliaThreading).thenReturn(true);
      scheduling.when(() -> J.isOwnedByCurrentRegion(player)).thenReturn(false);
      scheduling.when(() -> J.runEntity(
          Mockito.same(player),
          Mockito.any(Runnable.class),
          Mockito.eq(0),
          Mockito.any(Runnable.class)
      )).thenAnswer(invocation -> {
        operation.set(invocation.getArgument(1));
        retirement.set(invocation.getArgument(3));
        return true;
      });

      for (int index = 0; index < 1_000; index++) {
        monitor.flush();
      }

      scheduling.verify(() -> J.runEntity(
          Mockito.same(player),
          Mockito.any(Runnable.class),
          Mockito.eq(0),
          Mockito.any(Runnable.class)
      ), Mockito.times(1));
      Assertions.assertNotNull(operation.get());
      Assertions.assertNotNull(retirement.get());

      setRunning(monitor, false);
      operation.get().run();
      setRunning(monitor, true);
      monitor.flush();

      scheduling.verify(() -> J.runEntity(
          Mockito.same(player),
          Mockito.any(Runnable.class),
          Mockito.eq(0),
          Mockito.any(Runnable.class)
      ), Mockito.times(2));
    }
  }

  @Test
  void retirementAndRejectedSubmissionReleaseTheFlushClaimExactlyOnce()
      throws ReflectiveOperationException {
    Player player = Mockito.mock(Player.class);
    AtomicInteger attempts = new AtomicInteger();

    try (MockedStatic<ReactConfiguration> configuration = Mockito.mockStatic(ReactConfiguration.class);
         MockedStatic<J> scheduling = Mockito.mockStatic(J.class)) {
      ActionBarMonitor monitor = monitor(configuration, player);
      setRunning(monitor, true);
      scheduling.when(J::isFoliaThreading).thenReturn(true);
      scheduling.when(() -> J.isOwnedByCurrentRegion(player)).thenReturn(false);
      scheduling.when(() -> J.runEntity(
          Mockito.same(player),
          Mockito.any(Runnable.class),
          Mockito.eq(0),
          Mockito.any(Runnable.class)
      )).thenAnswer(invocation -> {
        attempts.incrementAndGet();
        Runnable retired = invocation.getArgument(3);
        retired.run();
        return false;
      });

      monitor.flush();
      monitor.flush();
      monitor.flush();

      Assertions.assertEquals(3, attempts.get());
    }
  }

  private ActionBarMonitor monitor(
      MockedStatic<ReactConfiguration> configuration,
      Player player
  ) throws ReflectiveOperationException {
    ReactConfiguration reactConfiguration = Mockito.mock(ReactConfiguration.class);
    ReactConfiguration.Monitoring monitoring = Mockito.mock(ReactConfiguration.Monitoring.class);
    ReactPlayer reactPlayer = Mockito.mock(ReactPlayer.class);
    PlayerSettings settings = Mockito.mock(PlayerSettings.class);
    MonitorConfiguration monitorConfiguration = Mockito.mock(MonitorConfiguration.class);
    Mockito.when(player.getUniqueId()).thenReturn(UUID.randomUUID());
    Mockito.when(reactPlayer.getPlayer()).thenReturn(player);
    Mockito.when(reactPlayer.getSettings()).thenReturn(settings);
    Mockito.when(settings.getMonitorConfiguration()).thenReturn(monitorConfiguration);
    Mockito.when(reactConfiguration.getMonitoring()).thenReturn(monitoring);
    Mockito.when(monitoring.getActionBarHeaderSlots()).thenReturn(6);
    Mockito.when(monitoring.getActionBarSamplerSlots()).thenReturn(4);
    configuration.when(ReactConfiguration::get).thenReturn(reactConfiguration);

    ActionBarMonitor monitor = new ActionBarMonitor(reactPlayer);
    Field focusClaim = ActionBarMonitor.class.getDeclaredField("focusClaim");
    focusClaim.setAccessible(true);
    focusClaim.set(monitor, Mockito.mock(HudTitleClaim.class));
    return monitor;
  }

  private void setRunning(ActionBarMonitor monitor, boolean running) throws ReflectiveOperationException {
    Field runningField = ActionBarMonitor.class.getDeclaredField("running");
    runningField.setAccessible(true);
    runningField.setBoolean(monitor, running);
  }
}
