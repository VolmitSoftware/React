package art.arcane.react.util.common.scheduling;

import art.arcane.react.React;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class TickedObjectTest {
  private React previous;
  private Ticker ticker;

  @BeforeEach
  void setUp() {
    previous = React.instance;
    React plugin = Mockito.mock(React.class);
    ticker = Mockito.mock(Ticker.class);
    Mockito.when(plugin.getTicker()).thenReturn(ticker);
    React.instance = plugin;
  }

  @AfterEach
  void tearDown() {
    React.instance = previous;
  }

  @Test
  void tickCountTracksExecutedTickDispatches() {
    CountingTickedObject ticked = new CountingTickedObject();

    ticked.tick();
    ticked.tick();

    Assertions.assertEquals(2L, ticked.getTickCount());
    Assertions.assertEquals(2, ticked.invocations);
  }

  @Test
  void skippedTicksDoNotAdvanceTheTickCount() {
    CountingTickedObject ticked = new CountingTickedObject();
    ticked.skip(1);

    ticked.tick();
    ticked.tick();

    Assertions.assertEquals(1L, ticked.getTickCount());
    Assertions.assertEquals(1, ticked.invocations);
  }

  @Test
  void terminalTicksDoNotAdvanceTheTickCount() {
    CountingTickedObject ticked = new CountingTickedObject();
    ticked.dieAfter(1);

    ticked.tick();

    Assertions.assertEquals(0L, ticked.getTickCount());
    Assertions.assertEquals(0, ticked.invocations);
    Mockito.verify(ticker).unregister(ticked);
  }

  private static final class CountingTickedObject extends TickedObject {
    private int invocations;

    private CountingTickedObject() {
      super("test", "counting", 1L);
    }

    @Override
    public void onTick() {
      invocations++;
    }
  }
}
