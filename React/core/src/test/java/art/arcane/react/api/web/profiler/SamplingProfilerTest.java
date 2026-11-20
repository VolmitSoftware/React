package art.arcane.react.api.web.profiler;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

class SamplingProfilerTest {
  private static StackTraceElement nativeFrame(String className, String method) {
    return new StackTraceElement(null, null, null, className, method, null, -2);
  }

  @Test
  void nativeWaitsAreIdle() {
    Assertions.assertTrue(SamplingProfiler.idle(nativeFrame("sun.nio.ch.KQueue", "poll")));
    Assertions.assertTrue(SamplingProfiler.idle(nativeFrame("io.netty.channel.epoll.Native", "epollWait")));
    Assertions.assertTrue(SamplingProfiler.idle(nativeFrame("java.io.FileInputStream", "readBytes")));
    Assertions.assertFalse(SamplingProfiler.idle(nativeFrame("java.lang.System", "arraycopy")));
    Assertions.assertFalse(SamplingProfiler.idle(new StackTraceElement("sun.nio.ch.Net", "poll", null, 10)));
  }

  @Test
  void stateTracksStartRetuneStopAndReset() throws InterruptedException {
    AtomicLong clock = new AtomicLong(1_000L);
    SamplingProfiler profiler = new SamplingProfiler(Optional::empty, clock::get);
    Assertions.assertFalse(profiler.state().running());
    Assertions.assertNull(profiler.state().startedAt());
    ProfileDtos.State started = profiler.start(5000, "server", 99_999);
    Assertions.assertTrue(started.running());
    Assertions.assertEquals(SamplingProfiler.MAX_INTERVAL_MS, started.intervalMs());
    Assertions.assertEquals(1_000L + SamplingProfiler.MAX_SECONDS * 1000L, started.stopsAt());
    clock.set(3_000L);
    ProfileDtos.State retuned = profiler.start(10, started.threads(), 10);
    Assertions.assertEquals(10, retuned.intervalMs());
    Assertions.assertEquals(11_000L, retuned.stopsAt());
    Assertions.assertEquals(2_000L, retuned.durationMs());
    ProfileDtos.State stopped = profiler.stop();
    Assertions.assertFalse(stopped.running());
    Assertions.assertNull(stopped.stopsAt());
    Assertions.assertEquals(2_000L, stopped.durationMs());
    ProfileDtos.State reset = profiler.reset();
    Assertions.assertNull(reset.startedAt());
    Assertions.assertEquals(0L, reset.samples());
  }
}
