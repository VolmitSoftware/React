package art.arcane.react;

import art.arcane.multiburst.MultiBurst;
import art.arcane.react.core.bridge.BytecodeAgent;
import art.arcane.react.model.ReactConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class ReactRuntimeSettingsTest {
  private final List<Runnable> pending = new ArrayList<>();
  private MultiBurst previousBurst;
  private ReactConfiguration previousConfiguration;
  private ReactConfiguration configuration;
  private React plugin;
  private ReactMetrics firstMetrics;
  private ReactMetrics secondMetrics;
  private MockedStatic<ReactMetrics> metrics;
  private MockedStatic<BytecodeAgent> bytecode;
  private MockedStatic<React> logging;

  @BeforeEach
  void prepareRuntime() throws Exception {
    previousBurst = React.burst;
    previousConfiguration = (ReactConfiguration) field(ReactConfiguration.class, "configuration").get(null);
    configuration = new ReactConfiguration();
    configuration.setMetrics(false);
    ReactConfiguration.applyHotloadSnapshot(configuration);
    plugin = mock(React.class);
    field(React.class, "runtimeSettingsLock").set(plugin, new Object());
    field(React.class, "runtimeSettingsSubmissionLock").set(plugin, new Object());
    field(React.class, "ready").set(plugin, true);
    doCallRealMethod().when(plugin).refreshRuntimeSettings(configuration);
    React.burst = mock(MultiBurst.class);
    doAnswer(invocation -> {
      pending.add(invocation.getArgument(0));
      return null;
    }).when(React.burst).lazy(any(Runnable.class));
    firstMetrics = mock(ReactMetrics.class);
    secondMetrics = mock(ReactMetrics.class);
    metrics = mockStatic(ReactMetrics.class);
    metrics.when(() -> ReactMetrics.start(plugin, 24219)).thenReturn(firstMetrics, secondMetrics);
    bytecode = mockStatic(BytecodeAgent.class);
    logging = mockStatic(React.class);
  }

  @AfterEach
  void restoreRuntime() throws Exception {
    logging.close();
    bytecode.close();
    metrics.close();
    React.burst = previousBurst;
    field(ReactConfiguration.class, "configuration").set(null, previousConfiguration);
  }

  @Test
  void startsStopsAndRestartsMetricsWithoutDuplicatingTheRuntime() {
    apply();
    metrics.verifyNoInteractions();

    configuration.setMetrics(true);
    apply();
    apply();
    metrics.verify(() -> ReactMetrics.start(plugin, 24219), times(1));

    configuration.setMetrics(false);
    apply();
    apply();
    verify(firstMetrics, times(1)).shutdown();

    configuration.setMetrics(true);
    apply();
    metrics.verify(() -> ReactMetrics.start(plugin, 24219), times(2));
  }

  @Test
  void queuedRefreshUsesTheLatestSettingsWithoutStartingAnObsoleteRuntime() {
    configuration.setMetrics(true);
    plugin.refreshRuntimeSettings(configuration);
    configuration.setMetrics(false);
    plugin.refreshRuntimeSettings(configuration);
    pending.removeFirst().run();
    pending.removeFirst().run();
    metrics.verifyNoInteractions();
  }

  @Test
  void queuedRefreshDoesNotReadUncommittedConfigEdits() {
    plugin.refreshRuntimeSettings(configuration);
    configuration.setMetrics(true);
    configuration.setUnsafeBytecode(true);
    pending.removeFirst().run();
    metrics.verifyNoInteractions();
    bytecode.verifyNoInteractions();
  }

  @Test
  void submittingSettingsDoesNotWaitForAnInFlightRuntimeOperation() throws Exception {
    Object lock = field(React.class, "runtimeSettingsLock").get(plugin);
    CountDownLatch entered = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    CompletableFuture<Void> applying = CompletableFuture.runAsync(() -> {
      synchronized (lock) {
        entered.countDown();
        try {
          assertTrue(release.await(5, TimeUnit.SECONDS));
        } catch (InterruptedException failure) {
          Thread.currentThread().interrupt();
          throw new AssertionError(failure);
        }
      }
    });
    try {
      assertTrue(entered.await(5, TimeUnit.SECONDS));
      assertTimeoutPreemptively(Duration.ofSeconds(1), () -> plugin.refreshRuntimeSettings(configuration));
      assertEquals(1, pending.size());
    } finally {
      release.countDown();
      applying.get(5, TimeUnit.SECONDS);
    }
  }

  @Test
  void queuedRefreshCannotStartMetricsAfterShutdown() throws Exception {
    configuration.setMetrics(true);
    plugin.refreshRuntimeSettings(configuration);
    field(React.class, "ready").set(plugin, false);
    pending.removeFirst().run();
    plugin.refreshRuntimeSettings(configuration);
    assertEquals(0, pending.size());
    metrics.verifyNoInteractions();
  }

  @Test
  void queuedRefreshCannotApplyToAReplacementRuntime() {
    configuration.setMetrics(true);
    plugin.refreshRuntimeSettings(configuration);
    React.burst = mock(MultiBurst.class);
    pending.removeFirst().run();
    metrics.verifyNoInteractions();
  }

  @Test
  void enablingBytecodeAttachesOnceAndDisablingDoesNotPretendToDetachIt() {
    AtomicBoolean installed = new AtomicBoolean();
    bytecode.when(BytecodeAgent::isInstalled).thenAnswer(invocation -> installed.get());
    bytecode.when(BytecodeAgent::install).thenAnswer(invocation -> {
      installed.set(true);
      return null;
    });
    apply();
    bytecode.verifyNoInteractions();
    configuration.setUnsafeBytecode(true);
    apply();
    apply();
    configuration.setUnsafeBytecode(false);
    apply();
    bytecode.verify(BytecodeAgent::install, times(1));
  }

  private void apply() {
    plugin.refreshRuntimeSettings(configuration);
    pending.removeFirst().run();
  }

  private static Field field(Class<?> type, String name) throws Exception {
    Field field = type.getDeclaredField(name);
    field.setAccessible(true);
    return field;
  }
}
