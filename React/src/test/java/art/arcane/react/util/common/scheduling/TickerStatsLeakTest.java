package art.arcane.react.util.common.scheduling;

import art.arcane.react.React;
import art.arcane.react.model.ReactConfiguration;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TickerStatsLeakTest {
  @Test
  void unregisteringATickRemovesItsSlowTickBookkeeping() throws Exception {
    ReactConfiguration configuration = new ReactConfiguration();
    configuration.setSlowTickLogMode(ReactConfiguration.SlowTickLogMode.SHORT);
    AtomicLong clock = new AtomicLong(1_000L);
    ClockTicked ticked = new ClockTicked(clock::get, "player-uuid", 50L, () -> sleep(60L));
    Ticker ticker = new Ticker(Runnable::run);

    try (MockedStatic<ReactConfiguration> reactConfiguration = Mockito.mockStatic(ReactConfiguration.class);
         MockedStatic<React> react = Mockito.mockStatic(React.class)) {
      reactConfiguration.when(ReactConfiguration::get).thenReturn(configuration);

      ticker.register(ticked);
      ticker.tick();
      clock.addAndGet(50L);
      ticker.tick();

      assertEquals(1, ticked.runs);
      assertEquals(1, slowTickLogStates(ticker).size());

      ticker.unregister(ticked);
      ticker.tick();

      assertTrue(slowTickLogStates(ticker).isEmpty());
    } finally {
      ticker.close();
    }
  }

  private static Map<?, ?> slowTickLogStates(Ticker ticker) throws Exception {
    Field field = Ticker.class.getDeclaredField("slowTickLogStates");
    field.setAccessible(true);
    return (Map<?, ?>) field.get(ticker);
  }

  private static void sleep(long ms) {
    try {
      Thread.sleep(ms);
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
    }
  }
}
