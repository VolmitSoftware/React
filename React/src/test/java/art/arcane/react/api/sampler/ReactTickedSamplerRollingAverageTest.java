package art.arcane.react.api.sampler;

import art.arcane.react.React;
import art.arcane.react.util.common.scheduling.Ticker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.ArrayDeque;
import java.util.Deque;

/** Rolling-average behavior the sampler takes from its RollingSequence. */
class ReactTickedSamplerRollingAverageTest {
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
  void captureIsZeroBeforeTheFirstTick() {
    ScriptedSampler sampler = new ScriptedSampler(4);

    Assertions.assertEquals(0D, sampler.capture());
    Assertions.assertEquals(0, sampler.calls);
  }

  @Test
  void sleepingSamplerDoesNotSampleOnTick() {
    ScriptedSampler sampler = new ScriptedSampler(4, 5D);

    sampler.onTick();

    Assertions.assertEquals(0, sampler.calls);
    Assertions.assertEquals(0D, sampler.capture());
  }

  @Test
  void firstTickFillsTheWholeWindow() {
    ScriptedSampler sampler = new ScriptedSampler(4, 8D);
    sampler.markDemand();

    sampler.onTick();

    Assertions.assertEquals(8D, sampler.capture());
  }

  @Test
  void laterTicksReplaceOneSlotAtATime() {
    ScriptedSampler sampler = new ScriptedSampler(4, 1D, 2D, 3D, 4D, 5D, 6D);
    double[] expected = {1D, 1.25D, 1.75D, 2.5D, 3.5D, 4.5D};
    sampler.markDemand();

    for (int index = 0; index < expected.length; index++) {
      sampler.onTick();
      Assertions.assertEquals(expected[index], sampler.capture(), "after tick " + (index + 1));
    }
  }

  @Test
  void singleSlotWindowReportsTheLatestSample() {
    ScriptedSampler sampler = new ScriptedSampler(1, 3D, -7D, 11D);
    sampler.markDemand();

    sampler.onTick();
    sampler.onTick();
    sampler.onTick();

    Assertions.assertEquals(11D, sampler.capture());
  }

  @Test
  void notANumberSampleStaysInTheAverageWhileOtherSamplesFollow() {
    ScriptedSampler sampler = new ScriptedSampler(3, 1D, Double.NaN, 5D, 5D, 5D, 5D);
    sampler.markDemand();

    for (int index = 0; index < 6; index++) {
      sampler.onTick();
    }

    Assertions.assertTrue(Double.isNaN(sampler.capture()), "capture was " + sampler.capture());
  }

  @Test
  void notANumberSampleLeavesTheAverageAfterAFullyZeroWindow() {
    ScriptedSampler sampler = new ScriptedSampler(3, 1D, Double.NaN, 0D, 0D, 0D, 0D, 9D);
    sampler.markDemand();

    for (int index = 0; index < 4; index++) {
      sampler.onTick();
    }
    Assertions.assertTrue(Double.isNaN(sampler.capture()), "capture was " + sampler.capture());

    sampler.onTick();
    Assertions.assertEquals(0D, sampler.capture());

    sampler.onTick();
    sampler.onTick();
    Assertions.assertEquals(3D, sampler.capture());
  }

  @Test
  void metadataWindowIsIntervalTimesMemory() {
    ScriptedSampler sampler = new ScriptedSampler(4);

    Assertions.assertEquals(50L, sampler.metadata().intervalMs());
    Assertions.assertEquals(200L, sampler.metadata().windowMs());
  }

  @Test
  void lapsedDemandSleepsWithoutLosingTheAverage() throws InterruptedException {
    ScriptedSampler sampler = new ScriptedSampler(4, 6D, 2D, 100D);
    sampler.markDemand();
    sampler.onTick();
    sampler.onTick();
    Assertions.assertEquals(50L, sampler.getTinterval());

    Thread.sleep(1_050L);
    sampler.onTick();
    sampler.onTick();

    Assertions.assertEquals(1_000L, sampler.getTinterval());
    Assertions.assertEquals(2, sampler.calls);
    Assertions.assertEquals(5D, sampler.capture());

    Assertions.assertEquals(5D, sampler.sample());
    Assertions.assertEquals(50L, sampler.getTinterval());
    sampler.onTick();
    Assertions.assertEquals(28.5D, sampler.capture());
  }

  private static final class ScriptedSampler extends ReactTickedSampler {
    private final Deque<Double> script = new ArrayDeque<>();
    private int calls;

    private ScriptedSampler(int memory, double... values) {
      super("ticked-rolling", 50L, memory);
      for (double value : values) {
        script.addLast(value);
      }
    }

    @Override
    public double onSample() {
      calls++;
      return script.removeFirst();
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
