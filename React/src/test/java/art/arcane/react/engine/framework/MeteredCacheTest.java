package art.arcane.react.engine.framework;

import art.arcane.volmlib.util.data.KCache;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.LongRange;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class MeteredCacheTest {

  @Test
  public void usageIsZeroWhenCacheEmpty() {
    MeteredCache cache = new FakeMeteredCache(0L, 100L, false);
    Assertions.assertEquals(0.0D, cache.getUsage(), 0.0D);
  }

  @Test
  public void usageIsOneWhenCacheFull() {
    MeteredCache cache = new FakeMeteredCache(100L, 100L, false);
    Assertions.assertEquals(1.0D, cache.getUsage(), 0.0D);
  }

  @Test
  public void usageIsRatioOfSizeToMaxSize() {
    MeteredCache cache = new FakeMeteredCache(25L, 100L, false);
    Assertions.assertEquals(0.25D, cache.getUsage(), 0.0D);
  }

  @Test
  public void usageIsNotClampedWhenSizeExceedsMaxSize() {
    MeteredCache cache = new FakeMeteredCache(150L, 100L, false);
    Assertions.assertEquals(1.5D, cache.getUsage(), 0.0D);
  }

  @Test
  public void usageIsNanWhenMaxSizeIsZeroAndEmpty() {
    MeteredCache cache = new FakeMeteredCache(0L, 0L, false);
    Assertions.assertTrue(Double.isNaN(cache.getUsage()));
  }

  @Test
  public void usageIsInfiniteWhenMaxSizeIsZeroAndNonEmpty() {
    MeteredCache cache = new FakeMeteredCache(5L, 0L, false);
    Assertions.assertTrue(Double.isInfinite(cache.getUsage()));
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
