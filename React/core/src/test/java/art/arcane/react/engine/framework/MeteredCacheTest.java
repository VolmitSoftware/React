package art.arcane.react.engine.framework;

import art.arcane.volmlib.util.data.KCache;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.LongRange;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

public class MeteredCacheTest {

  @ParameterizedTest(name = "size={0} maxSize={1} -> usage={2}")
  @CsvSource({
      "0, 100, 0.0",
      "100, 100, 1.0",
      "25, 100, 0.25",
      "150, 100, 1.5"
  })
  public void usageIsTheUnclampedRatioOfSizeToMaxSize(long size, long maxSize, double expected) {
    MeteredCache cache = new FakeMeteredCache(size, maxSize, false);
    Assertions.assertEquals(expected, cache.getUsage(), 0.0D);
  }

  @ParameterizedTest(name = "size={0} maxSize=0 -> nan={1}")
  @CsvSource({
      "0, true",
      "5, false"
  })
  public void usageIsUndefinedWhenMaxSizeIsZero(long size, boolean expectNaN) {
    MeteredCache cache = new FakeMeteredCache(size, 0L, false);
    double usage = cache.getUsage();

    Assertions.assertEquals(expectNaN, Double.isNaN(usage));
    Assertions.assertEquals(!expectNaN, Double.isInfinite(usage));
  }

  @Test
  public void isClosedReflectsBackingState() {
    Assertions.assertTrue(new FakeMeteredCache(0L, 100L, true).isClosed());
    Assertions.assertFalse(new FakeMeteredCache(0L, 100L, false).isClosed());
  }

  @Property
  public void usageEqualsSizeOverMaxSizeWithinUnitInterval(
      @ForAll @LongRange(min = 1L, max = 1_000_000L) long maxSize,
      @ForAll @LongRange(min = 0L, max = 1_000_000_000L) long rawSize) {
    long size = Math.floorMod(rawSize, maxSize + 1L);
    MeteredCache cache = new FakeMeteredCache(size, maxSize, false);
    double expected = (double) size / (double) maxSize;
    double usage = cache.getUsage();
    Assertions.assertEquals(expected, usage, 0.0D);
    Assertions.assertTrue(usage >= 0.0D && usage <= 1.0D);
  }

  private static final class FakeMeteredCache implements MeteredCache {
    private final long size;
    private final long maxSize;
    private final boolean closed;

    private FakeMeteredCache(long size, long maxSize, boolean closed) {
      this.size = size;
      this.maxSize = maxSize;
      this.closed = closed;
    }

    @Override
    public long getSize() {
      return size;
    }

    @Override
    public KCache<?, ?> getRawCache() {
      return null;
    }

    @Override
    public long getMaxSize() {
      return maxSize;
    }

    @Override
    public boolean isClosed() {
      return closed;
    }
  }
}
