package art.arcane.react.util.common.scheduling;

import art.arcane.react.React;
import art.arcane.react.model.ReactConfiguration;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.lang.reflect.Method;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

class TickerSlowTickWarningTest {
  @Test
  void offModeSuppressesSlowTickWarnings() throws Exception {
    ReactConfiguration configuration = new ReactConfiguration();
    configuration.setSlowTickLogMode(ReactConfiguration.SlowTickLogMode.OFF);
    Ticked ticked = slowTickedTask();
    Ticker ticker = new Ticker();

    try (MockedStatic<ReactConfiguration> reactConfiguration = Mockito.mockStatic(ReactConfiguration.class);
         MockedStatic<React> react = Mockito.mockStatic(React.class)) {
      reactConfiguration.when(ReactConfiguration::get).thenReturn(configuration);

      invokeSlowTickWarning(ticker, ticked);

      react.verifyNoInteractions();
    } finally {
      ticker.close();
    }
  }

  @Test
  void blameModeStillEmitsSlowTickWarnings() throws Exception {
    ReactConfiguration configuration = new ReactConfiguration();
    configuration.setSlowTickLogMode(ReactConfiguration.SlowTickLogMode.BLAME);
    Ticked ticked = slowTickedTask();
    Ticker ticker = new Ticker();

    try (MockedStatic<ReactConfiguration> reactConfiguration = Mockito.mockStatic(ReactConfiguration.class);
         MockedStatic<React> react = Mockito.mockStatic(React.class)) {
      reactConfiguration.when(ReactConfiguration::get).thenReturn(configuration);

      invokeSlowTickWarning(ticker, ticked);

      react.verify(() -> React.warn(Mockito.contains("Slow tick [CRITICAL]: controller:hotload")));
    } finally {
      ticker.close();
    }
  }

  @Test
  void offModeClearsPriorWarningThrottleState() throws Exception {
    ReactConfiguration configuration = new ReactConfiguration();
    Ticked ticked = slowTickedTask();
    Ticker ticker = new Ticker();

    try (MockedStatic<ReactConfiguration> reactConfiguration = Mockito.mockStatic(ReactConfiguration.class);
         MockedStatic<React> react = Mockito.mockStatic(React.class)) {
      reactConfiguration.when(ReactConfiguration::get).thenReturn(configuration);

      invokeSlowTickWarning(ticker, ticked);
      configuration.setSlowTickLogMode(ReactConfiguration.SlowTickLogMode.OFF);
      invokeSlowTickWarning(ticker, ticked);
      configuration.setSlowTickLogMode(ReactConfiguration.SlowTickLogMode.BLAME);
      invokeSlowTickWarning(ticker, ticked);

      react.verify(() -> React.warn(Mockito.contains("Slow tick [CRITICAL]: controller:hotload")), times(2));
    } finally {
      ticker.close();
    }
  }

  private Ticked slowTickedTask() {
    Ticked ticked = mock(Ticked.class);
    when(ticked.getTgroup()).thenReturn("react");
    when(ticked.getTid()).thenReturn("hotload");
    when(ticked.getTinterval()).thenReturn(500L);
    when(ticked.getAge()).thenReturn(1000L);
    return ticked;
  }

  private void invokeSlowTickWarning(Ticker ticker, Ticked ticked) throws Exception {
    Class<?> snapshotType = Class.forName(Ticker.class.getName() + "$SlowTickSnapshot");
    Method method = Ticker.class.getDeclaredMethod("warnSlowTick", Ticked.class, long.class, snapshotType);
    method.setAccessible(true);
    method.invoke(ticker, ticked, 126L, null);
  }
}
