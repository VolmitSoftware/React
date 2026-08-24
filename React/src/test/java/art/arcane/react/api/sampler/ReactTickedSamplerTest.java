package art.arcane.react.api.sampler;

import art.arcane.react.React;
import art.arcane.react.util.common.scheduling.Ticker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

class ReactTickedSamplerTest {
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
  void activeSamplerPublishesTickedValues() {
    TestSampler sampler = new TestSampler(false);

    sampler.sample();
    sampler.onTick();

    Assertions.assertEquals(7D, sampler.sample(), 1.0E-9D);
  }

  @Test
  void repeatedFailuresAreReportedWithoutLogFlooding() {
    TestSampler sampler = new TestSampler(true);
    sampler.sample();

    try (MockedStatic<React> react = Mockito.mockStatic(React.class)) {
      sampler.onTick();
      sampler.onTick();

      react.verify(() -> React.reportError(
          Mockito.eq("Sampler ticked-test failed: IllegalStateException - boom"),
          Mockito.any(Throwable.class)), Mockito.times(1));
    }
  }

  private static final class TestSampler extends ReactTickedSampler {
    private final boolean fail;

    private TestSampler(boolean fail) {
      super("ticked-test", 50L, 4);
      this.fail = fail;
    }

    @Override
    public double onSample() {
      if (fail) {
        throw new IllegalStateException("boom");
      }
      return 7D;
    }

    @Override
    public String formattedValue(double t) {
      return Double.toString(t);
    }

    @Override
    public String formattedSuffix(double t) {
      return "u";
    }
  }
}
