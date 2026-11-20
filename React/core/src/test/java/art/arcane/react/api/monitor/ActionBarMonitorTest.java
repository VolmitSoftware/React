package art.arcane.react.api.monitor;

import art.arcane.react.api.monitor.configuration.MonitorConfiguration;
import art.arcane.react.api.sampler.Sampler;
import art.arcane.react.model.PlayerSettings;
import art.arcane.react.platform.Subscription;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

class ActionBarMonitorTest {
  private static final UUID VIEWER = UUID.randomUUID();

  @Test
  void visibilityChangesDuringATickDoNotBreakSampling() {
    ActionBarMonitor monitor = monitor();
    Sampler late = sampler("late", null);
    AtomicBoolean mutated = new AtomicBoolean(false);
    List<Sampler> initial = new ArrayList<>();
    for (int index = 0; index < 4; index++) {
      initial.add(sampler("initial-" + index, () -> {
        if (mutated.compareAndSet(false, true)) {
          monitor.clearVisibility();
          for (Sampler sampler : initial) {
            monitor.setVisible(sampler, true);
          }
          monitor.setVisible(late, true);
        }
      }));
    }
    for (Sampler sampler : initial) {
      monitor.setVisible(sampler, true);
    }

    Assertions.assertDoesNotThrow(monitor::tick);
    Assertions.assertDoesNotThrow(monitor::tick);

    Assertions.assertTrue(mutated.get());
    Assertions.assertTrue(monitor.sampled().keySet().containsAll(initial));
    Assertions.assertTrue(monitor.sampled().containsKey(late));
  }

  @Test
  void nullSamplersAreNotTrackedAsVisible() {
    ActionBarMonitor monitor = monitor();
    monitor.setVisible(null, true);
    monitor.setVisible(sampler("present", null), true);

    Assertions.assertEquals(1, monitor.visibility().size());
  }

  @Test
  void settledValuesSlowTheMonitorToOnceASecond() {
    ActionBarMonitor monitor = monitor();
    Sampler steady = sampler("steady", null);
    monitor.setVisible(steady, true);

    for (int tick = 0; tick < 400; tick++) {
      monitor.tick();
    }
    Assertions.assertEquals(1000L, monitor.interval());

    monitor.wakeUp();
    Assertions.assertEquals(50L, monitor.interval());
  }

  @Test
  void inputLocksOnADoubleSneakAndScrollsOnlyWhileSneaking() {
    MonitorInput input = new MonitorInput();
    Assertions.assertFalse(input.scrolled(0, 3, false));
    input.sneak(true, false, 1_000L);
    input.sneak(false, false, 1_100L);
    input.sneak(true, false, 1_200L);
    Assertions.assertTrue(input.isLocked());
    Assertions.assertTrue(input.scrolled(0, 3, true));
    Assertions.assertEquals(3, input.getScrollPosition(9));
    Assertions.assertEquals(1, input.getScrollPosition(2));

    input.moved(-5D);
    input.tick();
    Assertions.assertFalse(input.isMonitorSneaking());
  }

  private static ActionBarMonitor monitor() {
    PlayerSettings settings = new PlayerSettings();
    settings.setMonitorConfiguration(MonitorConfiguration.builder().build());
    MonitorViewer viewer = new MonitorViewer() {
      @Override
      public UUID viewerId() {
        return VIEWER;
      }

      @Override
      public PlayerSettings getSettings() {
        return settings;
      }

      @Override
      public boolean isMonitorSneaking() {
        return false;
      }

      @Override
      public boolean isLocked() {
        return false;
      }

      @Override
      public int getScrollPosition(int maxRemainder) {
        return 0;
      }
    };
    return new ActionBarMonitor(viewer, new MonitorDisplay() {
      @Override
      public Subscription schedule(String id, Runnable tick) {
        return Subscription.NONE;
      }

      @Override
      public void render(UUID viewerId, Runnable render) {
      }

      @Override
      public FocusClaim claimFocus(UUID viewerId) {
        return null;
      }

      @Override
      public void actionBar(UUID viewerId, String legacyHeader) {
      }

      @Override
      public void titleTimes(UUID viewerId, Duration fadeIn, Duration stay, Duration fadeOut) {
      }

      @Override
      public void title(UUID viewerId, Component title) {
      }

      @Override
      public void subtitle(UUID viewerId, Component subtitle) {
      }

      @Override
      public void clearTitle(UUID viewerId) {
      }

      @Override
      public void clear(UUID viewerId, boolean blankTitle) {
      }
    });
  }

  private static Sampler sampler(String id, Runnable onSample) {
    Sampler sampler = Mockito.mock(Sampler.class);
    Mockito.when(sampler.getId()).thenReturn(id);
    Mockito.when(sampler.sample()).thenAnswer(invocation -> {
      if (onSample != null) {
        onSample.run();
      }
      return 1D;
    });
    return sampler;
  }
}
